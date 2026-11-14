// 화면 검사 공용 도구: 헤드리스 Chrome/Edge 를 DevTools 프로토콜로 조작하고, 배치 문제를 찾는다.
// 추가 패키지 없이 Node 내장 WebSocket 을 사용한다. 브라우저 경로는 CHROME_PATH 로 바꿀 수 있다.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');

function findBrowser() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
  ].filter(Boolean);
  const found = candidates.find(candidate => fs.existsSync(candidate));
  if (!found) throw new Error('Chrome 또는 Edge 를 찾지 못했습니다. CHROME_PATH 로 경로를 지정하세요.');
  return found;
}

async function launch() {
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'codrive-verify-'));
  const child = spawn(findBrowser(), ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
  const wsUrl = await new Promise((resolve, reject) => {
    let log = '';
    const timer = setTimeout(() => reject(new Error('브라우저가 시작되지 않았습니다.\n' + log)), 20000);
    child.stderr.on('data', chunk => {
      log += chunk;
      const match = log.match(/DevTools listening on (ws:\/\/\S+)/);
      if (match) { clearTimeout(timer); resolve(match[1]); }
    });
    child.on('exit', code => reject(new Error('브라우저 종료: ' + code + '\n' + log)));
  });
  const socket = new WebSocket(wsUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let nextId = 0;
  const pending = new Map();
  const waiters = [];
  socket.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const { resolve, reject } = pending.get(message.id);
      pending.delete(message.id);
      message.error ? reject(new Error(message.error.message)) : resolve(message.result);
    } else if (message.method) {
      for (const waiter of [...waiters]) if (waiter.method === message.method && waiter.sessionId === message.sessionId) {
        waiters.splice(waiters.indexOf(waiter), 1);
        waiter.resolve(message.params);
      }
    }
  };
  const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
    const id = ++nextId;
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params, sessionId }));
  });
  const once = (method, sessionId) => new Promise(resolve => waiters.push({ method, sessionId, resolve }));
  return {
    send, once,
    async close() {
      try { await send('Browser.close'); } catch { /* 이미 종료됨 */ }
      socket.close();
      await new Promise(resolve => { if (child.exitCode !== null) resolve(); else child.on('exit', resolve); });
      try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* 임시 폴더 정리 실패는 무시 */ }
    }
  };
}

// 새 탭(새 sessionStorage)에서 페이지를 연다.
async function open(browser, pageUrl, { scenario, search = '', width = 1682, height = 1210, reducedMotion = true }) {
  const { targetId } = await browser.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await browser.send('Target.attachToTarget', { targetId, flatten: true });
  const send = (method, params) => browser.send(method, params, sessionId);
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: reducedMotion ? 'reduce' : 'no-preference' }] });
  const loaded = browser.once('Page.loadEventFired', sessionId);
  const query = new URLSearchParams(search);
  if (scenario) query.set('scenario', scenario);
  await send('Page.navigate', { url: pageUrl + '?' + query });
  await loaded;
  const page = {
    async eval(expression) {
      const { result, exceptionDetails } = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      if (exceptionDetails) throw new Error(exceptionDetails.exception?.description || exceptionDetails.text);
      return result.value;
    },
    async waitFor(expression, timeout = 8000) {
      const end = Date.now() + timeout;
      while (Date.now() < end) {
        if (await page.eval(expression)) return;
        await new Promise(resolve => setTimeout(resolve, 50));
      }
      throw new Error('시간 안에 조건을 만족하지 않았습니다: ' + expression);
    },
    async screenshot(file) {
      const { data } = await send('Page.captureScreenshot', { format: 'png' });
      fs.writeFileSync(file, Buffer.from(data, 'base64'));
    },
    close: () => browser.send('Target.closeTarget', { targetId })
  };
  await page.waitFor('document.documentElement.dataset.ready === "true"');
  return page;
}

// boxes: 글자가 없어도 겹침을 검사할 요소, noWrap: 한 줄이어야 하는 요소(공통: 버튼·그룹 대수·메뉴·경로 이름표).
// 보이는 글자·버튼끼리 겹치거나, 부모 영역 밖으로 넘치거나, 가로 스크롤이 생기는지 확인한다.
function layoutProbe({ boxes = '', noWrap = '' } = {}) {
  return `(() => {
  const problems = [];
  const root = document.documentElement;
  if (root.scrollWidth > root.clientWidth) problems.push('page horizontal scroll ' + root.scrollWidth + '>' + root.clientWidth);
  const visible = node => node.checkVisibility({ opacityProperty: true, visibilityProperty: true }) && node.getClientRects().length;
  const hasText = node => [...node.childNodes].some(child => child.nodeType === 3 && child.textContent.trim());
  const items = [...document.querySelectorAll('.shell *')].filter(node => !(node instanceof SVGElement) && visible(node)
    && !node.classList.contains('sr-only') && (hasText(node) || node.matches(${JSON.stringify(['button', boxes].filter(Boolean).join(', '))})));
  const name = node => node.tagName.toLowerCase() + (node.id ? '#' + node.id : '') + (node.className && typeof node.className === 'string' ? '.' + node.className.split(' ')[0] : '') + ' "' + node.textContent.trim().slice(0, 20) + '"';
  for (const node of items) {
    const box = node.getBoundingClientRect();
    if (box.right > root.clientWidth + 0.5 || box.left < -0.5) problems.push('outside viewport: ' + name(node));
    if (node.matches('button, p, span, li') && node.scrollWidth > node.clientWidth + 1 && getComputedStyle(node).overflowX !== 'visible') problems.push('clipped: ' + name(node));
    const parent = node.closest('.main, .route-sidebar, .sidebar');
    if (parent) {
      const area = parent.getBoundingClientRect();
      if (box.right > area.right + 0.5 || box.left < area.left - 0.5) problems.push('outside ' + parent.className + ': ' + name(node));
    }
  }
  // 짧은 이름표·버튼 글자가 두 줄로 나뉘지 않는지 확인한다.
  for (const node of document.querySelectorAll(${JSON.stringify(['.shell button', '.group-count', '.nav-item span', '.route-point-label', noWrap].filter(Boolean).join(', '))})) {
    if (!visible(node)) continue;
    const range = document.createRange();
    range.selectNodeContents(node);
    const lines = new Set([...range.getClientRects()].filter(rect => rect.width > 0).map(rect => Math.round(rect.top)));
    if (lines.size > 1) problems.push('wrapped: ' + name(node));
  }
  for (let i = 0; i < items.length; i++) for (let j = i + 1; j < items.length; j++) {
    const a = items[i], b = items[j];
    if (a.contains(b) || b.contains(a)) continue;
    const r = a.getBoundingClientRect(), s = b.getBoundingClientRect();
    const overlapX = Math.min(r.right, s.right) - Math.max(r.left, s.left);
    const overlapY = Math.min(r.bottom, s.bottom) - Math.max(r.top, s.top);
    if (overlapX > 1 && overlapY > 1) problems.push('overlap: ' + name(a) + ' × ' + name(b));
  }
  return problems;
})()`;
}

module.exports = { launch, open, layoutProbe };
