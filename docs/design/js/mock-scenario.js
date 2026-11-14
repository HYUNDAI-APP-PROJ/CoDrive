// 시연용 가짜 데이터의 단일 출처. vehicle.js · navi.js · group.js 가 이 값을 읽어 Measurement로 바꾼다.
// 화면 코드는 이 파일을 직접 읽지 않는다. Vehicle SDK · NaviHelper · 그룹 서버를 연결하면
// 각 모듈의 read 부분만 교체하고 이 파일은 시연·검사용으로만 남긴다.
//
// 시나리오 전환: 페이지 주소에 ?scenario=<이름> (예: ?scenario=red-light)
// 그룹 없음: ?group=none (route-recommendation.html 과 같은 규칙)
// 선택한 시나리오와 시작 시각은 같은 탭의 세션에 보관해 새로고침해도 경과 시간이 이어진다.
(() => {
  'use strict';
  const STORAGE_KEY = 'codrive.fun.mock.v1';
  const MIN = 60, HOUR = 3600;

  const PLACES = Object.freeze({
    rest: Object.freeze({ placeId: 'mock-rest-hoengseong', placeName: '횡성휴게소', stopKind: 'WAYPOINT', placeCategory: 'REST_AREA' }),
    destination: Object.freeze({ placeId: 'mock-dest-anmok', placeName: '안목해변', stopKind: 'DESTINATION', placeCategory: 'ATTRACTION' })
  });

  // 각 시나리오는 "시나리오를 고른 순간"의 상태다. 이후 시간이 흐르면 유지 시간·주행 시간이 늘어난다.
  // motionHeld: 현재 motion이 이미 유지된 초, driving: 마지막 휴식 이후 연속 주행 초,
  // lastRest: 가장 최근 정차 길이(정차 중이면 현재 정차가 계속 늘어남),
  // arrivals: 그룹 차량별 도착 장소 키(null이면 미도착)와 도착 후 경과 초.
  const SCENARIOS = Object.freeze({
    driving: {
      label: '주행 중', motion: 'MOVING', motionHeld: 40 * MIN, driving: 1 * HOUR + 55 * MIN, lastRest: 6 * MIN,
      efficiency: 14.2, navi: { navigation: 'GUIDING', place: 'rest' },
      arrivals: { 'vehicle-a': null, 'vehicle-b': null, 'vehicle-c': null }
    },
    'red-light': {
      label: '신호 대기 (정차 직후)', motion: 'STOPPED', motionHeld: 0, driving: 1 * HOUR + 20 * MIN, lastRest: 0,
      efficiency: 13.1, navi: { navigation: 'GUIDING', place: 'rest' },
      arrivals: { 'vehicle-a': null, 'vehicle-b': null, 'vehicle-c': null }
    },
    'long-stop': {
      label: '2분 넘게 정차', motion: 'STOPPED', motionHeld: 150, driving: 1 * HOUR + 20 * MIN, lastRest: 150,
      efficiency: 13.1, navi: { navigation: 'GUIDING', place: 'rest' },
      arrivals: { 'vehicle-a': null, 'vehicle-b': null, 'vehicle-c': null }
    },
    'rest-stop': {
      label: '휴게소 도착 (일부 차량)', motion: 'STOPPED', motionHeld: 10, driving: 1 * HOUR + 50 * MIN, lastRest: 10,
      efficiency: 14.0, navi: { navigation: 'ARRIVED', place: 'rest' },
      arrivals: { 'vehicle-a': { place: 'rest', ago: 10 }, 'vehicle-b': { place: 'rest', ago: 95 }, 'vehicle-c': null }
    },
    'all-arrived': {
      label: '휴게소 전원 합류', motion: 'STOPPED', motionHeld: 16 * MIN, driving: 1 * HOUR + 50 * MIN, lastRest: 16 * MIN,
      efficiency: 14.0, navi: { navigation: 'ARRIVED', place: 'rest' },
      arrivals: { 'vehicle-a': { place: 'rest', ago: 16 * MIN }, 'vehicle-b': { place: 'rest', ago: 17 * MIN }, 'vehicle-c': { place: 'rest', ago: 4 * MIN } }
    },
    'leaving-rest-stop': {
      label: '휴게소 출발 (도착 표시 남음)', motion: 'MOVING', motionHeld: 5, driving: 5, lastRest: 18 * MIN,
      efficiency: 14.0, navi: { navigation: 'ARRIVED', place: 'rest' },
      arrivals: { 'vehicle-a': { place: 'rest', ago: 19 * MIN }, 'vehicle-b': { place: 'rest', ago: 20 * MIN }, 'vehicle-c': { place: 'rest', ago: 7 * MIN } }
    },
    destination: {
      label: '목적지 도착', motion: 'STOPPED', motionHeld: 5, driving: 1 * HOUR + 30 * MIN, lastRest: 5,
      efficiency: 13.6, navi: { navigation: 'ARRIVED', place: 'destination' },
      arrivals: { 'vehicle-a': { place: 'destination', ago: 5 }, 'vehicle-b': { place: 'destination', ago: 60 }, 'vehicle-c': null }
    },
    unknown: {
      label: '차량 정보 없음', motion: 'UNKNOWN', motionHeld: null, driving: null, lastRest: null,
      efficiency: null, navi: null,
      arrivals: { 'vehicle-a': null, 'vehicle-b': null, 'vehicle-c': null }
    }
  });
  const DEFAULT_SCENARIO = 'driving';

  // route-recommendation.html 의 mockGroup 과 같은 그룹·차량·탑승자 이름을 사용한다.
  const GROUP = Object.freeze({
    groupId: 'mock-gangneung', name: '강릉 주말 여행', myVehicleId: 'vehicle-a',
    vehicles: Object.freeze([
      Object.freeze({ vehicleId: 'vehicle-a', displayName: 'A 차량', memberName: '나', displayOrder: 0 }),
      Object.freeze({ vehicleId: 'vehicle-b', displayName: 'B 차량', memberName: '민수', displayOrder: 1 }),
      Object.freeze({ vehicleId: 'vehicle-c', displayName: 'C 차량', memberName: '지연', displayOrder: 2 })
    ])
  });
  const CURRENT_LOCATION_ON_ROAD = '원주 부근'; // 휴게소로 가는 도중의 현재 위치 표시
  const RATED_EFFICIENCY = 12.8; // 시연 차량의 공인 복합 연비(km/L)

  let testNow = null;
  const now = () => testNow ?? Date.now();
  const params = new URLSearchParams(window.location?.search || '');
  const hasGroup = params.get('group') !== 'none';

  function load() {
    try {
      const saved = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) || 'null');
      if (saved && Object.hasOwn(SCENARIOS, saved.name) && Number.isFinite(saved.startedAt)) return saved;
    } catch { /* 저장 상태가 없거나 손상되면 기본 시나리오로 시작한다. */ }
    return null;
  }
  function save() {
    try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state)); }
    catch { /* 저장 없이도 현재 화면은 동작한다. */ }
  }

  const requested = params.get('scenario');
  let state = load();
  if (Object.hasOwn(SCENARIOS, requested) && state?.name !== requested) state = null;
  if (!state) state = { name: Object.hasOwn(SCENARIOS, requested) ? requested : DEFAULT_SCENARIO, startedAt: now() };
  save();

  const listeners = new Set();
  let ticker = null;
  function emit() { for (const listener of [...listeners]) { try { listener(); } catch (error) { console.error(error); } } }
  function onChange(listener) {
    listeners.add(listener);
    if (!ticker && typeof window.setInterval === 'function') ticker = window.setInterval(emit, 1000);
    return () => {
      listeners.delete(listener);
      if (!listeners.size && ticker) { window.clearInterval(ticker); ticker = null; }
    };
  }

  function setScenario(name) {
    if (!Object.hasOwn(SCENARIOS, name)) throw new RangeError('Unknown scenario: ' + name);
    state = { name, startedAt: now() };
    save();
    emit();
  }

  // 시연용: 연속 주행 시간을 지정한 값으로 바꾼다. 주행 중이면 이 값부터 다시 늘어난다. 시나리오를 바꾸면 해제된다.
  function setDrivingSecondsForDemo(seconds) {
    if (!Number.isFinite(seconds) || seconds < 0) throw new RangeError('Invalid driving seconds: ' + seconds);
    if (SCENARIOS[state.name].driving == null) return false; // 차량 정보가 없는 시나리오에서는 바꾸지 않는다.
    state = { ...state, driving: { seconds, at: now() } };
    save();
    emit();
    return true;
  }

  // 남은 안내 경로. 이미 지난 지점은 넣지 않는다(design-guide 현재 경로 패널 규칙).
  // 휴게소로 가는 중이면 휴게소 → 목적지, 휴게소에 도착했으면 목적지만, 목적지에 도착했으면 남은 경로 없음.
  function routeFor(navi, place) {
    const arrived = navi.navigation === 'ARRIVED';
    const stops = [];
    if (place.stopKind === 'WAYPOINT') {
      if (!arrived) stops.push(place);
      stops.push(PLACES.destination);
    } else if (!arrived) stops.push(place);
    return {
      ownerLabel: hasGroup ? GROUP.name : '개인',
      currentLocation: arrived ? place.placeName : CURRENT_LOCATION_ON_ROAD,
      stops: stops.map(stop => ({ stopKind: stop.stopKind, placeId: stop.placeId, placeName: stop.placeName }))
    };
  }

  // 현재 시각 기준의 원시 값. 단위는 초·밀리초이며 Measurement 변환은 각 모듈이 맡는다.
  function read() {
    const at = now();
    const s = SCENARIOS[state.name];
    const elapsed = Math.max(0, (at - state.startedAt) / 1000);
    const known = s.motion !== 'UNKNOWN';
    const stopped = s.motion === 'STOPPED';
    const moving = s.motion === 'MOVING';
    const place = s.navi ? PLACES[s.navi.place] : null;
    return {
      observedAt: at,
      scenario: state.name,
      vehicle: {
        vehicleId: GROUP.myVehicleId, displayName: 'A 차량', powertrain: 'ICE',
        motion: known ? s.motion : null,
        motionHeldSeconds: known ? s.motionHeld + elapsed : null,
        drivingSeconds: s.driving == null ? null : state.driving
          ? state.driving.seconds + (moving ? Math.max(0, (at - state.driving.at) / 1000) : 0)
          : s.driving + (moving ? elapsed : 0),
        lastRestSeconds: s.lastRest == null ? null : s.lastRest + (stopped ? elapsed : 0),
        fuelEfficiency: s.efficiency,
        ratedFuelEfficiency: known ? RATED_EFFICIENCY : null
      },
      navi: s.navi ? {
        navigation: s.navi.navigation,
        stopKind: place.stopKind, placeId: place.placeId, placeName: place.placeName,
        route: routeFor(s.navi, place)
      } : null,
      group: hasGroup ? {
        groupId: GROUP.groupId, name: GROUP.name, myVehicleId: GROUP.myVehicleId,
        vehicles: GROUP.vehicles.map(vehicle => {
          const arrival = s.arrivals[vehicle.vehicleId];
          const arrivedPlace = arrival ? PLACES[arrival.place] : null;
          return {
            ...vehicle,
            connection: 'ONLINE',
            arrived: !!arrival,
            placeId: arrivedPlace?.placeId ?? null,
            placeName: arrivedPlace?.placeName ?? null,
            placeCategory: arrivedPlace?.placeCategory ?? null,
            arrivedAt: arrival ? Math.round(at - (arrival.ago + elapsed) * 1000) : null
          };
        })
      } : null
    };
  }

  window.CoDriveMock = Object.freeze({
    now, read, onChange, setScenario, setDrivingSecondsForDemo,
    scenario: () => state.name,
    scenarios: () => Object.entries(SCENARIOS).map(([name, s]) => ({ name, label: s.label })),
    // 검사 스크립트 전용: 시계를 고정·이동한다. 화면 코드에서 호출하지 않는다.
    setNowForTest(ms) { testNow = ms; emit(); }
  });
})();
