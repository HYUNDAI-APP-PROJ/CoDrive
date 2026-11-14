// 공익 도전과제 판정 규칙. DOM·시계·데이터 모듈을 모르는 순수 함수만 둔다.
// 화면은 vehicle·group·trip-log 에서 값을 읽어 넘기고, 결과(status·reason·progress·values)를 문구로 바꾼다.
//
// 원칙
// - 속도·도착 순서·주행 거리 경쟁, 차량별 비교·순위를 만들지 않는다. 그룹 공동 상태와 내 차량의 기준 대비 값만 쓴다.
// - 판정에 필요한 값이 AVAILABLE 이 아니면(NO_DATA · STALE · ERROR 등) UNKNOWN 으로 두고 달성·놓침을 판정하지 않는다.
(() => {
  'use strict';

  // ---- 기준값 (바꿀 때는 여기만 수정) ----
  const RULES = Object.freeze({
    REST_MAX_DRIVING_SECONDS: 2 * 3600, // 한국도로공사 권장: 2시간 운전하면
    REST_MIN_SECONDS: 15 * 60,          // 15분 이상 휴식
    EFFICIENCY_TARGET_RATIO: 1.05,      // 공인 연비(전비)의 105% 이상
    REUNION_TRIP_NUMBER: 3              // 같은 그룹과 세 번째 여행
  });

  const STATUS = Object.freeze({ ACHIEVED: 'ACHIEVED', IN_PROGRESS: 'IN_PROGRESS', MISSED: 'MISSED', UNKNOWN: 'UNKNOWN' });
  const CHALLENGES = Object.freeze([
    Object.freeze({ id: 'rest-before-2h', title: '쉬어가는 여행' }),
    Object.freeze({ id: 'smooth-driving', title: '부드러운 운전' }),
    Object.freeze({ id: 'perfect-merge', title: '완벽한 합류' }),
    Object.freeze({ id: 'unanimous', title: '만장일치' }),
    Object.freeze({ id: 'reunion', title: '또 만났네요' })
  ]);

  // 판정에는 AVAILABLE 값만 쓴다. STALE 은 마지막 값이 있어도 지금 상태로 보지 않는다.
  const usable = m => !!m && m.quality === 'AVAILABLE' && m.value !== null && m.value !== undefined;
  const clamp = value => Math.max(0, Math.min(1, value));
  const result = (status, reason, progress = null, values = {}) => Object.freeze({ status, reason, progress, values: Object.freeze(values) });

  /**
   * 쉬어가는 여행: 연속 운전 2시간이 되기 전에 15분 이상 휴식.
   * 입력: motion('MOVING'|'STOPPED'), drivingSeconds(마지막 휴식 이후 연속 운전), lastRestSeconds(가장 최근 정차 길이) — 모두 Measurement.
   * 주행 중에는 지난 휴식 앞의 운전 시간을 알 수 없으므로 정차 중일 때만 달성을 판정한다.
   */
  function evaluateRest({ motion, drivingSeconds, lastRestSeconds }) {
    if (!usable(motion) || !usable(drivingSeconds)) return result(STATUS.UNKNOWN, 'NO_VEHICLE_DATA');
    const driving = drivingSeconds.value;
    const over = driving > RULES.REST_MAX_DRIVING_SECONDS;
    if (motion.value === 'STOPPED') {
      if (!usable(lastRestSeconds)) return result(STATUS.UNKNOWN, 'NO_VEHICLE_DATA');
      const rest = lastRestSeconds.value;
      if (over) return result(STATUS.MISSED, 'OVER_LIMIT', null, { drivingSeconds: driving, restSeconds: rest });
      if (rest >= RULES.REST_MIN_SECONDS) return result(STATUS.ACHIEVED, 'RESTED', 1, { drivingSeconds: driving, restSeconds: rest });
      return result(STATUS.IN_PROGRESS, 'RESTING', clamp(rest / RULES.REST_MIN_SECONDS),
        { restSeconds: rest, remainingSeconds: RULES.REST_MIN_SECONDS - rest });
    }
    if (motion.value !== 'MOVING') return result(STATUS.UNKNOWN, 'NO_VEHICLE_DATA');
    if (over) return result(STATUS.MISSED, 'OVER_LIMIT', null, { drivingSeconds: driving });
    return result(STATUS.IN_PROGRESS, 'DRIVING', null,
      { drivingSeconds: driving, remainingSeconds: RULES.REST_MAX_DRIVING_SECONDS - driving });
  }

  /**
   * 부드러운 운전: 내 차량의 현재 연비(전기차는 전비)가 공인 값의 목표 비율 이상.
   * 입력: { current, rated, unit } — vehicle.getFuelEfficiency() 결과. 다른 차량 값은 받지 않는다.
   */
  function evaluateEfficiency({ current, rated, unit }) {
    if (!usable(current) || !usable(rated) || !(rated.value > 0) || !unit) return result(STATUS.UNKNOWN, 'NO_VEHICLE_DATA');
    const ratio = current.value / rated.value;
    const values = { ratio, current: current.value, rated: rated.value, unit, electric: unit === 'km/kWh' };
    if (ratio >= RULES.EFFICIENCY_TARGET_RATIO) return result(STATUS.ACHIEVED, 'ABOVE_TARGET', 1, values);
    return result(STATUS.IN_PROGRESS, 'BELOW_TARGET', clamp(ratio / RULES.EFFICIENCY_TARGET_RATIO), values);
  }

  /**
   * 완벽한 합류: 그룹 차량 전원이 같은 휴게소에 도착.
   * 입력: group(getGroup 결과 또는 null), vehicles(getGroupVehicles 결과).
   * 한 차량이라도 도착 여부를 알 수 없으면 UNKNOWN. 결과에는 차량별 정보 없이 대수만 담는다.
   */
  function evaluateMerge({ group, vehicles }) {
    if (!group || !Array.isArray(vehicles) || vehicles.length < 2) return result(STATUS.UNKNOWN, 'NO_GROUP');
    if (!vehicles.every(vehicle => usable(vehicle.arrival))) return result(STATUS.UNKNOWN, 'NO_ARRIVAL_DATA', null, { total: vehicles.length });
    const byPlace = new Map();
    for (const { arrival: { value } } of vehicles) {
      if (value.arrived && value.placeCategory === 'REST_AREA' && value.placeId) {
        const entry = byPlace.get(value.placeId) || { count: 0, placeName: value.placeName };
        entry.count++;
        byPlace.set(value.placeId, entry);
      }
    }
    const best = [...byPlace.values()].sort((a, b) => b.count - a.count)[0] || { count: 0, placeName: null };
    const total = vehicles.length;
    if (best.count === total) return result(STATUS.ACHIEVED, 'ALL_TOGETHER', 1, { together: total, total, placeName: best.placeName });
    return result(STATUS.IN_PROGRESS, best.count ? 'GATHERING' : 'NOT_YET', best.count / total, { together: best.count, total });
  }

  /**
   * 만장일치: 여행 중 모든 투표가 만장일치로 통과. 여행이 끝나야 확정한다.
   * 입력: decisions(trip-log 결정 목록), ended(여행 종료 여부). 룰렛·사다리는 투표가 아니므로 제외한다.
   */
  function evaluateUnanimous({ decisions, ended }) {
    if (!Array.isArray(decisions)) return result(STATUS.UNKNOWN, 'NO_VOTE_DATA');
    const votes = decisions.filter(decision => decision.kind === 'VOTE');
    const valid = vote => Number.isInteger(vote.votes?.agree) && Number.isInteger(vote.votes?.total)
      && vote.votes.total > 0 && vote.votes.agree >= 0 && vote.votes.agree <= vote.votes.total;
    if (!votes.every(valid)) return result(STATUS.UNKNOWN, 'NO_VOTE_DATA');
    const split = votes.filter(vote => vote.votes.agree < vote.votes.total).length;
    if (split) return result(STATUS.MISSED, 'SPLIT', null, { voteCount: votes.length, splitCount: split });
    if (!votes.length) return ended ? result(STATUS.MISSED, 'NO_VOTES') : result(STATUS.IN_PROGRESS, 'NO_VOTES');
    if (ended) return result(STATUS.ACHIEVED, 'ALL_UNANIMOUS', 1, { voteCount: votes.length });
    return result(STATUS.IN_PROGRESS, 'SO_FAR', null, { voteCount: votes.length });
  }

  /** 또 만났네요: 같은 그룹과 N번째 여행. 입력: group, tripNumber(Measurement, 이번 여행 포함). */
  function evaluateReunion({ group, tripNumber }) {
    if (!group) return result(STATUS.UNKNOWN, 'NO_GROUP');
    if (!usable(tripNumber) || !Number.isInteger(tripNumber.value) || tripNumber.value < 1) return result(STATUS.UNKNOWN, 'NO_HISTORY');
    const values = { tripNumber: tripNumber.value, target: RULES.REUNION_TRIP_NUMBER };
    if (tripNumber.value >= RULES.REUNION_TRIP_NUMBER) return result(STATUS.ACHIEVED, 'REACHED', 1, values);
    return result(STATUS.IN_PROGRESS, 'NOT_YET', tripNumber.value / RULES.REUNION_TRIP_NUMBER, values);
  }

  const EVALUATORS = Object.freeze({
    'rest-before-2h': ['rest', evaluateRest],
    'smooth-driving': ['efficiency', evaluateEfficiency],
    'perfect-merge': ['merge', evaluateMerge],
    unanimous: ['unanimous', evaluateUnanimous],
    reunion: ['reunion', evaluateReunion]
  });

  /**
   * 모든 도전과제를 판정한다. 이미 기록된 달성(recordedIds)은 지금 값과 관계없이 달성으로 유지한다.
   * @param snapshot { rest, efficiency, merge, unanimous, reunion } 각 판정 함수의 입력
   * @param recordedIds trip-log 에 이미 기록된 challengeId 목록
   * @returns [{ id, title, status, reason, progress, values, recorded }]
   */
  function evaluateAll(snapshot, recordedIds = []) {
    const recorded = new Set(recordedIds);
    return Object.freeze(CHALLENGES.map(({ id, title }) => {
      const [key, evaluate] = EVALUATORS[id];
      const evaluated = evaluate(snapshot?.[key] ?? {});
      const kept = recorded.has(id) && evaluated.status !== STATUS.ACHIEVED
        ? { status: STATUS.ACHIEVED, reason: 'RECORDED', progress: 1, values: evaluated.values } : evaluated;
      return Object.freeze({ id, title, ...kept, recorded: recorded.has(id) });
    }));
  }

  window.CoDriveChallengeRules = Object.freeze({
    RULES, STATUS, CHALLENGES,
    evaluateRest, evaluateEfficiency, evaluateMerge, evaluateUnanimous, evaluateReunion, evaluateAll
  });
})();
