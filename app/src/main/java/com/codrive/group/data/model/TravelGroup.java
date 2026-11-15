package com.codrive.group.data.model;

import java.io.Serializable;

/** 목업 여행 그룹. 실제 참여 차량 정보는 서버 연동 시 확장합니다. */
public final class TravelGroup implements Serializable {
    public final String id;
    public final String name;
    public final int vehicleCount;

    public TravelGroup(String id, String name, int vehicleCount) {
        this.id = id;
        this.name = name;
        this.vehicleCount = vehicleCount;
    }
}
