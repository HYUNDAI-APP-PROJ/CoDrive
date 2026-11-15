// Run with: node docs/design/verify-fun-stop-games.cjs
// fun-stop-games.html 을 실제 브라우저(헤드리스 Chrome 또는 Edge)에서 열어 잠금·중단·기록과 두 창 크기의 배치를 확인한다.
// 스크린샷 저장: SCREENSHOT_DIR=<폴더> node docs/design/verify-fun-stop-games.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const harness = require('./lib/browser-harness.cjs');

const PAGE = pathToFileURL(path.join(__dirname, 'fun-stop-games.html')).href;
const SIZES = [[1682, 1210], [829, 1210]];
const LOCKED = ['driving', 'red-light', 'unknown', 'leaving-rest-stop'];
const UNLOCKED = ['long-stop', 'rest-stop', 'all-arrived', 'destination'];
const RESULT_TEXT = /^(톨비는|주차비는) [ABC] 차량이 내요$/;
const { launch } = harness;
const open = (browser, options) => harness.open(browser, PAGE, options);
const LAYOUT_PROBE = harness.layoutProbe({ boxes: '.wheel-frame, .ladder-svg', noWrap: '.ladder-slot, .ladder-top li, .wheel-label' });

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

    // 7. 시연 패널: dev=1 이 없으면 흔적이 없고, dev=1 이면 시나리오를 바로 바꾼다.
    {
      const page = await open(browser, { scenario: 'long-stop' });
      assert.deepEqual(await page.eval(`({ nodes: document.querySelectorAll('[data-codrive-dev]').length,
        keys: Object.keys(sessionStorage).filter(key => key.includes('dev-panel')).length })`), { nodes: 0, keys: 0 });
      await page.close();
      const dev = await open(browser, { scenario: 'long-stop', search: 'dev=1' });
      assert.equal(await dev.eval(`document.querySelectorAll('aside[data-codrive-dev] [data-dev-scenario]').length`), 8);
      assert.equal(await dev.eval(`document.getElementById('dev-body').hidden`), true, '처음에는 접혀 있다');
      await click(dev, '.dev-toggle');
      assert.equal(await dev.eval(`document.getElementById('dev-body').hidden`), false);
      await click(dev, '[data-dev-scenario="driving"]');
      await dev.waitFor(`document.getElementById('main').dataset.locked === 'true'`);
      assert.equal(await dev.eval(`window.CoDriveMock.scenario()`), 'driving');
      assert.match(await dev.eval('location.search'), /scenario=driving/);
      assert.deepEqual(await dev.eval(`[document.getElementById('dev-body').hidden, document.activeElement.className]`), [true, 'dev-toggle'],
        '시나리오를 고르면 패널이 접히고 포커스가 펼치기 버튼으로 간다');
      await click(dev, '.dev-toggle');
      await click(dev, '[data-dev-action="near-two-hours"]');
      assert.equal(await dev.eval(`document.getElementById('dev-body').hidden`), false, '1시간 59분 버튼은 패널을 접지 않는다');
      const driving = await dev.eval(`window.CoDriveVehicle.getContinuousDrivingSeconds().then(m => m.value)`);
      assert.ok(driving >= 7140 && driving < 7150, String(driving));
      await dev.close();
    }

    // 8. 두 창 크기에서 글자·버튼이 겹치지 않는다.
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
        assert.equal(await page.eval(`document.querySelectorAll('[data-codrive-dev]').length`), 0);
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
