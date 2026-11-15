// Run with: node docs/design/verify-trip-card.cjs
// trip-card.html 을 실제 브라우저(헤드리스 Chrome 또는 Edge)에서 열어 확인한다.
// - 진행 중 → 완성 전환(시연 패널 "여행 종료 처리"), 빈 기록 처리
// - 룰렛 결과와 도전과제가 trip-log 를 거쳐 카드로 이어지는지 (같은 탭에서 화면 이동)
// - 1682×1210 · 829×1210 배치
// 스크린샷 저장: SCREENSHOT_DIR=<폴더> node docs/design/verify-trip-card.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const harness = require('./lib/browser-harness.cjs');

const fileUrl = name => pathToFileURL(path.join(__dirname, name)).href;
const CARD = fileUrl('trip-card.html');
const SIZES = [[1682, 1210], [829, 1210]];
const LAYOUT_PROBE = harness.layoutProbe({ boxes: '.qr', noWrap: '.stat dt, .stat dd, .entry-time, .save-title, .qr span' });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const open = (browser, options) => harness.open(browser, CARD, options);

const card = page => page.eval(`(() => {
  const texts = selector => [...document.querySelectorAll(selector)].map(node => node.textContent.trim());
  const shown = id => !document.getElementById(id).hidden;
  return {
    state: document.getElementById('main').dataset.tripState,
    name: document.getElementById('trip-name').textContent,
    meta: document.getElementById('trip-meta').textContent,
    progress: shown('trip-progress') ? document.getElementById('trip-progress').textContent.trim() : null,
    save: shown('save') ? document.getElementById('save').textContent.replace(/\\s+/g, ' ').trim() : null,
    stats: texts('.stat dd'),
    visits: texts('#visits .entry-title'),
    visitsEmpty: texts('#visits .empty')[0] || null,
    decisions: [...document.querySelectorAll('#decisions .entry')].map(row => [row.querySelector('.entry-title').textContent, row.querySelector('.entry-detail')?.textContent]),
    decisionsEmpty: texts('#decisions .empty')[0] || null,
    decisionSummary: shown('decisions-summary') ? document.getElementById('decisions-summary').textContent : null,
    achievements: texts('#achievements .entry-title'),
    achievementChecks: document.querySelectorAll('#achievements .entry .check use[href="#i-check"]').length,
    achievementsEmpty: texts('#achievements .empty')[0] || null
  };
})()`);

const EMPTY_LOG = `JSON.stringify({ tripId: 'empty', groupName: '', startedAt: Date.now() - 90 * 1000, endedAt: null,
  tripNumberWithGroup: 1, visits: [], decisions: [], achievements: [] })`;
async function loadEmpty(page) {
  const next = page.nextLoad();
  await page.eval(`sessionStorage.setItem('codrive.history.trip-log.v1', ${EMPTY_LOG}); location.reload()`);
  await next;
  await page.waitFor('document.documentElement.dataset.ready === "true"');
}

