// Run with: node docs/design/verify-fun-challenge-rules.cjs
// challenge-rules.js 의 순수 판정 함수를 과제별 달성 · 미달성 · 데이터 없음 경우로 확인한다.
// 함께: dev-panel.js 가 ?dev=1 없이 아무것도 건드리지 않는지 확인한다.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const read = name => fs.readFileSync(path.join(__dirname, 'js', name), 'utf8');
const plain = value => JSON.parse(JSON.stringify(value));

// ---- challenge-rules.js: window 외에 아무것도 주지 않는다(순수 함수 확인) ----
const window = {};
vm.runInNewContext(read('challenge-rules.js'), { window });
const R = window.CoDriveChallengeRules;
const { STATUS: S, RULES } = R;

const at = 1_800_000_000_000;
const ok = value => ({ value, quality: 'AVAILABLE', observedAt: at, source: 'MOCK' });
const missing = quality => ({ value: null, quality, observedAt: 0, source: 'MOCK' });
const stale = value => ({ value, quality: 'STALE', observedAt: at, source: 'MOCK' });
const MIN = 60, HOUR = 3600;
const check = (actual, status, reason) => {
  assert.equal(actual.status, status, JSON.stringify(plain(actual)));
  if (reason) assert.equal(actual.reason, reason, JSON.stringify(plain(actual)));
};

// 기준값은 한 곳의 상수다.
assert.deepEqual(plain(RULES), { REST_MAX_DRIVING_SECONDS: 7200, REST_MIN_SECONDS: 900, EFFICIENCY_TARGET_RATIO: 1.05, REUNION_TRIP_NUMBER: 3 });

// 1. 쉬어가는 여행
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(1 * HOUR + 50 * MIN), lastRestSeconds: ok(15 * MIN) }), S.ACHIEVED, 'RESTED');
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(2 * HOUR), lastRestSeconds: ok(20 * MIN) }), S.ACHIEVED, 'RESTED'); // 정확히 2시간은 "2시간 전" 안쪽
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(1 * HOUR), lastRestSeconds: ok(14 * MIN + 59) }), S.IN_PROGRESS, 'RESTING');
assert.equal(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(1 * HOUR), lastRestSeconds: ok(450) }).progress, 0.5);
check(R.evaluateRest({ motion: ok('MOVING'), drivingSeconds: ok(1 * HOUR + 59 * MIN), lastRestSeconds: ok(30 * MIN) }), S.IN_PROGRESS, 'DRIVING'); // 주행 중에는 지난 휴식으로 달성하지 않음
assert.equal(R.evaluateRest({ motion: ok('MOVING'), drivingSeconds: ok(1 * HOUR + 55 * MIN), lastRestSeconds: ok(0) }).values.remainingSeconds, 5 * MIN);
check(R.evaluateRest({ motion: ok('MOVING'), drivingSeconds: ok(2 * HOUR + 1), lastRestSeconds: ok(0) }), S.MISSED, 'OVER_LIMIT');
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(2 * HOUR + 10 * MIN), lastRestSeconds: ok(20 * MIN) }), S.MISSED, 'OVER_LIMIT');
check(R.evaluateRest({ motion: missing('NO_DATA'), drivingSeconds: ok(HOUR), lastRestSeconds: ok(20 * MIN) }), S.UNKNOWN);
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: stale(HOUR), lastRestSeconds: ok(20 * MIN) }), S.UNKNOWN);
check(R.evaluateRest({ motion: ok('STOPPED'), drivingSeconds: ok(HOUR), lastRestSeconds: missing('ERROR') }), S.UNKNOWN);
check(R.evaluateRest({ motion: ok('MOVING'), drivingSeconds: missing('NO_DATA'), lastRestSeconds: ok(0) }), S.UNKNOWN);
check(R.evaluateRest({}), S.UNKNOWN);

