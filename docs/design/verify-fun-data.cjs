// Run with: node docs/design/verify-fun-data.cjs
// 엔터 요소 데이터 모듈(vehicle · navi · group · play-safety · trip-log)의 규칙을 확인한다.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const ORDER = ['measurement.js', 'mock-scenario.js', 'vehicle.js', 'navi.js', 'group.js', 'play-safety.js', 'trip-log.js'];
// vm 안에서 만든 배열·객체는 프로토타입이 달라 deepEqual 전에 일반 값으로 바꾼다.
const plain = value => JSON.parse(JSON.stringify(value));
const sources = ORDER.map(name => [name, fs.readFileSync(path.join(__dirname, 'js', name), 'utf8')]);

// 같은 storage 를 넘기면 같은 탭에서 새로고침한 것과 같다.
function boot({ search = '', storage = new Map() } = {}) {
  const intervals = [];
  const window = {
    location: { search },
    sessionStorage: {
      getItem: key => storage.has(key) ? storage.get(key) : null,
      setItem: (key, value) => storage.set(key, String(value))
    },
    setInterval: fn => { intervals.push(fn); return intervals.length; },
    clearInterval: id => { intervals[id - 1] = null; }
  };
  const context = vm.createContext({ window, console, URLSearchParams });
  for (const [name, code] of sources) vm.runInContext(code, context, { filename: name });
  return { window, storage, intervals };
}

// 시계를 고정한 뒤 시나리오를 다시 골라 경과 시간을 0부터 정확히 센다.
function at(window, scenario, t0 = 1_800_000_000_000) {
  window.CoDriveMock.setNowForTest(t0);
  window.CoDriveMock.setScenario(scenario);
  return seconds => window.CoDriveMock.setNowForTest(t0 + seconds * 1000);
}

