package com.codrive.convoy.ui;

import com.codrive.convoy.data.model.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Korean presentation rules kept together; calculations do not depend on localized strings. */
public final class DrivingText {
    private DrivingText() {}
    public static String distance(double meters) {
        if(meters<1000) return Math.round(meters)+"m";
        double km=meters/1000;
        return String.format(Locale.KOREA,km==Math.rint(km) ? "%.0fkm" : "%.1fkm",km);
    }
    public static String duration(double seconds) {
        long minutes=Math.round(seconds/60);
        return minutes>=60 ? minutes/60+"시간"+(minutes%60==0 ? "" : " "+minutes%60+"분") : minutes+"분";
    }
    public static String remaining(double seconds) { return seconds<60 ? "곧 도착" : duration(seconds)+" 남음"; }
    public static String clock(long millis,ZoneId zone) { return DateTimeFormatter.ofPattern("HH:mm",Locale.KOREA).format(Instant.ofEpochMilli(millis).atZone(zone)); }
    public static String age(long now,long observed) {
        long seconds=Math.max(0,(now-observed)/1000);
        return seconds<60 ? seconds+"초 전" : seconds<3600 ? seconds/60+"분 전" : seconds/3600+"시간 전";
    }
    public static String quality(Measurement<?> m,long now) {
        switch(m.quality) {
            case AVAILABLE: return "";
            case LOADING: return "확인 중";
            case NOT_SHARED: return "공유 안 함";
            case UNSUPPORTED: return "지원 안 됨";
            case PERMISSION_DENIED: return "접근 권한 없음";
            case STALE: return "마지막 정보 · "+age(now,m.observedAt);
            case ERROR: return "정보 확인 실패";
            default: return "정보 없음";
        }
    }
    public static String relative(long deltaMillis) {
        if(Math.abs(deltaMillis)<60000) return "비슷한 시간에 도착 예정";
        return "나보다 "+Math.round(Math.abs(deltaMillis)/60000d)+"분 "+(deltaMillis<0 ? "먼저" : "후")+" 도착 예정";
    }
    public static String request(VehicleRequest.Type type) {
        if(type==null) return "";
        switch(type) { case REST: return "휴식"; case REFUEL:return "주유"; case CHARGE:return "충전"; default:return "주유·충전"; }
    }
    public static String requestState(VehicleRequest r) {
        switch(r.status) {
            case PENDING: return request(r.desired)+" 요청 전달 중";
            case CANCELLING: return "요청 취소 전달 중";
            case FAILED: return "전달 실패 · 다시 시도"+(r.shared==null ? "" : " · "+request(r.shared)+" 요청 유지");
            case SHARED: return request(r.shared)+" 요청 중";
            default: return "";
        }
    }
}
