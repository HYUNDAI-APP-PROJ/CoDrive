// Run with: node docs/design/verify-fun-a11y.cjs
// 엔터 요소 화면 3개(fun-stop-games · fun-challenges · trip-card)의 공통 품질 검사. 헤드리스 Chrome 또는 Edge 필요.
// 1. 키보드만으로 모든 조작에 도달하고 실행할 수 있으며, 포커스 표시가 보인다
// 2. 동작 줄이기 설정에서 전환·반복 애니메이션이 없고, 상태를 글자로 알 수 있다
// 3. 화면 문구가 ~해요 체이고, 내부 상태명·기술 용어가 노출되지 않는다
// 4. 긴 그룹 이름·차량 이름에서도 분할 창 배치가 깨지지 않는다
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const harness = require('./lib/browser-harness.cjs');

const url = name => pathToFileURL(path.join(__dirname, name)).href;
const PAGES = { games: url('fun-stop-games.html'), challenges: url('fun-challenges.html'), card: url('trip-card.html') };
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

// 지금 보이고 사용할 수 있는 키보드 조작 대상에 번호를 붙이고 목록을 돌려준다.
const MARK_FOCUSABLE = `(() => {
  const nodes = [...document.querySelectorAll('button, a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
    .filter(node => !node.disabled && node.checkVisibility({ visibilityProperty: true }) && node.getClientRects().length);
  nodes.forEach((node, index) => { node.dataset.a11y = String(index); });
  return nodes.map(node => node.textContent.trim().slice(0, 20) || node.getAttribute('aria-label'));
})()`;
const FOCUS_STATE = `(() => {
  const node = document.activeElement;
  const style = getComputedStyle(node);
  return { id: node.dataset?.a11y ?? null, label: node.textContent.trim().slice(0, 20),
    visible: node.matches(':focus-visible') && style.outlineStyle !== 'none' && parseFloat(style.outlineWidth) >= 2 };
})()`;

// Tab 으로 한 바퀴 돌며 모든 대상에 도달하는지, 도달할 때마다 포커스 표시가 보이는지 확인한다.
async function tabCycle(page, label) {
  const expected = await page.eval(MARK_FOCUSABLE);
  assert.ok(expected.length > 0, label + ': 조작 대상 없음');
  await page.eval('document.activeElement.blur(); window.focus()');
  const reached = new Set();
  for (let i = 0; i < expected.length + 4; i++) {
    await page.press('Tab');
    const focus = await page.eval(FOCUS_STATE);
    if (focus.id === null) continue;
    assert.ok(focus.visible, `${label}: 포커스 표시가 안 보임 — ${focus.label}`);
    reached.add(focus.id);
  }
  const missed = expected.filter((_, index) => !reached.has(String(index)));
  assert.deepEqual(missed, [], label + ': Tab 으로 닿지 않는 조작');
  return expected.length;
}
async function focusOn(page, selector, label) {
  await page.eval('document.activeElement.blur(); window.focus()');
  for (let i = 0; i < 60; i++) {
    await page.press('Tab');
    if (await page.eval(`document.activeElement.matches(${JSON.stringify(selector)})`)) return;
  }
  throw new Error(label + ': Tab 으로 ' + selector + ' 에 닿지 않음');
}

// 동작 줄이기에서 보이는 요소에 전환·애니메이션이 남아 있는지 찾는다.
const MOTION_PROBE = `(() => [...document.querySelectorAll('body *')].filter(node => node.checkVisibility && node.checkVisibility()).flatMap(node => {
  const style = getComputedStyle(node);
  const seconds = value => value.split(',').map(part => parseFloat(part) || 0);
  const problems = [];
  if (style.animationName !== 'none' && seconds(style.animationDuration).some(v => v > 0)) problems.push('animation ' + style.animationName);
  if (seconds(style.transitionDuration).some(v => v > 0)) problems.push('transition ' + style.transitionProperty);
  return problems.map(problem => (node.id || node.className?.baseVal || node.className || node.tagName) + ': ' + problem);
}))()`;