// 2. 부드러운 운전 (내 차량 · 공인 대비만)
check(R.evaluateEfficiency({ current: ok(14.2), rated: ok(12.8), unit: 'km/L' }), S.ACHIEVED, 'ABOVE_TARGET');
check(R.evaluateEfficiency({ current: ok(12.8 * 1.05), rated: ok(12.8), unit: 'km/L' }), S.ACHIEVED);
const below = R.evaluateEfficiency({ current: ok(13.1), rated: ok(12.8), unit: 'km/L' });
check(below, S.IN_PROGRESS, 'BELOW_TARGET');
assert.ok(below.progress > 0.95 && below.progress < 1);
const electric = R.evaluateEfficiency({ current: ok(6.0), rated: ok(5.2), unit: 'km/kWh' });
check(electric, S.ACHIEVED);
assert.equal(electric.values.electric, true);
check(R.evaluateEfficiency({ current: ok(4.9), rated: ok(5.2), unit: 'km/kWh' }), S.IN_PROGRESS);
check(R.evaluateEfficiency({ current: missing('NO_DATA'), rated: ok(12.8), unit: 'km/L' }), S.UNKNOWN);
check(R.evaluateEfficiency({ current: stale(20), rated: ok(12.8), unit: 'km/L' }), S.UNKNOWN);
check(R.evaluateEfficiency({ current: ok(14), rated: ok(0), unit: 'km/L' }), S.UNKNOWN);
check(R.evaluateEfficiency({ current: ok(14), rated: ok(12.8), unit: null }), S.UNKNOWN);
assert.deepEqual(Object.keys(below.values).sort(), ['current', 'electric', 'rated', 'ratio', 'unit']); // 다른 차량 값 없음

// 3. 완벽한 합류
const group = { groupId: 'g', name: '강릉 주말 여행', vehicleCount: 3 };
const at_ = (placeId, placeCategory = 'REST_AREA') => ({ arrival: ok({ arrived: true, placeId, placeName: placeId === 'r1' ? '횡성휴게소' : '다른 곳', placeCategory }) });
const away = { arrival: ok({ arrived: false, placeId: null, placeName: null, placeCategory: null }) };
const merged = R.evaluateMerge({ group, vehicles: [at_('r1'), at_('r1'), at_('r1')] });
check(merged, S.ACHIEVED, 'ALL_TOGETHER');
assert.equal(merged.values.placeName, '횡성휴게소');
const gathering = R.evaluateMerge({ group, vehicles: [at_('r1'), at_('r1'), away] });
check(gathering, S.IN_PROGRESS, 'GATHERING');
assert.deepEqual(plain(gathering.values), { together: 2, total: 3 }); // 대수만, 차량 식별 정보 없음
check(R.evaluateMerge({ group, vehicles: [at_('r1'), at_('r2'), at_('r2')] }), S.IN_PROGRESS); // 서로 다른 휴게소
check(R.evaluateMerge({ group, vehicles: [at_('d', 'ATTRACTION'), at_('d', 'ATTRACTION'), at_('d', 'ATTRACTION')] }), S.IN_PROGRESS, 'NOT_YET'); // 휴게소가 아님
check(R.evaluateMerge({ group, vehicles: [at_('r1'), at_('r1'), { arrival: missing('NO_DATA') }] }), S.UNKNOWN, 'NO_ARRIVAL_DATA');
check(R.evaluateMerge({ group, vehicles: [at_('r1'), at_('r1'), { arrival: stale({ arrived: true, placeId: 'r1', placeCategory: 'REST_AREA' }) }] }), S.UNKNOWN);
check(R.evaluateMerge({ group: null, vehicles: [] }), S.UNKNOWN, 'NO_GROUP');

// 4. 만장일치 (여행이 끝나야 확정)
const vote = (agree, total) => ({ kind: 'VOTE', title: 't', result: 'r', votes: { agree, total } });
const roulette = { kind: 'ROULETTE', title: '톨비 정하기', result: 'B 차량' };
check(R.evaluateUnanimous({ decisions: [vote(3, 3), roulette, vote(2, 2)], ended: true }), S.ACHIEVED, 'ALL_UNANIMOUS');
check(R.evaluateUnanimous({ decisions: [vote(3, 3), roulette], ended: false }), S.IN_PROGRESS, 'SO_FAR');
check(R.evaluateUnanimous({ decisions: [vote(3, 3), vote(2, 3)], ended: false }), S.MISSED, 'SPLIT');
check(R.evaluateUnanimous({ decisions: [roulette], ended: false }), S.IN_PROGRESS, 'NO_VOTES');
check(R.evaluateUnanimous({ decisions: [], ended: true }), S.MISSED, 'NO_VOTES');
check(R.evaluateUnanimous({ decisions: [vote(3, 3), { kind: 'VOTE', title: 't', result: 'r' }], ended: true }), S.UNKNOWN, 'NO_VOTE_DATA');
check(R.evaluateUnanimous({ decisions: [vote(4, 3)], ended: true }), S.UNKNOWN);
check(R.evaluateUnanimous({ decisions: undefined, ended: true }), S.UNKNOWN);

