// 차량 데이터의 유일한 진입점. 화면 코드는 주행 상태·연속 주행 시간·연비를 직접 만들지 않고 이 함수만 사용한다.
// 현재는 mock-scenario.js 의 시연 데이터를 반환한다. Vehicle SDK를 연결할 때는 "데이터 출처" 구역만 교체하고
// 공개 함수의 이름·반환 형식(Measurement)은 유지한다.
//
// 필드명은 vehicle-status-ui 브랜치의 com.codrive.convoy.data.model.VehicleStatus 를 따른다.
//   motion          → Measurement<Motion>  (MOVING · STOPPED · UNKNOWN)
//   drivingSeconds  → Measurement<Double>
//   tripDistance    → Measurement<Double>  (이번 여행 주행 거리. km 단위로 가정 — fun-pending-decisions.md 참고)
//   powertrain      → EV · ICE · HEV · PHEV · UNKNOWN
// 아래는 현재 모델에 없는 "추가 예정 필드"다. 모델에 추가되면 같은 이름으로 연결한다.
//   [추가 예정] motionHeldSeconds   현재 motion 값이 유지된 시간(초)
//   [추가 예정] lastRestSeconds     가장 최근 정차(휴식) 길이(초)
//   [추가 예정] fuelEfficiency      현재 주행 연비(전기차는 전비)
//   [추가 예정] ratedFuelEfficiency 공인 연비(전기차는 공인 전비)
(() => {
  'use strict';
  const M = window.CoDriveMeasurement;
  const mock = window.CoDriveMock;
  if (!M || !mock) throw new Error('vehicle.js 보다 measurement.js 와 mock-scenario.js 를 먼저 불러와야 합니다.');

  const MOTION = Object.freeze({ MOVING: 'MOVING', STOPPED: 'STOPPED', UNKNOWN: 'UNKNOWN' });
  // 실제 SDK의 갱신 주기를 확인한 뒤 조정한다. 이 시간보다 오래된 주행 상태는 STALE로 낮춰 안전 판단에 쓰지 않는다.
  const MOTION_STALE_MS = 5000;

  // ---- 데이터 출처 (SDK 연결 시 이 부분만 교체) ----
  function readRaw() { return mock.read(); }
  function subscribeRaw(listener) { return mock.onChange(listener); }
  const clockNow = () => mock.now(); // SDK 연결 시 Date.now()
  const SOURCE = M.SOURCE.MOCK;
  // ------------------------------------------------

  const quantity = (value, at, max) => value == null
    ? M.missing(M.QUALITY.NO_DATA, SOURCE)
    : M.number(value, M.QUALITY.AVAILABLE, at, SOURCE, max);

  /**
   * 현재 주행 상태. 값은 'MOVING' | 'STOPPED' | 'UNKNOWN' (VehicleStatus.Motion 의 이름 그대로).
   * 기어 상태 필드가 모델에 추가되면 'PARKED' 를 함께 검토한다.
   * @returns {Promise<Readonly<{value: string|null, quality: string, observedAt: number, source: string}>>}
   */
  async function getMotionState() {
    const raw = readRaw();
    const motion = raw.vehicle.motion;
    const m = Object.hasOwn(MOTION, motion) && motion !== MOTION.UNKNOWN
      ? M.create(motion, M.QUALITY.AVAILABLE, raw.observedAt, SOURCE)
      : M.missing(M.QUALITY.NO_DATA, SOURCE);
    return M.expire(m, clockNow(), MOTION_STALE_MS);
  }

  /**
   * [추가 예정 필드 motionHeldSeconds] 현재 주행 상태가 바뀌지 않고 유지된 시간(초).
   * SDK가 이 값을 주지 않으면 이 모듈이 motion 값이 바뀐 시각을 기억해 계산한다.
   * play-safety.js 의 "정차 120초 유지" 판단에 사용한다.
   */
  async function getMotionHeldSeconds() {
    const raw = readRaw();
    return M.expire(quantity(raw.vehicle.motionHeldSeconds, raw.observedAt), clockNow(), MOTION_STALE_MS);
  }

  /**
   * 마지막 휴식 이후 연속 주행 시간(초). 모델 필드 drivingSeconds 를 사용한다.
   * drivingSeconds 가 "여행 전체 주행 시간"인지 "마지막 휴식 이후"인지는 차량 상태 담당과 확인이 필요하다.
   */
  async function getContinuousDrivingSeconds() {
    const raw = readRaw();
    return quantity(raw.vehicle.drivingSeconds, raw.observedAt);
  }

  /** 이번 여행 주행 거리(km). 모델 필드 tripDistance 를 사용한다. 연비 과제의 최소 거리 판정에 쓴다. */
  async function getTripDistance() {
    const raw = readRaw();
    return quantity(raw.vehicle.tripDistance, raw.observedAt);
  }

  /** [추가 예정 필드 lastRestSeconds] 가장 최근 정차 길이(초). 정차 중이면 지금까지의 정차 시간이다. */
  async function getLastRestSeconds() {
    const raw = readRaw();
    return quantity(raw.vehicle.lastRestSeconds, raw.observedAt);
  }

  /**
   * [추가 예정 필드 fuelEfficiency · ratedFuelEfficiency] 현재 연비와 공인 연비.
   * 다른 차량과 비교하지 않고 내 차량의 공인 연비 대비 효율만 표현하는 데 사용한다.
   * @returns {Promise<{current: object, rated: object, unit: 'km/L'|'km/kWh'|null}>}
   */
  async function getFuelEfficiency() {
    const raw = readRaw();
    const powertrain = raw.vehicle.powertrain;
    const unit = powertrain === 'EV' ? 'km/kWh' : powertrain === 'UNKNOWN' || !powertrain ? null : 'km/L';
    return Object.freeze({
      current: quantity(raw.vehicle.fuelEfficiency, raw.observedAt),
      rated: quantity(raw.vehicle.ratedFuelEfficiency, raw.observedAt),
      unit
    });
  }

  /**
   * 차량 상태가 바뀌거나 시간이 흐를 때 알린다. 콜백은 인자 없이 호출되며, 필요한 값은 위 함수로 다시 읽는다.
   * @returns {() => void} 구독 해제 함수
   */
  function subscribe(callback) { return subscribeRaw(callback); }

  window.CoDriveVehicle = Object.freeze({
    MOTION, getMotionState, getMotionHeldSeconds, getContinuousDrivingSeconds, getTripDistance, getLastRestSeconds, getFuelEfficiency, subscribe
  });
})();
