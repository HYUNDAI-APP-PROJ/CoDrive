// CoDrive 측정값 형식. vehicle-status-ui 브랜치의
// com.codrive.convoy.data.model.Measurement(Java)와 같은 규칙을 따른다.
// 모든 데이터 모듈(vehicle·navi·group)은 값을 이 형식으로만 돌려준다.
(() => {
  'use strict';
  const QUALITY = Object.freeze({
    AVAILABLE: 'AVAILABLE', LOADING: 'LOADING', NO_DATA: 'NO_DATA', NOT_SHARED: 'NOT_SHARED',
    UNSUPPORTED: 'UNSUPPORTED', PERMISSION_DENIED: 'PERMISSION_DENIED', STALE: 'STALE', ERROR: 'ERROR'
  });
  const SOURCE = Object.freeze({
    MOCK: 'MOCK', VEHICLE_SDK: 'VEHICLE_SDK', NAVIHELPER: 'NAVIHELPER',
    FUSED_LOCATION: 'FUSED_LOCATION', USER_INPUT: 'USER_INPUT'
  });

  function deepFreeze(value) {
    if (value && typeof value === 'object' && !Object.isFrozen(value)) {
      Object.values(value).forEach(deepFreeze);
      Object.freeze(value);
    }
    return value;
  }

  // Java 생성자와 동일: 값을 유지할 수 있는 품질(AVAILABLE·STALE)인데 값이나 측정 시각이 없으면 NO_DATA,
  // 그 밖의 품질에서는 값을 버린다.
  function create(value, quality, observedAt, source) {
    if (!Object.hasOwn(QUALITY, quality)) throw new TypeError('Unknown quality: ' + quality);
    if (!Object.hasOwn(SOURCE, source)) throw new TypeError('Unknown source: ' + source);
    const canKeep = quality === QUALITY.AVAILABLE || quality === QUALITY.STALE;
    const finalQuality = canKeep && (value == null || !(observedAt > 0)) ? QUALITY.NO_DATA : quality;
    return deepFreeze({
      value: canKeep && finalQuality === quality ? value : null,
      quality: finalQuality,
      observedAt,
      source
    });
  }

  const mock = (value, at) => create(value, QUALITY.AVAILABLE, at, SOURCE.MOCK);
  const missing = (quality, source = SOURCE.MOCK) => create(null, quality, 0, source);

  // Java Measurement.number와 동일: 유한하지 않거나 음수이거나 최대값을 넘으면 ERROR.
  function number(value, quality, at, source, max = Number.MAX_VALUE) {
    if (quality !== QUALITY.AVAILABLE && quality !== QUALITY.STALE) return create(null, quality, at, source);
    if (value != null && (!Number.isFinite(value) || value < 0 || value > max)) return create(null, QUALITY.ERROR, at, source);
    return create(value, quality, at, source);
  }

  const isAvailable = m => !!m && m.quality === QUALITY.AVAILABLE && m.value != null;
  const hasLastValue = m => !!m && m.value != null && (m.quality === QUALITY.AVAILABLE || m.quality === QUALITY.STALE);
  const expire = (m, now, thresholdMillis) => isAvailable(m) && now - m.observedAt > thresholdMillis
    ? create(m.value, QUALITY.STALE, m.observedAt, m.source) : m;

  window.CoDriveMeasurement = Object.freeze({ QUALITY, SOURCE, create, mock, missing, number, isAvailable, hasLastValue, expire });
})();
