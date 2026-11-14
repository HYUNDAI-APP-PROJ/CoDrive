// 여행 기록 카드에 들어갈 기록의 진입점. 카드 화면은 이 모듈에서만 기록을 읽는다.
// 현재는 시연용 초기 기록(방문 장소·그룹 결정)에 이 탭에서 추가한 기록을 합쳐 sessionStorage 에 보관한다.
// 나중에 history 도메인 저장소로 교체하며, 다른 도메인(group 투표 등)의 결정도 그쪽에서 합쳐진다.
//
// 기록하는 것: 방문 장소(시간순), 함께 내린 결정, 달성한 도전과제, 여행 시작·종료 시각.
// 기록하지 않는 것: 차량별 속도·도착 순서·주행 거리 비교.
(() => {
  'use strict';
  const STORAGE_KEY = 'codrive.history.trip-log.v1';
  const MIN = 60 * 1000;
  const now = () => window.CoDriveMock ? window.CoDriveMock.now() : Date.now();
  const DECISION_KIND = Object.freeze({ ROULETTE: 'ROULETTE', LADDER: 'LADDER', VOTE: 'VOTE' });

  // 시연용 초기 기록. 처음 불러온 시각을 기준으로 상대 시간을 계산해 한 번만 만든다.
  function seed(at) {
    const startedAt = at - 3 * 60 * MIN;
    return {
      tripId: 'mock-trip-gangneung', groupName: '강릉 주말 여행', startedAt, endedAt: null,
      visits: [
        { placeId: 'mock-place-lake-walk', placeName: '대청호 산책로', arrivedAt: startedAt + 25 * MIN, leftAt: startedAt + 70 * MIN },
        { placeId: 'mock-place-lake-restaurant', placeName: '호숫가 식당', arrivedAt: startedAt + 80 * MIN, leftAt: startedAt + 130 * MIN }
      ],
      decisions: [
        { id: 'mock-decision-lunch', kind: DECISION_KIND.VOTE, title: '점심 장소', result: '호숫가 식당', decidedAt: startedAt + 60 * MIN }
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

  /** 룰렛·사다리 등으로 함께 내린 결정을 기록한다. 결과가 확정된 뒤에만 호출한다. */
  function recordDecision({ kind, title, result }) {
    if (!Object.hasOwn(DECISION_KIND, kind)) throw new RangeError('Unknown decision kind: ' + kind);
    const at = now();
    const entry = { id: 'decision-' + at + '-' + log.decisions.length, kind, title: text(title, 'title'), result: text(result, 'result'), decidedAt: at };
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
    DECISION_KIND, getTripRecord, recordDecision, recordAchievement, endTrip, subscribe,
    // 시연·검사용: 초기 기록으로 되돌린다.
    resetForDemo() { log = seed(now()); save(); emit(); }
  });
})();
