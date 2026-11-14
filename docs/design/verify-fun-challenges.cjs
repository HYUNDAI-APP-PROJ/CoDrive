// Run with: node docs/design/verify-fun-challenges.cjs
// fun-challenges.html 을 실제 브라우저(헤드리스 Chrome 또는 Edge)에서 열어 확인한다.
// - 주행 중에는 새 달성 알림이 뜨지 않고, isSafeToPlay() 가 true 가 되면 뜬다
// - 차량별 비교·순위 요소가 없다 / 주행 중에는 조작 요소가 없다
// - 시나리오별 판정, 기록 유지, 1682×1210 · 829×1210 배치
// 스크린샷 저장: SCREENSHOT_DIR=<폴더> node docs/design/verify-fun-challenges.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const harness = require('./lib/browser-harness.cjs');

const PAGE = pathToFileURL(path.join(__dirname, 'fun-challenges.html')).href;
const SIZES = [[1682, 1210], [829, 1210]];
const open = (browser, options) => harness.open(browser, PAGE, options);
const LAYOUT_PROBE = harness.layoutProbe({ noWrap: '.challenge-state, .challenge-title, .notice-title' });
const RANKING = /[A-Z] 차량|순위|등수|\d+\s*등|\d+\s*위\b|랭킹|가장 빠|먼저 도착|빨리 도착/;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

const statuses = page => page.eval(`Object.fromEntries([...document.querySelectorAll('.challenge')].map(row => [row.dataset.challenge, row.dataset.status]))`);
const row = (page, id) => page.eval(`(() => { const row = document.querySelector('[data-challenge="${id}"]');
  return { status: row.dataset.status, state: row.querySelector('.challenge-state').textContent, detail: row.querySelector('.challenge-detail').textContent }; })()`);
const notice = page => page.eval(`document.getElementById('notice').hidden ? null : document.getElementById('notice-list').textContent`);
const achievements = page => page.eval(`window.CoDriveTripLog.getTripRecord().then(r => r.achievements.map(a => a.challengeId))`);
const pendingIds = page => page.eval(`JSON.parse(sessionStorage.getItem('codrive.fun.challenges.v1') || '{"pending":[]}').pending`);
const setScenario = (page, name) => page.eval(`window.CoDriveMock.setScenario(${JSON.stringify(name)})`);
const ready = page => page.waitFor(`document.querySelectorAll('.challenge[data-status]').length === 5`);
const noRanking = async (page, label) => {
  const text = await page.eval(`document.getElementById('challenges').textContent`);
  assert.doesNotMatch(text, RANKING, label + ': 차량 이름·순위 표현이 있음');
  assert.equal(await page.eval(`document.querySelectorAll('#challenges ol, #challenges [data-vehicle-id], #challenges [data-rank]').length`), 0, label);
};