// 화면에 보이는 글자와 접근성 이름을 모아 내부 상태명·기술 용어·말투를 확인한다.
const TEXT_PROBE = `(() => {
  const texts = [document.body.innerText,
    ...[...document.querySelectorAll('[aria-label], [title], [alt]')].map(node => [node.getAttribute('aria-label'), node.getAttribute('title'), node.getAttribute('alt')].filter(Boolean).join(' ')),
    ...[...document.querySelectorAll('[role="status"]')].map(node => node.textContent)];
  return texts.join('\\n');
})()`;
const FORBIDDEN = /\b(STALE|MOVING|STOPPED|UNKNOWN|ACHIEVED|IN_PROGRESS|MISSED|AVAILABLE|NO_DATA|LOADING|ERROR|ROULETTE|LADDER|VOTE|REST_AREA|ATTRACTION|WAYPOINT|DESTINATION|GUIDING|ARRIVED|NO_ROUTE|MOCK|PARKING|TOLL|undefined|null|NaN|true|false|Infinity)\b|\[object|mock-|vehicle-[a-c]\b|Measurement|sessionStorage|codrive\./;
function checkWording(text, label) {
  const forbidden = text.match(FORBIDDEN);
  assert.equal(forbidden, null, `${label}: 내부 상태명·기술 용어 노출 "${forbidden?.[0]}"`);
  for (const line of text.split('\n').map(item => item.trim()).filter(Boolean)) {
    for (const sentence of line.split(/(?<=\.)\s+/)) {
      if (/(니다|한다|된다|이다|했다|없다|있다)\.?$/.test(sentence)) assert.fail(`${label}: ~해요 체가 아님 "${sentence}"`);
      if (sentence.endsWith('.') && !/요\.$/.test(sentence)) assert.fail(`${label}: 문장이 ~요로 끝나지 않음 "${sentence}"`);
    }
  }
}

(async () => {
  const browser = await harness.launch();
  const report = [];
  try {
    // ---- 1. 키보드 도달 · 포커스 표시 (시연 패널 포함) ----
    for (const [name, options] of [
      ['games', { scenario: 'rest-stop', search: 'dev=1' }],
      ['challenges', { scenario: 'all-arrived', search: 'dev=1' }],
      ['card', { scenario: 'driving', search: 'dev=1' }]
    ]) {
      const page = await harness.open(browser, PAGES[name], options);
      if (name === 'challenges') await page.waitFor(`!document.getElementById('notice').hidden`);
      await focusOn(page, '.dev-toggle', name);
      await page.press('Enter');
      assert.equal(await page.eval(`document.getElementById('dev-body').hidden`), false, name + ': Enter 로 시연 패널이 열려야 함');
      const count = await tabCycle(page, name);
      report.push(`${name}: Tab 으로 조작 ${count}개 모두 도달, 포커스 표시 확인`);
      await page.close();
    }

    // ---- 1-1. 키보드로 실제 조작 ----
    {
      const page = await harness.open(browser, PAGES.games, { scenario: 'rest-stop' });
      const decisions = () => page.eval('window.CoDriveTripLog.getTripRecord().then(r => r.decisions.length)');
      const before = await decisions();
      await focusOn(page, '[data-game="LADDER"]', 'games');
      await page.press('Enter');
      await focusOn(page, '[data-purpose="PARKING"]', 'games');
      await page.press('Space');
      assert.deepEqual(await page.eval(`[document.querySelector('[data-game="LADDER"]').getAttribute('aria-pressed'), document.querySelector('[data-purpose="PARKING"]').getAttribute('aria-pressed')]`), ['true', 'true']);
      await focusOn(page, '#start', 'games');
      await page.press('Enter');
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`);
      assert.match(await page.eval(`document.getElementById('outcome-title').textContent`), /^주차비는 [ABC] 차량이 내요$/);
      await page.waitFor(`document.activeElement === document.getElementById('start')`, 2000);
      await page.press('Space'); // "다시 하기"
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`);
      assert.equal(await decisions(), before + 2);
      await page.close();

      const moving = await harness.open(browser, PAGES.games, { scenario: 'long-stop', reducedMotion: false });
      await focusOn(moving, '#start', 'games');
      await moving.press('Enter');
      await moving.waitFor(`document.getElementById('main').dataset.gameState === 'running'`);
      await moving.eval(`window.CoDriveMock.setScenario('driving')`);
      await moving.waitFor(`document.getElementById('main').dataset.gameState === 'stopped'`);
      assert.equal(await moving.eval(`document.activeElement.id`), 'outcome', '멈추면 포커스가 멈춘 이유로 옮겨진다');
      await moving.close();

      const challenges = await harness.open(browser, PAGES.challenges, { scenario: 'all-arrived' });
      await challenges.waitFor(`!document.getElementById('notice').hidden`);
      await focusOn(challenges, '#notice-close', 'challenges');
      await challenges.press('Enter');
      assert.equal(await challenges.eval(`document.getElementById('notice').hidden`), true);
      assert.equal(await challenges.eval(`document.activeElement.id`), 'heading', '알림을 닫으면 포커스가 제목으로 옮겨진다');
      await challenges.close();

      const card = await harness.open(browser, PAGES.card, { scenario: 'driving', search: 'dev=1' });
      await focusOn(card, '.dev-toggle', 'card');
      await card.press('Enter');
      await focusOn(card, '[data-dev-action="end-trip"]', 'card');
      await card.press('Enter');
      await card.waitFor(`document.getElementById('main').dataset.tripState === 'complete'`);
      const collapsed = () => card.eval(`({ hidden: document.getElementById('dev-body').hidden, expanded: document.querySelector('.dev-toggle').getAttribute('aria-expanded'),
        focus: document.activeElement.className, status: document.getElementById('dev-status').checkVisibility() ? document.getElementById('dev-status').textContent : null })`);
      assert.deepEqual(await collapsed(), { hidden: true, expanded: 'false', focus: 'dev-toggle', status: '여행을 종료했어요.' },
        '여행 종료 후 패널이 접히고 포커스는 펼치기 버튼, 결과 문구는 계속 보인다');
      await card.press('Enter');
      assert.deepEqual(await collapsed(), { hidden: false, expanded: 'true', focus: 'dev-toggle', status: null }, '펼치기 버튼으로 다시 펼친다');
      // 시나리오 버튼도 누르면 접힌다.
      await focusOn(card, '[data-dev-scenario="rest-stop"]', 'card');
      await card.press('Space');
      assert.deepEqual(await collapsed(), { hidden: true, expanded: 'false', focus: 'dev-toggle', status: null }, '시나리오를 고르면 접힌다');
      // 새로고침하는 버튼(초기화): 다시 불러온 화면에서도 접혀 있고 포커스는 펼치기 버튼에 있다.
      await card.press('Enter');
      await focusOn(card, '[data-dev-action="reset"]', 'card');
      const reloaded = card.nextLoad();
      await card.press('Enter');
      await reloaded;
      await card.waitFor('document.documentElement.dataset.ready === "true"');
      assert.deepEqual(await collapsed(), { hidden: true, expanded: 'false', focus: 'dev-toggle', status: null }, '초기화 후 접힌 채로 포커스가 펼치기 버튼에 있다');
      assert.equal(await card.eval(`document.getElementById('main').dataset.tripState`), 'in-progress', '초기화로 여행 종료가 지워진다');
      await card.close();
      report.push('키보드 조작: 방법·비용 선택(Enter·Space), 시작·다시 하기, 중단 시 포커스 이동, 알림 확인, 시연 패널 여행 종료');
      report.push('시연 패널: 시나리오·여행 종료·초기화(여행 횟수는 도전과제 검사)를 누르면 접히고 포커스는 펼치기 버튼으로, 결과 문구는 접힌 채 보임');
    }

    // ---- 2. 동작 줄이기 ----
    const motionStates = [
      ['games idle', PAGES.games, { scenario: 'long-stop' }, null],
      ['games locked', PAGES.games, { scenario: 'red-light' }, null],
      ['games roulette done', PAGES.games, { scenario: 'long-stop' }, `document.getElementById('start').click()`],
      ['games ladder done', PAGES.games, { scenario: 'long-stop' }, `document.querySelector('[data-game="LADDER"]').click(); document.getElementById('start').click()`],
      ['challenges notice', PAGES.challenges, { scenario: 'all-arrived' }, null],
      ['card complete', PAGES.card, { scenario: 'rest-stop' }, `window.CoDriveTripLog.endTrip()`],
      ['dev panel open', PAGES.card, { scenario: 'rest-stop', search: 'dev=1' }, `document.querySelector('.dev-toggle').click()`]
    ];
    for (const [label, pageUrl, options, action] of motionStates) {
      const page = await harness.open(browser, pageUrl, { ...options, reducedMotion: true });
      if (action) await page.eval(action);
      await sleep(300);
      assert.deepEqual(await page.eval(MOTION_PROBE), [], label + ': 동작 줄이기에서 움직임이 남음');
      // 상태를 글자로 알 수 있는지
      if (label.startsWith('games')) {
        const state = await page.eval(`({ game: document.getElementById('main').dataset.gameState, safety: document.getElementById('safety-text').textContent,
          outcome: document.getElementById('outcome').hidden ? '' : document.getElementById('outcome-title').textContent })`);
        assert.ok(state.safety.length > 0, label);
        if (label.endsWith('done')) assert.match(state.outcome, /내요$/, label + ': 애니메이션 없이 결과가 바로 보여야 함');
        if (label === 'games locked') assert.match(state.safety, /초 남았어요/);
      }
      await page.close();
    }
    report.push(`동작 줄이기: ${motionStates.length}개 상태에서 전환·애니메이션 없음, 잠금 이유와 결과를 글자로 표시`);

    // ---- 3. 문구 ----
    const textStates = [
      ['games driving', PAGES.games, { scenario: 'driving' }, null],
      ['games red-light', PAGES.games, { scenario: 'red-light' }, null],
      ['games unknown', PAGES.games, { scenario: 'unknown' }, null],
      ['games done', PAGES.games, { scenario: 'destination' }, `document.querySelector('[data-purpose="PARKING"]').click(); document.getElementById('start').click()`],
      ['games ladder done', PAGES.games, { scenario: 'rest-stop' }, `document.querySelector('[data-game="LADDER"]').click(); document.getElementById('start').click()`],
      ['games no group', PAGES.games, { scenario: 'long-stop', search: 'group=none' }, null],
      ['challenges driving', PAGES.challenges, { scenario: 'driving' }, null],
      ['challenges notice', PAGES.challenges, { scenario: 'all-arrived' }, null],
      ['challenges resting', PAGES.challenges, { scenario: 'rest-stop' }, null],
      ['challenges over 2h', PAGES.challenges, { scenario: 'driving' }, `window.CoDriveMock.setDrivingSecondsForDemo(7300)`],
      ['challenges unknown', PAGES.challenges, { scenario: 'unknown' }, null],
      ['challenges no group', PAGES.challenges, { scenario: 'all-arrived', search: 'group=none' }, null],
      ['challenges trip 1', PAGES.challenges, { scenario: 'driving', search: 'trip=1' }, null],
      ['challenges ended', PAGES.challenges, { scenario: 'all-arrived' }, `window.CoDriveTripLog.endTrip()`],
      ['card in progress', PAGES.card, { scenario: 'driving' }, null],
      ['card complete', PAGES.card, { scenario: 'driving' }, `window.CoDriveTripLog.recordDecision({ kind: 'VOTE', title: '저녁', result: '식당', votes: { agree: 1, total: 3 } }); window.CoDriveTripLog.endTrip()`],
      ['dev panel', PAGES.card, { scenario: 'unknown', search: 'dev=1' }, `document.querySelector('.dev-toggle').click(); document.querySelector('[data-dev-action="near-two-hours"]').click()`]
    ];
    for (const [label, pageUrl, options, action] of textStates) {
      const page = await harness.open(browser, pageUrl, options);
      if (action) await page.eval(action);
      await sleep(1200);
      checkWording(await page.eval(TEXT_PROBE), label);
      await page.close();
    }
    // 빈 기록 카드
    {
      const page = await harness.open(browser, PAGES.card, { scenario: 'driving' });
      const next = page.nextLoad();
      await page.eval(`sessionStorage.setItem('codrive.history.trip-log.v1', JSON.stringify({ tripId: 'e', groupName: '', startedAt: Date.now() - 1000, endedAt: null, tripNumberWithGroup: 1, visits: [], decisions: [], achievements: [] })); location.reload()`);
      await next;
      await page.waitFor('document.documentElement.dataset.ready === "true"');
      checkWording(await page.eval(TEXT_PROBE), 'card empty');
      await page.eval('window.CoDriveTripLog.endTrip()');
      await sleep(200);
      checkWording(await page.eval(TEXT_PROBE), 'card empty complete');
      await page.close();
    }
    report.push(`문구: ${textStates.length + 2}개 상태에서 내부 상태명·기술 용어 없음, 문장은 모두 ~요로 끝남`);

    // ---- 4. 긴 그룹 이름·차량 이름과 분할 창 (design-guide 검수 체크리스트) ----
    const LONG_GROUP = '우리 가족과 친구들이 함께 떠나는 아주 긴 이름의 강원도 동해안 겨울 주말 여행';
    const LONG_VEHICLES = JSON.stringify(['아버지 차 쏘렌토 하이브리드', 'B 차량', '이모네 캠핑카']);
    const longCases = [
      ['games roulette', PAGES.games, { scenario: 'long-stop' }, `document.getElementById('context-title').textContent = ${JSON.stringify(LONG_GROUP)};
        document.querySelectorAll('.wheel-label').forEach((node, i) => { node.textContent = ${LONG_VEHICLES}[i]; })`],
      ['games ladder', PAGES.games, { scenario: 'long-stop' }, `document.querySelector('[data-game="LADDER"]').click();
        document.querySelectorAll('.ladder-top li').forEach((node, i) => { node.textContent = ${LONG_VEHICLES}[i]; })`],
      ['challenges', PAGES.challenges, { scenario: 'all-arrived' }, `document.getElementById('context-title').textContent = ${JSON.stringify(LONG_GROUP)}`],
      ['card', PAGES.card, { scenario: 'driving' }, `document.getElementById('trip-name').textContent = ${JSON.stringify(LONG_GROUP)}`]
    ];
    for (const width of [1682, 829]) {
      for (const [label, pageUrl, options, inject] of longCases) {
        const page = await harness.open(browser, pageUrl, { ...options, width, height: 1210 });
        await page.eval(inject);
        await sleep(300);
        assert.deepEqual(await page.eval(harness.layoutProbe({ boxes: '.wheel-frame, .ladder-svg' })), [], `${width} ${label}: 긴 이름 배치`);
        // 제목은 낱말 중간에서 끊기지 않는다
        const wordBreak = await page.eval(`getComputedStyle(document.getElementById('context-title') || document.getElementById('trip-name')).wordBreak`);
        assert.equal(wordBreak, 'keep-all', label);
        await page.close();
      }
    }
    report.push('긴 그룹 이름·차량 이름: 두 창 크기에서 겹침·넘침 없음, 제목은 낱말 단위로 줄바꿈');

    console.log('PASS:\n- ' + report.join('\n- '));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