(async () => {
  const { window } = boot();
  const { CoDriveMeasurement: M, CoDriveVehicle: vehicle, CoDriveNavi: navi, CoDriveGroup: group,
    CoDrivePlaySafety: safety, CoDriveTripLog: tripLog, CoDriveMock: mock } = window;
  assert.equal(window.CoDrive, undefined, '데이터 모듈은 네이티브 브리지 이름(window.CoDrive)을 쓰지 않는다');

  // Measurement 는 Java 모델과 같은 규칙을 따른다.
  assert.deepEqual(Object.keys(M.mock(1, 10)), ['value', 'quality', 'observedAt', 'source']);
  assert.equal(M.create(null, 'AVAILABLE', 10, 'MOCK').quality, 'NO_DATA');
  assert.equal(M.create(3, 'AVAILABLE', 0, 'MOCK').quality, 'NO_DATA');
  assert.equal(M.create(3, 'LOADING', 10, 'MOCK').value, null);
  assert.equal(M.number(-1, 'AVAILABLE', 10, 'MOCK').quality, 'ERROR');
  assert.equal(M.number(101, 'AVAILABLE', 10, 'MOCK', 100).quality, 'ERROR');
  assert.equal(M.expire(M.mock(1, 1000), 7000, 5000).quality, 'STALE');
  assert.throws(() => M.create(1, 'GOOD', 10, 'MOCK'));
  assert.ok(Object.isFrozen(M.mock({ a: 1 }, 10).value));

  // 기본 시나리오는 주행 중이며 게임은 잠긴다.
  assert.equal(mock.scenario(), 'driving');
  let tick = at(window, 'driving');
  assert.equal((await vehicle.getMotionState()).value, 'MOVING');
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: false, reason: 'MOVING', waitSeconds: null });
  const drivingBefore = (await vehicle.getContinuousDrivingSeconds()).value;
  tick(300);
  assert.equal((await vehicle.getContinuousDrivingSeconds()).value, drivingBefore + 300, '주행 중에는 연속 주행 시간이 늘어난다');
  assert.ok((await vehicle.getTripDistance()).value >= 20, '시연 주행 거리는 연비 과제 최소 거리 이상');
  const efficiency = await vehicle.getFuelEfficiency();
  assert.equal(efficiency.unit, 'km/L');
  assert.ok(M.isAvailable(efficiency.current) && M.isAvailable(efficiency.rated));

  // (b) 정차 120초: 119초까지 잠금, 120초에 해제.
  tick = at(window, 'red-light');
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: false, reason: 'STOPPED_SHORT', waitSeconds: 120 });
  tick(119);
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: false, reason: 'STOPPED_SHORT', waitSeconds: 1 });
  tick(120);
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: true, reason: 'STOPPED_LONG', waitSeconds: null });
  assert.equal(await safety.isSafeToPlay(), true);

  at(window, 'long-stop');
  assert.equal((await safety.getPlaySafety()).reason, 'STOPPED_LONG');

  // (a) 경유지·목적지 도착이면 정차 120초를 기다리지 않는다.
  at(window, 'rest-stop');
  assert.equal((await vehicle.getMotionHeldSeconds()).value, 10);
  assert.equal(await navi.isArrivedAtStop(), true);
  assert.equal((await navi.getArrivalState()).value.stopKind, 'WAYPOINT');
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: true, reason: 'ARRIVED', waitSeconds: null });
  at(window, 'destination');
  assert.equal((await navi.getArrivalState()).value.stopKind, 'DESTINATION');
  assert.equal((await safety.getPlaySafety()).reason, 'ARRIVED');

  // 도착 표시가 남아 있어도 다시 주행하면 잠근다.
  at(window, 'leaving-rest-stop');
  assert.equal(await navi.isArrivedAtStop(), true);
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: false, reason: 'MOVING', waitSeconds: null });

  // 주행 상태를 알 수 없으면 잠근다.
  at(window, 'unknown');
  const unknownMotion = await vehicle.getMotionState();
  assert.equal(unknownMotion.value, null);
  assert.equal(unknownMotion.quality, 'NO_DATA');
  assert.deepEqual({ ...(await safety.getPlaySafety()) }, { safe: false, reason: 'UNKNOWN', waitSeconds: null });
  assert.equal((await navi.getArrivalState()).quality, 'NO_DATA');
  assert.equal((await vehicle.getFuelEfficiency()).current.quality, 'NO_DATA');
  assert.equal((await vehicle.getTripDistance()).quality, 'NO_DATA');

  // 시연 시나리오의 주행 거리는 모두 연비 과제 최소 거리(20km) 이상이다. 주행 중에는 늘어난다.
  for (const { name } of mock.scenarios().filter(item => item.name !== 'unknown')) {
    at(window, name);
    assert.ok((await vehicle.getTripDistance()).value >= 20, name + ' 주행 거리');
  }
  tick = at(window, 'driving');
  const distanceBefore = (await vehicle.getTripDistance()).value;
  tick(3600);
  assert.equal(Math.round((await vehicle.getTripDistance()).value - distanceBefore), 80);

  // 그룹 차량 목록과 도착 여부: 내 차량이 맨 앞.
  assert.deepEqual({ ...(await group.getGroup()) }, { groupId: 'mock-gangneung', name: '강릉 주말 여행', vehicleCount: 3 });
  at(window, 'rest-stop');
  let vehicles = await group.getGroupVehicles();
  assert.deepEqual(plain(vehicles.map(v => [v.displayName, v.memberName, v.isMine])), [['A 차량', '나', true], ['B 차량', '민수', false], ['C 차량', '지연', false]]);
  assert.deepEqual(plain(vehicles.map(v => v.arrival.value.arrived)), [true, true, false]);
  assert.equal(vehicles[0].arrival.value.placeId, vehicles[1].arrival.value.placeId);
  at(window, 'all-arrived');
  vehicles = await group.getGroupVehicles();
  assert.ok(vehicles.every(v => v.arrival.value.arrived && v.arrival.value.placeId === 'mock-rest-hoengseong'));
  const grouplessWindow = boot({ search: '?group=none' }).window;
  assert.equal(await grouplessWindow.CoDriveGroup.getGroup(), null);
  assert.equal((await grouplessWindow.CoDriveGroup.getGroupVehicles()).length, 0);

  // 구독: 상태 변화를 알리고, 해제하면 더 이상 알리지 않는다.
  let calls = 0;
  const off = safety.subscribe(() => calls++);
  mock.setScenario('driving');
  assert.ok(calls > 0);
  off();
  const afterOff = calls;
  mock.setScenario('red-light');
  assert.equal(calls, afterOff);

  // 새로고침해도 시나리오와 정차 경과 시간이 이어진다.
  const tab = boot({ search: '?scenario=red-light' });
  const t0 = tab.window.CoDriveMock.now();
  const reloaded = boot({ storage: tab.storage }).window;
  assert.equal(reloaded.CoDriveMock.scenario(), 'red-light');
  reloaded.CoDriveMock.setNowForTest(t0 + 121_000);
  assert.equal(await reloaded.CoDrivePlaySafety.isSafeToPlay(), true);
  assert.equal(boot({ search: '?scenario=nope' }).window.CoDriveMock.scenario(), 'driving');

  // 여행 기록: 시간순, 결정 추가, 도전과제 중복 방지, 종료 후 함께한 시간 고정, 새로고침 복원.
  // ?trip=N 으로 같은 그룹 여행 횟수를 바꾸고, 새로고침해도 유지된다. 범위 밖이면 기본값 3.
  const secondTrip = boot({ search: '?trip=2' });
  assert.equal((await secondTrip.window.CoDriveTripLog.getTripNumberWithGroup()).value, 2);
  assert.equal((await boot({ search: '?trip=2', storage: secondTrip.storage }).window.CoDriveTripLog.getTripNumberWithGroup()).value, 2);
  assert.equal((await boot({ search: '?trip=1', storage: secondTrip.storage }).window.CoDriveTripLog.getTripNumberWithGroup()).value, 1);
  assert.equal((await boot({ search: '?trip=99' }).window.CoDriveTripLog.getTripNumberWithGroup()).value, 3);

  const tripTab = boot();
  const log = tripTab.window.CoDriveTripLog;
  const tripMock = tripTab.window.CoDriveMock;
  let record = await log.getTripRecord();
  assert.deepEqual(plain(record.visits.map(v => v.placeName)), ['대청호 산책로', '호숫가 식당']);
  assert.equal(record.decisions.length, 1);
  const decided = log.recordDecision({ kind: 'ROULETTE', title: '톨비 정하기', result: 'B 차량' });
  assert.equal(decided.kind, 'ROULETTE');
  assert.throws(() => log.recordDecision({ kind: 'RACE', title: 'x', result: 'y' }));
  assert.throws(() => log.recordDecision({ kind: 'LADDER', title: '  ', result: 'y' }));
  assert.equal(log.recordAchievement({ challengeId: 'rest-after-2h', title: '2시간 운전 후 15분 휴식' }), true);
  assert.equal(log.recordAchievement({ challengeId: 'rest-after-2h', title: '2시간 운전 후 15분 휴식' }), false);
  assert.equal(log.endTrip(), true);
  record = await log.getTripRecord();
  tripMock.setNowForTest(tripMock.now() + 3_600_000);
  assert.equal((await log.getTripRecord()).togetherSeconds, record.togetherSeconds, '종료 후에는 함께한 시간이 늘지 않는다');
  const tripReloaded = await boot({ storage: tripTab.storage }).window.CoDriveTripLog.getTripRecord();
  assert.equal(tripReloaded.decisions.length, 2);
  assert.equal(tripReloaded.achievements.length, 1);
  assert.equal(tripReloaded.endedAt, record.endedAt);
  assert.equal(tripReloaded.togetherSeconds, 3 * 3600);

  console.log('PASS: measurement rules, play lock (arrival · 120s stop · moving · unknown), group arrivals, subscriptions, reload restore, trip log');
})().catch(error => { console.error(error); process.exit(1); });
