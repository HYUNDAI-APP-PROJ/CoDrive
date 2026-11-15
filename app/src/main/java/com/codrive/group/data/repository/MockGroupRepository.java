package com.codrive.group.data.repository;

import com.codrive.group.data.model.TravelGroup;
import java.util.UUID;

/** 네트워크를 사용하지 않는 프론트 미리보기용 그룹 데이터입니다. */
public final class MockGroupRepository {
    public TravelGroup defaultGroup() {
        return new TravelGroup("mock-gangneung", "강릉 주말 여행", 3);
    }

    public TravelGroup create(String name) {
        String clean = name.trim();
        if (clean.isEmpty() || clean.length() > 30) {
            throw new IllegalArgumentException("그룹 이름을 1~30자로 입력해주세요.");
        }
        return new TravelGroup(UUID.randomUUID().toString(), clean, 3);
    }
}
