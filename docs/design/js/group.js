// 그룹 차량 목록과 차량별 도착 여부의 진입점. 현재는 mock-scenario.js 의 시연 데이터를 반환한다.
// 그룹 서버·group 도메인 저장소가 생기면 readRaw()·subscribe() 만 교체한다.
// 차량 간 순위·도착 순서 비교에 쓰지 않는다. "전원 같은 장소 합류"처럼 그룹 공동 상태 판단에만 사용한다.
//
// 그룹 없음 시연: 페이지 주소에 ?group=none
(() => {
  'use strict';
  const M = window.CoDriveMeasurement;
  const mock = window.CoDriveMock;
  if (!M || !mock) throw new Error('group.js 보다 measurement.js 와 mock-scenario.js 를 먼저 불러와야 합니다.');

  // ---- 데이터 출처 (그룹 서버 연결 시 이 부분만 교체) ----
  function readRaw() { return mock.read(); }
  function subscribeRaw(listener) { return mock.onChange(listener); }
  const SOURCE = M.SOURCE.MOCK;
  // -----------------------------------------------------

  function toVehicle(vehicle, myVehicleId, at) {
    return Object.freeze({
      vehicleId: vehicle.vehicleId,
      displayName: vehicle.displayName,
      memberName: vehicle.memberName,
      displayOrder: vehicle.displayOrder,
      isMine: vehicle.vehicleId === myVehicleId,
      connection: vehicle.connection, // VehicleStatus.Connection: ONLINE · RECONNECTING · OFFLINE
      // value: { arrived, placeId, placeName, placeCategory, arrivedAt }. 연결이 끊긴 차량은 도착 여부를 알 수 없으므로 NO_DATA.
      // placeCategory: 'REST_AREA'(휴게소) 등 장소 종류. 실제 데이터에서 종류를 알 수 없으면 null.
      arrival: vehicle.connection === 'ONLINE'
        ? M.create({ arrived: vehicle.arrived, placeId: vehicle.placeId, placeName: vehicle.placeName,
          placeCategory: vehicle.placeCategory, arrivedAt: vehicle.arrivedAt },
          M.QUALITY.AVAILABLE, at, SOURCE)
        : M.missing(M.QUALITY.NO_DATA, SOURCE)
    });
  }

  /** 현재 그룹 정보. 그룹이 없으면 null. @returns {Promise<{groupId, name, vehicleCount}|null>} */
  async function getGroup() {
    const group = readRaw().group;
    return group ? Object.freeze({ groupId: group.groupId, name: group.name, vehicleCount: group.vehicles.length }) : null;
  }

  /** 그룹 차량 목록과 각 차량의 도착 여부. 내 차량을 맨 앞에 두고 나머지는 displayOrder 순서. 그룹이 없으면 []. */
  async function getGroupVehicles() {
    const raw = readRaw();
    if (!raw.group) return Object.freeze([]);
    const mine = raw.group.myVehicleId;
    const sorted = [...raw.group.vehicles].sort((a, b) =>
      (a.vehicleId === mine ? -1 : a.displayOrder) - (b.vehicleId === mine ? -1 : b.displayOrder));
    return Object.freeze(sorted.map(vehicle => toVehicle(vehicle, mine, raw.observedAt)));
  }

  /** 그룹 상태가 바뀌거나 시간이 흐를 때 알린다. @returns {() => void} 구독 해제 함수 */
  function subscribe(callback) { return subscribeRaw(callback); }

  window.CoDriveGroup = Object.freeze({ getGroup, getGroupVehicles, subscribe });
})();