// 5. 또 만났네요
check(R.evaluateReunion({ group, tripNumber: ok(3) }), S.ACHIEVED, 'REACHED');
check(R.evaluateReunion({ group, tripNumber: ok(5) }), S.ACHIEVED);
const second = R.evaluateReunion({ group, tripNumber: ok(2) });
check(second, S.IN_PROGRESS, 'NOT_YET');
assert.equal(second.progress, 2 / 3);
check(R.evaluateReunion({ group, tripNumber: missing('NO_DATA') }), S.UNKNOWN, 'NO_HISTORY');
check(R.evaluateReunion({ group, tripNumber: ok(0) }), S.UNKNOWN);
check(R.evaluateReunion({ group: null, tripNumber: ok(3) }), S.UNKNOWN, 'NO_GROUP');

// 전체 판정: 기록된 달성은 지금 값이 바뀌어도 유지된다. 기록되지 않은 과제는 지금 값대로.
const all = R.evaluateAll({
  rest: { motion: ok('MOVING'), drivingSeconds: ok(10), lastRestSeconds: ok(20 * MIN) },
  efficiency: { current: missing('STALE'), rated: ok(12.8), unit: 'km/L' },
  merge: { group, vehicles: [at_('r1'), away, away] },
  unanimous: { decisions: [vote(3, 3)], ended: false },
  reunion: { group, tripNumber: ok(3) }
}, ['rest-before-2h']);
assert.deepEqual(plain(all.map(item => [item.id, item.status, item.recorded])), [
  ['rest-before-2h', 'ACHIEVED', true], ['smooth-driving', 'UNKNOWN', false], ['perfect-merge', 'IN_PROGRESS', false],
  ['unanimous', 'IN_PROGRESS', false], ['reunion', 'ACHIEVED', false]]);
assert.equal(R.evaluateAll({}).every(item => item.status === 'UNKNOWN'), true, '값이 하나도 없으면 모두 확인할 수 없음');
assert.ok(Object.isFrozen(all) && Object.isFrozen(all[0]));

// ---- dev-panel.js: ?dev=1 이 없으면 location.search 외에는 아무것도 읽거나 쓰지 않는다 ----
for (const search of ['', '?scenario=driving', '?dev=0', '?dev=true']) {
  const touched = [];
  const guard = new Proxy({ location: { search } }, {
    get(target, key) {
      if (key === 'location') return target.location;
      touched.push(String(key));
      throw new Error('dev-panel.js 가 dev=1 없이 window.' + String(key) + ' 에 접근함');
    },
    set(target, key) { touched.push(String(key)); throw new Error('dev-panel.js 가 dev=1 없이 window.' + String(key) + ' 을 바꿈'); }
  });
  const forbidden = name => new Proxy({}, { get() { throw new Error('dev-panel.js 가 dev=1 없이 ' + name + ' 에 접근함'); } });
  vm.runInNewContext(read('dev-panel.js'), { window: guard, document: forbidden('document'), URLSearchParams, URL: forbidden('URL') });
  assert.deepEqual(touched, [], search);
}

console.log('PASS: rest (achieved · resting · driving · over 2h · no data), efficiency (target · EV · below · stale), '
  + 'merge (all · partial · different rest areas · not a rest area · unknown vehicle · no group), unanimous (ended · so far · split · missing tally), '
  + 'reunion (Nth trip · earlier · no history · no group), recorded achievements kept, dev panel inert without dev=1');
