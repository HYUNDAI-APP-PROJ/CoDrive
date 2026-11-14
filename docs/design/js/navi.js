// 내비게이션 도착 정보의 진입점. 경유지·목적지 도착 여부는 vehicle.js 가 아니라 이 모듈에서만 가져온다.
// 현재는 mock-scenario.js 의 시연 데이터를 반환한다. 나중에 Pleos NaviHelper 의 도착 이벤트로 교체하며,
// 그때 readRaw()·subscribe() 만 바꾸고 source 는 NAVIHELPER 로 표시한다.
//
// navigation 값은 VehicleStatus.Navigation 의 이름을 따른다: GUIDING · ARRIVED · NO_ROUTE (알 수 없으면 Measurement NO_DATA)
(() => {
  'use strict';
  const M = window.CoDriveMeasurement;
  const mock = window.CoDriveMock;
  if (!M || !mock) throw new Error('navi.js 보다 measurement.js 와 mock-scenario.js 를 먼저 불러와야 합니다.');

  const NAVIGATION = Object.freeze({ GUIDING: 'GUIDING', ARRIVED: 'ARRIVED', NO_ROUTE: 'NO_ROUTE' });
  const STOP_KIND = Object.freeze({ WAYPOINT: 'WAYPOINT', DESTINATION: 'DESTINATION' });

  // ---- 데이터 출처 (NaviHelper 연결 시 이 부분만 교체) ----
  function readRaw() { return mock.read(); }
  function subscribeRaw(listener) { return mock.onChange(listener); }
  const SOURCE = M.SOURCE.MOCK;
  // -------------------------------------------------------

  /**
   * 현재 안내 상태와 다음(또는 도착한) 경유지·목적지.
   * value: { navigation, stopKind: 'WAYPOINT'|'DESTINATION'|null, placeId, placeName }
   * navigation 이 ARRIVED 이면 stopKind·place 는 도착한 곳, GUIDING 이면 향하는 곳이다.
   */
  async function getArrivalState() {
    const raw = readRaw();
    const navi = raw.navi;
    if (!navi || !Object.hasOwn(NAVIGATION, navi.navigation)) return M.missing(M.QUALITY.NO_DATA, SOURCE);
    const hasStop = navi.navigation !== NAVIGATION.NO_ROUTE && Object.hasOwn(STOP_KIND, navi.stopKind);
    return M.create({
      navigation: navi.navigation,
      stopKind: hasStop ? navi.stopKind : null,
      placeId: hasStop ? navi.placeId : null,
      placeName: hasStop ? navi.placeName : null
    }, M.QUALITY.AVAILABLE, raw.observedAt, SOURCE);
  }

  /** 경유지 또는 목적지에 도착한 상태인지. 정보를 알 수 없으면 false. */
  async function isArrivedAtStop() {
    const arrival = await getArrivalState();
    return M.isAvailable(arrival) && arrival.value.navigation === NAVIGATION.ARRIVED && arrival.value.stopKind !== null;
  }

  /** 도착 상태가 바뀌거나 시간이 흐를 때 알린다. @returns {() => void} 구독 해제 함수 */
  function subscribe(callback) { return subscribeRaw(callback); }

  window.CoDriveNavi = Object.freeze({ NAVIGATION, STOP_KIND, getArrivalState, isArrivedAtStop, subscribe });
})();