(async () => {
  const browser = await harness.launch();
  const shots = process.env.SCREENSHOT_DIR;
  if (shots) fs.mkdirSync(shots, { recursive: true });
  try {
    // 1. 주행 중 달성 → 기록은 바로, 알림은 미룸 → 짧은 정차에도 미룸 → 안전해지면 표시 → 다시 주행하면 숨김 → 확인하면 사라짐
    {
      const page = await open(browser, { scenario: 'driving' });
      await ready(page);
      assert.deepEqual(await statuses(page), {
        'rest-before-2h': 'IN_PROGRESS', 'smooth-driving': 'ACHIEVED', 'perfect-merge': 'IN_PROGRESS', unanimous: 'IN_PROGRESS', reunion: 'ACHIEVED'
      });
      assert.deepEqual((await achievements(page)).sort(), ['reunion', 'smooth-driving'], '달성은 주행 중에도 바로 기록된다');
      await sleep(1500); // 몇 번 갱신되어도
      assert.equal(await notice(page), null, '주행 중에는 알림이 뜨지 않는다');
      assert.deepEqual((await pendingIds(page)).sort(), ['reunion', 'smooth-driving']);
      assert.equal(await page.eval(`[...document.querySelectorAll('#main button, #main input, #main a')].filter(n => n.checkVisibility()).length`), 0, '주행 중에는 조작 요소가 없다');
      assert.equal(await page.eval(`document.querySelector('[data-challenge="rest-before-2h"] .challenge-detail').textContent`), '연속 운전 1시간 55분 · 2시간까지 5분 남았어요');

      await setScenario(page, 'red-light');
      await sleep(1200);
      assert.equal(await notice(page), null, '정차 직후(120초 전)에도 알림이 뜨지 않는다');

      await setScenario(page, 'long-stop');
      await page.waitFor(`!document.getElementById('notice').hidden`, 3000);
      assert.equal(await notice(page), '부드러운 운전 · 또 만났네요');
      await noRanking(page, 'notice');

      await setScenario(page, 'driving');
      await page.waitFor(`document.getElementById('notice').hidden`, 3000);
      assert.deepEqual((await pendingIds(page)).sort(), ['reunion', 'smooth-driving'], '다시 주행하면 숨기고 대기열은 유지한다');

      await setScenario(page, 'rest-stop');
      await page.waitFor(`!document.getElementById('notice').hidden`, 3000);
      await page.eval(`document.getElementById('notice-close').click()`);
      assert.equal(await notice(page), null);
      assert.deepEqual(await pendingIds(page), []);
      await sleep(1200);
      assert.equal(await notice(page), null, '확인한 알림은 다시 뜨지 않는다');
      await page.close();
    }

    // 2. 휴게소 전원 합류: 휴식·합류 달성. 이후 다시 주행해도 기록된 달성은 유지된다.
    {
      const page = await open(browser, { scenario: 'all-arrived' });
      await ready(page);
      assert.deepEqual(await statuses(page), {
        'rest-before-2h': 'ACHIEVED', 'smooth-driving': 'ACHIEVED', 'perfect-merge': 'ACHIEVED', unanimous: 'IN_PROGRESS', reunion: 'ACHIEVED'
      });
      assert.equal((await row(page, 'perfect-merge')).detail, '횡성휴게소에 모두 모였어요.');
      assert.equal((await row(page, 'smooth-driving')).detail, '공인 연비 12.8km/L 대비 109%');
      await page.waitFor(`!document.getElementById('notice').hidden`, 3000);
      assert.equal(await notice(page), '쉬어가는 여행 · 부드러운 운전 · 완벽한 합류 · 또 만났네요');
      await noRanking(page, 'all-arrived');
      await setScenario(page, 'driving');
      await page.waitFor(`document.querySelector('[data-challenge="smooth-driving"]').dataset.status === 'ACHIEVED'`, 3000);
      const kept = await statuses(page);
      assert.equal(kept['rest-before-2h'], 'ACHIEVED');
      assert.equal(kept['perfect-merge'], 'ACHIEVED');
      // 여행 종료 → 만장일치 확정
      await page.eval(`window.CoDriveTripLog.endTrip()`);
      await page.waitFor(`document.querySelector('[data-challenge="unanimous"]').dataset.status === 'ACHIEVED'`, 3000);
      assert.ok((await achievements(page)).includes('unanimous'));
      assert.equal(await page.eval(`document.querySelectorAll('.challenge.is-achieved .mark.is-achieved use[href="#i-check"]').length`), 5);
      await page.close();
    }

    // 3. 연속 운전 2시간 초과 → 놓침, 휴식 중 → 진행률
    {
      const page = await open(browser, { scenario: 'driving' });
      await ready(page);
      await page.eval(`window.CoDriveMock.setDrivingSecondsForDemo(7300)`);
      await page.waitFor(`document.querySelector('[data-challenge="rest-before-2h"]').dataset.status === 'MISSED'`, 3000);
      assert.deepEqual(await row(page, 'rest-before-2h'), { status: 'MISSED', state: '이번엔 놓쳤어요', detail: '연속 운전이 2시간을 넘었어요. 가까운 곳에서 쉬어가요.' });
      await setScenario(page, 'rest-stop');
      await page.waitFor(`document.querySelector('[data-challenge="rest-before-2h"]').dataset.status === 'IN_PROGRESS'`, 3000);
      assert.match((await row(page, 'rest-before-2h')).detail, /^휴식을 시작했어요 · 15분 더 쉬면 달성해요$/);
      assert.equal(await page.eval(`document.querySelector('[data-challenge="rest-before-2h"] .progress').hidden`), false);
      await page.close();
    }

    // 3-1. 공인 연비 목표 미달은 진행 중(놓침 아님)이고, 내 차량 값만 보여준다.
    {
      const page = await open(browser, { scenario: 'red-light' });
      await ready(page);
      assert.deepEqual(await row(page, 'smooth-driving'), { status: 'IN_PROGRESS', state: '진행 중', detail: '공인 연비 12.8km/L 대비 102% · 목표 105%' });
      await page.close();
    }

    // 4. 값을 쓸 수 없으면 "확인할 수 없어요" — 달성도 놓침도 아니다.
    {
      const page = await open(browser, { scenario: 'unknown' });
      await ready(page);
      for (const id of ['rest-before-2h', 'smooth-driving']) {
        const item = await row(page, id);
        assert.equal(item.status, 'UNKNOWN', id);
        assert.equal(item.state, '확인할 수 없어요', id);
      }
      assert.deepEqual(await achievements(page), ['reunion']); // 차량 정보와 무관한 과제만 판정됨
      await page.close();

      const groupless = await open(browser, { scenario: 'all-arrived', search: 'group=none' });
      await ready(groupless);
      for (const id of ['perfect-merge', 'reunion']) {
        assert.deepEqual(await row(groupless, id), { status: 'UNKNOWN', state: '확인할 수 없어요', detail: '그룹이 있어야 확인할 수 있어요.' }, id);
      }
      assert.ok(!(await achievements(groupless)).includes('perfect-merge'));
      assert.equal(await groupless.eval(`document.getElementById('context-label').getAttribute('aria-hidden')`), 'true');
      await groupless.close();
    }

    // 5. 사이드 메뉴: 항목 6개 그대로, "여행 기록"만 선택. 시연 패널 흔적 없음.
    {
      const page = await open(browser, { scenario: 'driving' });
      await ready(page);
      assert.equal(await page.eval(`document.querySelectorAll('.nav-item').length`), 6);
      assert.deepEqual(await page.eval(`[...document.querySelectorAll('.nav-item.active, .nav-item[aria-current="page"]')].map(n => n.textContent)`), ['여행 기록']);
      assert.equal(await page.eval(`document.querySelectorAll('[data-codrive-dev]').length`), 0);
      await page.close();
      const dev = await open(browser, { scenario: 'driving', search: 'dev=1' });
      await ready(dev);
      assert.equal(await dev.eval(`document.querySelectorAll('aside[data-codrive-dev] [data-dev-scenario]').length`), 8);
      await dev.eval(`document.querySelector('.dev-toggle').click(); document.querySelector('[data-dev-action="near-two-hours"]').click()`);
      await dev.waitFor(`document.querySelector('[data-challenge="rest-before-2h"] .challenge-detail').textContent.includes('1시간 59분')`, 3000);
      await dev.close();
    }

    // 6. 두 창 크기에서 겹침·넘침·줄바꿈 없음
    const layouts = [
      ['driving', { scenario: 'driving' }],
      ['notice', { scenario: 'all-arrived' }],
      ['missed-resting', { scenario: 'rest-stop' }],
      ['unknown', { scenario: 'unknown' }],
      ['no-group', { scenario: 'all-arrived', search: 'group=none' }]
    ];
    for (const [width, height] of SIZES) {
      for (const [name, options] of layouts) {
        const page = await open(browser, { ...options, width, height });
        await ready(page);
        if (name === 'notice') await page.waitFor(`!document.getElementById('notice').hidden`, 3000);
        await sleep(500);
        await noRanking(page, `${width}×${height} ${name}`);
        const problems = await page.eval(LAYOUT_PROBE);
        if (shots) await page.screenshot(path.join(shots, `challenges-${width}x${height}-${name}.png`));
        assert.deepEqual(problems, [], `${width}×${height} ${name}`);
        await page.close();
      }
    }
    console.log('PASS: achievements recorded while driving but notice held until isSafeToPlay(), hidden again on driving, cleared on confirm; '
      + 'no vehicle ranking or per-vehicle comparison; read-only while driving; unknown data stays unknown; recorded achievements kept; '
      + 'menu highlights 여행 기록; layout without overlap or wrapping at 1682×1210 and 829×1210');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
