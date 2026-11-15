// 여행 기록 카드에 들어갈 기록의 진입점. 카드 화면은 이 모듈에서만 기록을 읽는다.
// 현재는 시연용 초기 기록(방문 장소·그룹 결정)에 이 탭에서 추가한 기록을 합쳐 sessionStorage 에 보관한다.
// 나중에 history 도메인 저장소로 교체하며, 다른 도메인(group 투표 등)의 결정도 그쪽에서 합쳐진다.
//
// 기록하는 것: 방문 장소(시간순), 함께 내린 결정, 달성한 도전과제, 여행 시작·종료 시각, 같은 그룹과의 여행 횟수.
// 투표(VOTE) 결정은 찬성 수 votes { agree, total } 를 함께 남긴다. 만장일치 도전과제 판정에 사용한다.
// 기록하지 않는 것: 차량별 속도·도착 순서·주행 거리 비교.
(() => {
  'use strict';
  const STORAGE_KEY = 'codrive.history.trip-log.v1';
  const MIN = 60 * 1000;
  const now = () => window.CoDriveMock ? window.CoDriveMock.now() : Date.now();
  const DECISION_KIND = Object.freeze({ ROULETTE: 'ROULETTE', LADDER: 'LADDER', VOTE: 'VOTE' });

  // 시연용: 주소의 ?trip=N 으로 같은 그룹과의 여행 횟수를 바꾼다(1~10). 없으면 3.
  const DEFAULT_TRIP_NUMBER = 3;
  const requestedTrip = Number(new URLSearchParams(window.location?.search || '').get('trip'));
  const demoTripNumber = Number.isInteger(requestedTrip) && requestedTrip >= 1 && requestedTrip <= 10 ? requestedTrip : null;

  // 시연용 초기 기록. 처음 불러온 시각을 기준으로 상대 시간을 계산해 한 번만 만든다.
  function seed(at) {
    const startedAt = at - 3 * 60 * MIN;
    return {
      tripId: 'mock-trip-gangneung', groupName: '강릉 주말 여행', startedAt, endedAt: null,
      tripNumberWithGroup: demoTripNumber ?? DEFAULT_TRIP_NUMBER, // 같은 그룹과의 몇 번째 여행인지 (history 저장소 연결 시 실제 기록으로 계산)
      visits: [
        { placeId: 'mock-place-lake-walk', placeName: '대청호 산책로', arrivedAt: startedAt + 25 * MIN, leftAt: startedAt + 70 * MIN },
        { placeId: 'mock-place-lake-restaurant', placeName: '호숫가 식당', arrivedAt: startedAt + 80 * MIN, leftAt: startedAt + 130 * MIN }
      ],
      decisions: [
        { id: 'mock-decision-lunch', kind: DECISION_KIND.VOTE, title: '점심 장소', result: '호숫가 식당', votes: { agree: 3, total: 3 }, decidedAt: startedAt + 60 * MIN }
      ],
      achievements: []
    };
  }

  function load() {
    try {
      const saved = JSON.parse(window.sessionStorage.getItem(STORAGE_KEY) || 'null');
      if (saved && Array.isArray(saved.visits) && Array.isArray(saved.decisions) && Array.isArray(saved.achievements)) return saved;
    } catch { /* 저장 기록이 손상되면 시연 기록으로 다시 시작한다. */ }
    return seed(now());
  }
  let log = load();
  if (demoTripNumber !== null) log.tripNumberWithGroup = demoTripNumber; // 주소의 값이 저장 기록보다 우선한다.
  function save() {
    try { window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(log)); }
    catch { /* 저장 없이도 이번 화면에서는 기록이 유지된다. */ }
  }
  save();

  const listeners = new Set();
  function emit() { for (const listener of [...listeners]) { try { listener(); } catch (error) { console.error(error); } } }
  const byTime = key => (a, b) => a[key] - b[key];
  const text = (value, field) => {
    if (typeof value !== 'string' || !value.trim()) throw new TypeError(field + ' 값이 필요합니다.');
    return value.trim().slice(0, 60);
  };

  /**
   * 카드에 표시할 여행 기록 전체. 목록은 모두 시간순이다.
   * togetherSeconds: 여행 시작부터 종료(진행 중이면 현재)까지 함께한 시간.
   */
  async function getTripRecord() {
    const end = log.endedAt ?? now();
    return Object.freeze({
      tripId: log.tripId, groupName: log.groupName, startedAt: log.startedAt, endedAt: log.endedAt,
      togetherSeconds: Math.max(0, Math.floor((end - log.startedAt) / 1000)),
      visits: Object.freeze([...log.visits].sort(byTime('arrivedAt')).map(Object.freeze)),
      decisions: Object.freeze([...log.decisions].sort(byTime('decidedAt')).map(Object.freeze)),
      achievements: Object.freeze([...log.achievements].sort(byTime('achievedAt')).map(Object.freeze))
    });
  }

  /**
   * 룰렛·사다리·투표로 함께 내린 결정을 기록한다. 결과가 확정된 뒤에만 호출한다.
   * 투표(VOTE)는 votes { agree, total } 를 함께 넘긴다. 찬성 수를 모르면 생략하며, 이때 만장일치 여부는 확인할 수 없음으로 판정된다.
   */
  function recordDecision({ kind, title, result, votes }) {
    if (!Object.hasOwn(DECISION_KIND, kind)) throw new RangeError('Unknown decision kind: ' + kind);
    if (votes !== undefined && (kind !== DECISION_KIND.VOTE || !Number.isInteger(votes?.agree) || !Number.isInteger(votes?.total)
      || votes.total < 1 || votes.agree < 0 || votes.agree > votes.total)) throw new RangeError('Invalid votes');
    const at = now();
    const entry = { id: 'decision-' + at + '-' + log.decisions.length, kind, title: text(title, 'title'), result: text(result, 'result'), decidedAt: at };
    if (votes) entry.votes = { agree: votes.agree, total: votes.total };
    log.decisions.push(entry);
    save(); emit();
    return Object.freeze({ ...entry });
  }

  /** 달성한 도전과제를 기록한다. 같은 도전과제는 한 번만 기록하며, 이미 있으면 false. */
  function recordAchievement({ challengeId, title }) {
    const id = text(challengeId, 'challengeId');
    if (log.achievements.some(item => item.challengeId === id)) return false;
    log.achievements.push({ challengeId: id, title: text(title, 'title'), achievedAt: now() });
    save(); emit();
    return true;
  }

  /**
   * 이번 여행이 같은 그룹과의 몇 번째 여행인지 (이번 여행 포함). Measurement 형식.
   * 기록을 불러오지 못했거나 값이 없으면 NO_DATA.
   */
  async function getTripNumberWithGroup() {
    const M = window.CoDriveMeasurement;
    const value = log.tripNumberWithGroup;
    if (!M) throw new Error('trip-log.js 보다 measurement.js 를 먼저 불러와야 합니다.');
    return Number.isInteger(value) && value >= 1 ? M.mock(value, now()) : M.missing(M.QUALITY.NO_DATA);
  }

  /** 여행을 종료한다. 이후 함께한 시간이 더 늘지 않는다. 이미 종료했으면 false. */
  function endTrip() {
    if (log.endedAt != null) return false;
    log.endedAt = now();
    save(); emit();
    return true;
  }

  /** 기록이 바뀔 때 알린다. @returns {() => void} 구독 해제 함수 */
  function subscribe(callback) { listeners.add(callback); return () => listeners.delete(callback); }

  window.CoDriveTripLog = Object.freeze({
    DECISION_KIND, getTripRecord, getTripNumberWithGroup, recordDecision, recordAchievement, endTrip, subscribe,
    // 시연·검사용: 초기 기록으로 되돌린다.
    resetForDemo() { log = seed(now()); save(); emit(); }
  });
})();