(async () => {
  const browser = await harness.launch();
  const shots = process.env.SCREENSHOT_DIR;
  if (shots) fs.mkdirSync(shots, { recursive: true });
  try {
    // 1. 진행 중 카드 → 시연 패널 "여행 종료 처리" → 완성 카드
    {
      const page = await open(browser, { scenario: 'driving', search: 'dev=1' });
      let c = await card(page);
      assert.equal(c.state, 'in-progress');
      assert.equal(c.name, '강릉 주말 여행');
      assert.equal(c.progress, '여행이 끝나면 기록이 완성돼요');
      assert.equal(c.save, null, '진행 중에는 저장 영역이 없다');
      assert.match(c.meta, / 출발$/);
      assert.deepEqual(c.stats, ['3시간', '2곳', '1건', '0개']);
      assert.deepEqual(c.visits, ['대청호 산책로', '호숫가 식당'], '방문 장소는 시간순');
      assert.deepEqual(c.decisions, [['점심 장소: 호숫가 식당', '투표 · 찬성 3/3 · 가결']]);
      assert.equal(c.decisionSummary, '투표 1건 (가결 1 · 부결 0) · 룰렛 0건 · 사다리타기 0건');
      assert.equal(c.achievementsEmpty, '아직 달성한 도전과제가 없어요');
      assert.deepEqual(await page.eval(`[...document.querySelectorAll('.nav-item.active, .nav-item[aria-current="page"]')].map(n => n.textContent)`), ['여행 기록']);
      assert.equal(await page.eval(`document.querySelectorAll('.nav-item').length`), 6);

      await page.eval(`document.querySelector('.dev-toggle').click(); document.querySelector('[data-dev-action="end-trip"]').click()`);
      await page.waitFor(`document.getElementById('main').dataset.tripState === 'complete'`, 3000);
      c = await card(page);
      assert.equal(c.progress, null);
      assert.match(c.meta, / 출발 · .+ 도착$/);
      assert.match(c.save, /시연용/, 'QR 자리는 시연용으로 구분한다');
      assert.match(c.save, /실제로 저장되지 않아요/);
      assert.equal(c.achievementsEmpty, '달성한 도전과제가 없어요');
      const together = c.stats[0];
      await sleep(1200);
      assert.equal((await card(page)).stats[0], together, '종료 후 함께한 시간은 고정');
      await page.close();
    }

    // 2. 룰렛 결과 → 도전과제 달성 → 투표 → 카드 (같은 탭, 같은 trip-log)
    {
      const page = await harness.open(browser, fileUrl('fun-stop-games.html'), { scenario: 'long-stop' });
      await page.eval(`document.getElementById('start').click()`);
      await page.waitFor(`document.getElementById('main').dataset.gameState === 'done'`);
      const outcome = await page.eval(`document.getElementById('outcome-title').textContent`);
      const winner = outcome.match(/^톨비는 (.+ 차량)이 내요$/)[1];

      await page.goto(fileUrl('fun-challenges.html') + '?scenario=all-arrived');
      await page.waitFor(`document.querySelectorAll('.challenge[data-status="ACHIEVED"]').length === 4`);
      await page.eval(`window.CoDriveTripLog.recordDecision({ kind: 'VOTE', title: '저녁 장소', result: '바닷가 식당', votes: { agree: 1, total: 3 } })`);

      await page.goto(CARD + '?scenario=all-arrived');
      const c = await card(page);
      assert.deepEqual(c.decisions, [
        ['점심 장소: 호숫가 식당', '투표 · 찬성 3/3 · 가결'],
        ['톨비 정하기', '룰렛 · 결과 ' + winner],
        ['저녁 장소: 바닷가 식당', '투표 · 찬성 1/3 · 부결']
      ]);
      assert.equal(c.decisionSummary, '투표 2건 (가결 1 · 부결 1) · 룰렛 1건 · 사다리타기 0건');
      assert.deepEqual(c.achievements, ['쉬어가는 여행', '부드러운 운전', '완벽한 합류', '또 만났네요']);
      assert.equal(c.achievementChecks, 4, '달성 표시는 강조색 원과 선형 체크');
      assert.deepEqual(c.stats.slice(1), ['2곳', '3건', '4개']);
      // 투표가 부결되면 만장일치는 놓친 것으로 이어진다(같은 trip-log)
      await page.goto(fileUrl('fun-challenges.html') + '?scenario=all-arrived');
      assert.equal(await page.eval(`document.querySelector('[data-challenge="unanimous"]').dataset.status`), 'MISSED');
      await page.close();
    }

    // 3. 빈 기록: 방문·결정·도전과제 0건, 여행 이름 없음
    {
      const page = await open(browser, { scenario: 'driving' });
      await loadEmpty(page);
      let c = await card(page);
      assert.equal(c.state, 'in-progress');
      assert.equal(c.name, '이번 여행');
      assert.deepEqual(c.stats, ['1분', '0곳', '0건', '0개']);
      assert.deepEqual([c.visitsEmpty, c.decisionsEmpty, c.achievementsEmpty], ['방문한 장소가 아직 없어요', '아직 함께 내린 결정이 없어요', '아직 달성한 도전과제가 없어요']);
      assert.equal(c.decisionSummary, null);
      assert.deepEqual([c.visits, c.decisions, c.achievements], [[], [], []]);
      await page.eval(`window.CoDriveTripLog.endTrip()`);
      await page.waitFor(`document.getElementById('main').dataset.tripState === 'complete'`, 3000);
      c = await card(page);
      assert.deepEqual([c.visitsEmpty, c.decisionsEmpty, c.achievementsEmpty], ['방문한 장소가 없어요', '함께 내린 결정이 없어요', '달성한 도전과제가 없어요']);
      assert.match(c.save, /시연용/);
      assert.deepEqual(await page.eval(harness.layoutProbe({ boxes: '.qr' })), []);
      assert.equal(await page.eval(`document.querySelectorAll('[data-codrive-dev]').length`), 0);
      await page.close();
    }

    // 4. 두 창 크기에서 겹침·넘침·줄바꿈 없음
    const prepare = {
      'in-progress': async () => {},
      complete: async page => { await page.eval(`window.CoDriveTripLog.endTrip()`); },
      'empty-complete': async page => { await loadEmpty(page); await page.eval(`window.CoDriveTripLog.endTrip()`); },
      'many-entries': async page => {
        await page.eval(`(() => {
          const log = window.CoDriveTripLog;
          log.recordDecision({ kind: 'ROULETTE', title: '주차비 정하기', result: 'C 차량' });
          log.recordDecision({ kind: 'LADDER', title: '톨비 정하기', result: 'A 차량' });
          log.recordDecision({ kind: 'VOTE', title: '아주 긴 이름의 휴식 장소를 정하는 투표 제안입니다', result: '경치가 좋은 바닷가 옆 전망대 주차장', votes: { agree: 2, total: 3 } });
          for (const id of ['rest-before-2h', 'smooth-driving', 'perfect-merge', 'unanimous', 'reunion']) log.recordAchievement({ challengeId: id, title: id });
          log.endTrip();
        })()`);
      }
    };
    for (const [width, height] of SIZES) {
      for (const [name, run] of Object.entries(prepare)) {
        const page = await open(browser, { scenario: 'rest-stop', width, height });
        await run(page);
        await page.waitFor(`document.getElementById('main').dataset.tripState === '${name === 'in-progress' ? 'in-progress' : 'complete'}'`, 3000);
        await sleep(400);
        const problems = await page.eval(LAYOUT_PROBE);
        if (shots) await page.screenshot(path.join(shots, `trip-card-${width}x${height}-${name}.png`));
        assert.deepEqual(problems, [], `${width}×${height} ${name}`);
        assert.equal(await page.eval(`document.querySelectorAll('[data-codrive-dev]').length`), 0);
        await page.close();
      }
    }
    console.log('PASS: in-progress card turns complete via dev panel end-trip (QR area marked as demo), empty record renders, '
      + 'roulette result · votes (passed/rejected) · achievements flow from trip-log into the card, layout without overlap or wrapping at 1682×1210 and 829×1210');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
