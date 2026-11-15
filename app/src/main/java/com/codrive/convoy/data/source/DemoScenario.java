package com.codrive.convoy.data.source;

public enum DemoScenario {
    NORMAL("01 · 기본 주행"), LOW_ENERGY("02 · 배터리 18%"), OPTIONAL_RANGE("03 · 주행가능거리 시연"),
    REST_REQUEST("04 · 휴식 요청·취소"), REQUEST_FAILURE("05 · 요청 실패·재시도"),
    STALE("06 · 거리·도착 정보 11초 지연"), OFFLINE("07 · 메시지 31초 끊김"),
    PRIVATE_UNSUPPORTED("08 · 미지원·공유 안 함"), DESTINATION_MISMATCH("09 · 목적지 버전 불일치"),
    CLOSE_MARKERS("10 · 가까운 차량 위치"), ONE_ARRIVED("11a · 한 대 도착"), ALL_ARRIVED("11b · 모두 도착"),
    ZERO_DISTANCE("12 · 거리 0·도착 미확인"), SOLO("13a · 내 차 한 대"), TWO_VEHICLES("13b · 두 대"),
    NO_SELF_ETA("14a · 내 차 도착 정보 없음"), NO_DATA("14b · 모든 거리 정보 없음"),
    WARNINGS("15 · 차량 경고"),
    LOADING("확장 · 정보 확인 중"), PERMISSION_DENIED("확장 · 접근 권한 없음"),
    ERROR("확장 · 조회 실패"), RECONNECTING("확장 · 재접속 중"),
    PEER_REQUEST("확장 · 일행 휴식 요청"), PHEV("확장 · PHEV 배터리·연료"),
    NO_ROUTE("확장 · 안내 종료"), RESTRICTED("확장 · 화면 조작 제한");

    public final String label;
    DemoScenario(String label) { this.label=label; }
    public static DemoScenario parse(String name) {
        try { return valueOf(name); } catch (IllegalArgumentException | NullPointerException e) { return NORMAL; }
    }
}
