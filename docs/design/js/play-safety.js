// 룰렛·사다리타기 같은 정차 중 게임의 잠금 해제 판단. 화면은 이 모듈의 결과만 보고 잠금을 결정한다.
// 차량 상태는 vehicle.js, 도착 여부는 navi.js 에서만 가져온다.
//
// 잠금 해제 조건 (팀 합의 2026-11-15)
//   (a) 경유지 또는 목적지에 도착한 상태
//   (b) STOPPED 가 120초 이상 유지된 상태
//   주행 상태를 알 수 없으면(UNKNOWN · NO_DATA · STALE 등) 잠근다.
// 추가 안전 규칙: (a)도 차량이 STOPPED 일 때만 인정한다. 도착 표시가 남은 채 다시 출발한 경우를 막기 위해서다.
// TODO: 기어 상태 필드 추가 시 parked 조건 추가
(() => {
  'use strict';
  const M = window.CoDriveMeasurement;
  const vehicle = window.CoDriveVehicle;
  const navi = window.CoDriveNavi;
  if (!M || !vehicle || !navi) throw new Error('play-safety.js 보다 measurement.js · vehicle.js · navi.js 를 먼저 불러와야 합니다.');

  const STOP_HOLD_SECONDS = 120;
  const REASON = Object.freeze({
    ARRIVED: 'ARRIVED',             // 해제: 경유지·목적지 도착 후 정차
    STOPPED_LONG: 'STOPPED_LONG',   // 해제: 120초 이상 정차
    STOPPED_SHORT: 'STOPPED_SHORT', // 잠금: 정차했지만 120초 미만
    MOVING: 'MOVING',               // 잠금: 주행 중
    UNKNOWN: 'UNKNOWN'              // 잠금: 주행 상태를 알 수 없음
  });

  /**
   * 잠금 여부와 이유. 화면 안내 문구와 남은 대기 시간 표시에 사용한다.
   * @returns {Promise<Readonly<{safe: boolean, reason: string, waitSeconds: number|null}>>}
   *   waitSeconds: STOPPED_SHORT 일 때 잠금 해제까지 남은 초(올림). 그 밖에는 null.
   */
  async function getPlaySafety() {
    const [motion, held, arrived] = await Promise.all([
      vehicle.getMotionState(), vehicle.getMotionHeldSeconds(), navi.isArrivedAtStop()
    ]);
    const result = (safe, reason, waitSeconds = null) => Object.freeze({ safe, reason, waitSeconds });
    if (!M.isAvailable(motion)) return result(false, REASON.UNKNOWN);
    if (motion.value !== vehicle.MOTION.STOPPED) return result(false, motion.value === vehicle.MOTION.MOVING ? REASON.MOVING : REASON.UNKNOWN);
    // 기어 상태 필드 추가 시 parked 조건 추가
    if (arrived) return result(true, REASON.ARRIVED);
    if (!M.isAvailable(held)) return result(false, REASON.UNKNOWN);
    if (held.value >= STOP_HOLD_SECONDS) return result(true, REASON.STOPPED_LONG);
    return result(false, REASON.STOPPED_SHORT, Math.ceil(STOP_HOLD_SECONDS - held.value));
  }

  /** 게임을 사용해도 되는지. @returns {Promise<boolean>} */
  async function isSafeToPlay() { return (await getPlaySafety()).safe; }

  /**
   * 차량·도착 상태가 바뀔 때 알린다. 게임 진행 중 MOVING 으로 바뀌면 화면이 즉시 중단할 수 있도록 사용한다.
   * @returns {() => void} 구독 해제 함수
   */
  function subscribe(callback) {
    const offVehicle = vehicle.subscribe(callback);
    const offNavi = navi.subscribe(callback);
    return () => { offVehicle(); offNavi(); };
  }

  window.CoDrivePlaySafety = Object.freeze({ STOP_HOLD_SECONDS, REASON, getPlaySafety, isSafeToPlay, subscribe });
})();
