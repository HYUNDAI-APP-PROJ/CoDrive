// Run with: node docs/design/verify-fun-stop-games.cjs
// fun-stop-games.html 을 실제 브라우저(헤드리스 Chrome 또는 Edge)에서 열어 잠금·중단·기록과 두 창 크기의 배치를 확인한다.
// 추가 패키지 없이 Node 내장 WebSocket 으로 DevTools 프로토콜을 사용한다. 브라우저 경로는 CHROME_PATH 로 바꿀 수 있다.
// 스크린샷 저장: SCREENSHOT_DIR=<폴더> node docs/design/verify-fun-stop-games.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const PAGE = pathToFileURL(path.join(__dirname, 'fun-stop-games.html')).href;
const SIZES = [[1682, 1210], [829, 1210]];
const LOCKED = ['driving', 'red-light', 'unknown', 'leaving-rest-stop'];
const UNLOCKED = ['long-stop', 'rest-stop', 'all-arrived', 'destination'];
const RESULT_TEXT = /^(톨비는|주차비는) [ABC] 차량이 내요$/;

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
async function open(browser, { scenario, search = '', width = 1682, height = 1210, reducedMotion = true }) {
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
  await send('Page.navigate', { url: PAGE + '?' + query });
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

const state = page => page.eval(`({
  game: document.getElementById('main').dataset.gameState,
  locked: document.getElementById('main').dataset.locked,
  startDisabled: document.getElementById('start').disabled,
  safety: document.getElementById('safety-text').textContent,
  outcome: document.getElementById('outcome').hidden ? null : document.getElementById('outcome-title').textContent,
  note: document.getElementById('outcome').hidden ? null : document.getElementById('outcome-note').textContent
})`);
const decisions = page => page.eval('window.CoDriveTripLog.getTripRecord().then(record => record.decisions.length)');
const click = (page, selector) => page.eval(`document.querySelector(${JSON.stringify(selector)}).click()`);

// 보이는 글자·버튼끼리 겹치거나, 부모 영역 밖으로 넘치거나, 가로 스크롤이 생기는지 확인한다.
const LAYOUT_PROBE = `(() => {
  const problems = [];
  const root = document.documentElement;
  if (root.scrollWidth > root.clientWidth) problems.push('page horizontal scroll ' + root.scrollWidth + '>' + root.clientWidth);
  const visible = node => node.checkVisibility({ opacityProperty: true, visibilityProperty: true }) && node.getClientRects().length;
  const hasText = node => [...node.childNodes].some(child => child.nodeType === 3 && child.textContent.trim());
  const items = [...document.querySelectorAll('.shell *')].filter(node => !(node instanceof SVGElement) && visible(node)
    && !node.classList.contains('sr-only') && (hasText(node) || node.matches('button, .wheel-frame, .ladder-svg')));
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
  for (const node of document.querySelectorAll('.shell button, .ladder-slot, .ladder-top li, .wheel-label, .group-count, .nav-item span, .route-point-label')) {
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

(async () => {
  const browser = await launch();
  const shots = process.env.SCREENSHOT_DIR;
  if (shots) fs.mkdirSync(shots, { recursive: true });
  try {
    // 1. 주행·정차 직후·상태 불명·도착 표시가 남은 출발 중에는 시작할 수 없다.
    for (const scenario of LOCKED) {
      const page = await open(browser, { scenario });
      const before = await decisions(page);
      let s = await state(page);
      assert.equal(s.locked, 'true', scenario);
      assert.equal(s.startDisabled, true, scenario);
      await page.eval(`(() => { const b = document.getElementById('start'); b.disabled = false; b.click(); })()`); // 버튼을 강제로 켜도 시작하지 않는다.
      await new Promise(resolve => setTimeout(resolve, 200));
      s = await state(page);
      assert.notEqual(s.game, 'running', scenario + ': 잠금 중 시작됨');
      assert.equal(s.outcome, null, scenario);
      assert.equal(await decisions(page), before, scenario);
      if (scenario === 'red-light') assert.match(s.safety, /2분이 지나면 사용할 수 있어요\. 1[12]\d초 남았어요\./);
      if (scenario === 'driving' || scenario === 'leaving-rest-stop') assert.match(s.safety, /^주행 중에는 사용할 수 없어요/);
      if (scenario === 'unknown') assert.equal(s.safety, '차량 상태를 확인할 수 없어 잠겨 있어요.');
      await page.close();
    }

    // 2. 2분 넘은 정차·경유지 도착·전원 합류·목적지 도착에서는 시작할 수 있고, 결과 확정 시 결정 1건이 기록된다.
    for (const [index, scenario] of UNLOCKED.entries()) {
      const page = await open(browser, { scenario });
      assert.equal((await state(page)).locked, 'false', scenario);
      if (index % 2) await click(page, '[data-game="LADDER"]');
      if (index >= 2) await click(page, '[data-purpose="PARKING"]');
      const before = await decisions(page);
      await click(page, '#start');
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`);
      const s = await state(page);
      assert.match(s.outcome, RESULT_TEXT, scenario);
      assert.ok(s.outcome.startsWith(index >= 2 ? '주차비는' : '톨비는'), scenario);
      assert.equal(s.note, '여행 기록에 남겼어요.');
      assert.equal(await decisions(page), before + 1, scenario);
      const last = await page.eval('window.CoDriveTripLog.getTripRecord().then(r => r.decisions.at(-1))');
      assert.equal(last.kind, index % 2 ? 'LADDER' : 'ROULETTE');
      assert.equal(last.title, (index >= 2 ? '주차비' : '톨비') + ' 정하기');
      assert.equal(s.outcome, (index >= 2 ? '주차비는 ' : '톨비는 ') + last.result + '이 내요');
      await page.close();
    }

    // 3. 진행 중 잠금 상태가 되면 즉시 멈추고 결과를 기록하지 않는다. (애니메이션을 켠 상태)
    for (const [game, scenario, to, text] of [
      ['ROULETTE', 'long-stop', 'driving', '차량이 움직여서 멈췄어요.'],
      ['LADDER', 'rest-stop', 'leaving-rest-stop', '차량이 움직여서 멈췄어요.'],
      ['ROULETTE', 'all-arrived', 'unknown', '차량 상태를 확인할 수 없어 멈췄어요.']
    ]) {
      const page = await open(browser, { scenario, reducedMotion: false });
      if (game === 'LADDER') await click(page, '[data-game="LADDER"]');
      const before = await decisions(page);
      await click(page, '#start');
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'running'`);
      await new Promise(resolve => setTimeout(resolve, 400));
      await page.eval(`window.CoDriveMock.setScenario(${JSON.stringify(to)})`);
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'stopped'`, 1000);
      let s = await state(page);
      assert.equal(s.outcome, text);
      assert.equal(s.note, '결과는 정하지 않았어요.');
      assert.equal(s.startDisabled, true);
      const frozen = await page.eval(`getComputedStyle(document.querySelector(${JSON.stringify(game === 'ROULETTE' ? '#wheel-rotor' : '.ladder-path')})).${game === 'ROULETTE' ? 'transform' : 'strokeDashoffset'}`);
      await new Promise(resolve => setTimeout(resolve, 4500)); // 원래 끝났을 시간이 지나도
      s = await state(page);
      assert.equal(s.game, 'stopped', game + ': 멈춘 뒤 결과가 확정됨');
      assert.equal(await decisions(page), before, game + ': 멈춘 게임이 기록됨');
      assert.equal(await page.eval(`document.querySelectorAll('.is-winner').length`), 0);
      const after = await page.eval(`getComputedStyle(document.querySelector(${JSON.stringify(game === 'ROULETTE' ? '#wheel-rotor' : '.ladder-path')})).${game === 'ROULETTE' ? 'transform' : 'strokeDashoffset'}`);
      assert.equal(after, frozen, game + ': 멈춘 위치에서 계속 움직임');
      await page.close();
    }

    // 4. 애니메이션을 켠 상태에서도 끝까지 진행하면 기록된다.
    {
      const page = await open(browser, { scenario: 'long-stop', reducedMotion: false });
      const before = await decisions(page);
      await click(page, '#start');
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`, 6000);
      assert.equal(await decisions(page), before + 1);
      await page.close();
    }

    // 5. 그룹이 없으면 안내만 보인다.
    {
      const page = await open(browser, { scenario: 'long-stop', search: 'group=none' });
      assert.deepEqual(await page.eval(`({
        games: document.getElementById('games').hidden, control: document.getElementById('game-control').hidden,
        message: document.getElementById('no-group').hidden ? null : document.getElementById('no-group-title').textContent,
        context: document.getElementById('context-label').getAttribute('aria-hidden')
      })`), { games: true, control: true, message: '함께하는 그룹이 없어요', context: 'true' });
      await page.close();
    }

    // 6. 사이드 메뉴는 선택 항목이 없고, 현재 경로 패널은 남은 경로만 표시한다.
    {
      const page = await open(browser, { scenario: 'rest-stop' });
      assert.equal(await page.eval(`document.querySelectorAll('.nav-item').length`), 6);
      assert.equal(await page.eval(`document.querySelectorAll('.nav-item.active, [aria-current="page"]').length`), 0);
      assert.deepEqual(await page.eval(`[...document.querySelectorAll('.route-point')].map(p => p.textContent)`), ['현재 위치횡성휴게소', '목적지안목해변']);
      await page.close();
    }

    // 7. 두 창 크기에서 글자·버튼이 겹치지 않는다.
    const layouts = [
      ['idle-roulette', { scenario: 'long-stop' }, null],
      ['idle-ladder', { scenario: 'long-stop' }, '[data-game="LADDER"]'],
      ['locked', { scenario: 'red-light' }, null],
      ['done-roulette', { scenario: 'rest-stop' }, '#start'],
      ['done-ladder', { scenario: 'rest-stop' }, 'LADDER+start'],
      ['stopped', { scenario: 'long-stop' }, 'stop'],
      ['no-group', { scenario: 'long-stop', search: 'group=none' }, null],
      ['no-route', { scenario: 'destination' }, null]
    ];
    for (const [width, height] of SIZES) {
      for (const [name, options, action] of layouts) {
        const page = await open(browser, { ...options, width, height, reducedMotion: action !== 'stop' });
        if (action === '[data-game="LADDER"]') await click(page, action);
        if (action === '#start') { await click(page, '#start'); await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`); }
        if (action === 'LADDER+start') {
          await click(page, '[data-game="LADDER"]');
          await click(page, '#start');
          await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`);
        }
        if (action === 'stop') {
          await click(page, '#start');
          await new Promise(resolve => setTimeout(resolve, 300));
          await page.eval(`window.CoDriveMock.setScenario('driving')`);
          await page.waitFor(`document.getElementById('main').dataset.gameState === 'stopped'`);
        }
        await new Promise(resolve => setTimeout(resolve, 500)); // 진입 애니메이션이 끝난 뒤 측정
        const problems = await page.eval(LAYOUT_PROBE);
        if (shots) await page.screenshot(path.join(shots, `${width}x${height}-${name}.png`));
        assert.deepEqual(problems, [], `${width}×${height} ${name}`);
        await page.close();
      }
    }
    console.log('PASS: locked in driving/red-light/unknown/leaving-rest-stop, playable in long-stop/rest-stop/all-arrived/destination, '
      + 'interrupted games stop in place without a record, finished games add one decision, no-group notice, '
      + 'layout without overlap at 1682×1210 and 829×1210');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
