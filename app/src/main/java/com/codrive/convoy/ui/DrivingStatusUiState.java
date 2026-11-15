package com.codrive.convoy.ui;

import com.codrive.convoy.data.model.*;
import com.codrive.convoy.domain.usecase.TripComparison;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DrivingStatusUiState {
    public static final class Notice {
        public final String id, vehicleId, text;
        public final int priority;
        public final long at;
        Notice(String id,String vehicleId,String text,int priority,long at) {
            this.id=id; this.vehicleId=vehicleId; this.text=text; this.priority=priority; this.at=at;
        }
    }
    public final TripStatus trip;
    public final TripComparison comparison;
    public final List<Notice> notices;
    public final long now;
    public final ZoneId zone;
    public final String destinationSummary, arrivalSummary, comparisonScope;

    public DrivingStatusUiState(TripStatus trip,long now,ZoneId zone) {
        this.trip=trip; this.now=now; this.zone=zone; comparison=new TripComparison(trip);
        int applied=0;
        List<Notice> list=new ArrayList<>();
        for(VehicleStatus v:trip.vehicles) {
            if(trip.sameDestination(v)) applied++;
            else if(v.applied==VehicleStatus.Applied.FAILED || v.applied==VehicleStatus.Applied.APPLIED)
                list.add(new Notice("destination-"+v.vehicleId,v.vehicleId,v.displayName+" · 목적지 확인 필요",1,0));
            for(VehicleStatus.Alert alert:v.alerts) if(alert.active) {
                boolean old=v.connection!=VehicleStatus.Connection.ONLINE || v.alertsQuality!=Measurement.Quality.AVAILABLE;
                list.add(new Notice(alert.id,v.vehicleId,v.displayName+" · "+alert.message+(old ? " · 마지막 수신 경고" : ""),
                        alert.kind==VehicleStatus.Alert.Kind.SAFETY ? 0 : alert.kind==VehicleStatus.Alert.Kind.ENERGY ? 2 : 4,alert.observedAt));
            }
            Measurement<Double> energy=primaryEnergy(v);
            if(trip.mode==TripStatus.Mode.MOCK && v.connection==VehicleStatus.Connection.ONLINE && energy.available() && energy.value<=20) {
                String msg=v.range.available() && TripComparison.eligible(trip,v) && v.distance.available() && v.range.value<v.distance.value
                        ? "충전 경유 검토 · 시연" : energyName(v)+" 잔량 "+Math.round(energy.value)+"%";
                list.add(new Notice("energy-"+v.vehicleId,v.vehicleId,v.displayName+" · "+msg,2,energy.observedAt));
            }
            if(v.request.shared!=null) list.add(new Notice("request-"+v.vehicleId,v.vehicleId,
                    v.displayName+" · "+DrivingText.request(v.request.shared)+" 요청 중",3,v.request.createdAt));
        }
        list.sort(Comparator.comparingInt((Notice n)->n.priority).thenComparingLong(n->n.at).thenComparing(n->n.id));
        notices=Collections.unmodifiableList(list);
        destinationSummary=applied==trip.vehicles.size() ? applied+"대 목적지 동일" : applied==0 ? "목적지 확인 중" : applied+"/"+trip.vehicles.size()+"대 목적지 반영";
        arrivalSummary=comparison.allArrived ? "모두 도착했어요" : comparison.arrivalGapMillis==null ? "도착 정보 확인 중"
                : comparison.arrivalGapMillis<60000 ? "도착 차이 1분 미만" : "도착 차이 "+Math.round(comparison.arrivalGapMillis/60000d)+"분";
        comparisonScope=comparison.arrivalTimes.size()<trip.vehicles.size() && comparison.arrivalTimes.size()>=2
                ? comparison.arrivalTimes.size()+"대 정보 기준" : "예상 도착 시간 기준";
    }
    public static Measurement<Double> primaryEnergy(VehicleStatus v) {
        return v.powertrain==VehicleStatus.Powertrain.ICE || v.powertrain==VehicleStatus.Powertrain.HEV ? v.fuel : v.battery;
    }
    public static String energyName(VehicleStatus v) {
        return v.powertrain==VehicleStatus.Powertrain.ICE || v.powertrain==VehicleStatus.Powertrain.HEV ? "연료" : "배터리";
    }
    public VehicleRequest.Type energyRequest() {
        switch(trip.mine().powertrain) {
            case EV:return VehicleRequest.Type.CHARGE;
            case ICE:return VehicleRequest.Type.REFUEL;
            default:return VehicleRequest.Type.ENERGY;
        }
    }
    public String eta(VehicleStatus v) {
        if(!trip.sameDestination(v)) return "목적지 확인 필요";
        if(v.navigation.available() && v.navigation.value==VehicleStatus.Navigation.ARRIVED) return "도착";
        if(v.navigation.available() && v.navigation.value==VehicleStatus.Navigation.NO_ROUTE) return "안내 종료";
        if(v.distance.available() && v.distance.value==0) return "도착 확인 중";
        return v.remainingSeconds.hasLastValue() ? DrivingText.remaining(v.remainingSeconds.value) : DrivingText.quality(v.remainingSeconds,now);
    }
    public String arrivalDetail(VehicleStatus v) {
        String distance=v.distance.hasLastValue() ? DrivingText.distance(v.distance.value) : "거리 "+DrivingText.quality(v.distance,now);
        if(!trip.sameDestination(v) || !v.navigation.available() || v.navigation.value==VehicleStatus.Navigation.NO_ROUTE) return distance;
        Measurement<Double> time=v.remainingSeconds;
        if(!time.hasLastValue()) return distance;
        Long eta=TripComparison.absoluteArrival(new Measurement<>(time.value,Measurement.Quality.AVAILABLE,time.observedAt,time.source));
        return distance+(eta==null ? "" : " · "+DrivingText.clock(eta,zone)+" 도착 예정");
    }
    public String relative(VehicleStatus v) {
        Long own=comparison.arrivalTimes.get(trip.currentVehicleId), other=comparison.arrivalTimes.get(v.vehicleId);
        return v.vehicleId.equals(trip.currentVehicleId) || own==null || other==null ? "" : DrivingText.relative(other-own);
    }
    public String freshness(VehicleStatus v) {
        String sensor=v.distance.quality==Measurement.Quality.STALE ? DrivingText.quality(v.distance,now)
                : v.remainingSeconds.quality==Measurement.Quality.STALE ? DrivingText.quality(v.remainingSeconds,now) : "";
        if(v.connection==VehicleStatus.Connection.OFFLINE) return "연결 끊김"+(sensor.isEmpty() ? " · 마지막 수신 "+DrivingText.age(now,v.lastMessageAt) : " · "+sensor);
        if(v.connection==VehicleStatus.Connection.RECONNECTING) return "재접속 중";
        if(!sensor.isEmpty()) return sensor;
        return "";
    }
    public String motion(VehicleStatus v) {
        if(v.connection!=VehicleStatus.Connection.ONLINE) return v.connection==VehicleStatus.Connection.OFFLINE ? "연결 끊김" : "재접속 중";
        if(!v.motion.available()) return "상태 "+DrivingText.quality(v.motion,now);
        switch(v.motion.value) { case MOVING:return "주행 중"; case STOPPED:return "정차 중"; default:return "상태 확인 중"; }
    }
    public String cardNotice(VehicleStatus v) {
        for(Notice n:notices) if(n.vehicleId.equals(v.vehicleId) && n.priority<=2) return n.text.substring(v.displayName.length()+3);
        String request=DrivingText.requestState(v.request);
        if(!request.isEmpty()) return request;
        for(Notice n:notices) if(n.vehicleId.equals(v.vehicleId)) return n.text.substring(v.displayName.length()+3);
        return v.alertsQuality==Measurement.Quality.AVAILABLE ? "" : "경고 정보 "+DrivingText.quality(Measurement.missing(v.alertsQuality),now);
    }
}
