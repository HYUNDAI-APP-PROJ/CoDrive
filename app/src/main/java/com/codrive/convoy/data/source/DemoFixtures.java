package com.codrive.convoy.data.source;

import com.codrive.convoy.data.model.*;
import com.codrive.voice.data.model.VoiceSession;
import java.util.ArrayList;
import java.util.List;
import static com.codrive.convoy.data.model.Measurement.Quality.*;
import static com.codrive.convoy.data.model.VehicleStatus.*;

public final class DemoFixtures {
    private DemoFixtures() {}
    public static TripStatus create(DemoScenario scenario, long now) {
        Builder me=vehicle("self", "내 차", 1, Powertrain.EV, 42000, 2880, 68, 6000, now);
        Builder a=vehicle("a", "차량 A", 2, Powertrain.ICE, 25000, 2160, 45, 5100, now);
        Builder b=vehicle("b", "차량 B", 3, Powertrain.EV, 60000, 3720, 34, 6900, now);
        switch (scenario) {
            case LOW_ENERGY: b.battery=Measurement.mock(18d, now); break;
            case OPTIONAL_RANGE: b.battery=Measurement.mock(18d, now); b.range=Measurement.mock(48000d, now); break;
            case STALE:
                b.distance=Measurement.mock(60000d, now-11000); b.remainingSeconds=Measurement.mock(3720d, now-11000); break;
            case OFFLINE:
                b.lastMessageAt=now-31000; b.distance=Measurement.mock(60000d, now-31000);
                b.remainingSeconds=Measurement.mock(3720d, now-31000);
                b.alerts.add(new Alert("tire-b", Alert.Kind.SAFETY, 2, "타이어 저압", true, now-32000)); break;
            case PRIVATE_UNSUPPORTED: a.fuel=Measurement.missing(UNSUPPORTED); b.battery=Measurement.missing(NOT_SHARED); break;
            case DESTINATION_MISMATCH: b.destinationVersion=2; break;
            case CLOSE_MARKERS:
                me.distance=Measurement.mock(60000d, now); a.distance=Measurement.mock(59800d, now);
                b.distance=Measurement.mock(59500d, now); break;
            case ONE_ARRIVED: arrive(a, now); break;
            case ALL_ARRIVED: arrive(me, now); arrive(a, now); arrive(b, now); break;
            case ZERO_DISTANCE:
                for (Builder v : new Builder[]{me,a,b}) { v.distance=Measurement.mock(0d,now); v.remainingSeconds=Measurement.mock(0d,now); }
                arrive(a,now); break;
            case NO_SELF_ETA: me.remainingSeconds=Measurement.missing(NO_DATA); break;
            case NO_DATA: case LOADING: case PERMISSION_DENIED: case ERROR:
                Measurement.Quality q=scenario==DemoScenario.LOADING ? LOADING : scenario==DemoScenario.PERMISSION_DENIED ? PERMISSION_DENIED
                        : scenario==DemoScenario.ERROR ? ERROR : NO_DATA;
                for (Builder v : new Builder[]{me,a,b}) {
                    v.distance=Measurement.missing(q); v.remainingSeconds=Measurement.missing(q);
                    v.battery=Measurement.missing(q); v.fuel=Measurement.missing(q);
                    v.motion=Measurement.missing(q); v.alertsQuality=q;
                }
                break;
            case WARNINGS:
                a.alerts.add(new Alert("tire-a",Alert.Kind.SAFETY,2,"타이어 저압",true,now-2000));
                b.alerts.add(new Alert("general-b",Alert.Kind.GENERAL,1,"차량 경고 확인",true,now-1000)); break;
            case RECONNECTING: b.connection=Connection.RECONNECTING; break;
            case PEER_REQUEST: a.request=new VehicleRequest(1,VehicleRequest.Type.REST,VehicleRequest.Type.REST,VehicleRequest.Status.SHARED,now); break;
            case PHEV: me.powertrain=Powertrain.PHEV; me.fuel=Measurement.mock(45d,now); break;
            case NO_ROUTE: b.navigation=Measurement.mock(Navigation.NO_ROUTE,now); break;
            default: break;
        }
        List<VehicleStatus> vehicles=new ArrayList<>(); vehicles.add(me.build());
        if (scenario != DemoScenario.SOLO) vehicles.add(a.build());
        if (scenario != DemoScenario.SOLO && scenario != DemoScenario.TWO_VEHICLES) vehicles.add(b.build());
        return new TripStatus("demo-trip-1",TripStatus.Mode.MOCK,"destination-1",1,"공동 목적지","self",vehicles,
                scenario != DemoScenario.RESTRICTED,scenario != DemoScenario.RESTRICTED);
    }
    private static Builder vehicle(String id,String name,int order,Powertrain type,double distance,double seconds,double energy,double driving,long now) {
        Builder v=new Builder(id,name,order); v.powertrain=type; v.lastMessageAt=now;
        v.distance=Measurement.mock(distance,now); v.remainingSeconds=Measurement.mock(seconds,now);
        if (type==Powertrain.EV) v.battery=Measurement.mock(energy,now); else v.fuel=Measurement.mock(energy,now);
        v.drivingSeconds=Measurement.mock(driving,now); v.motion=Measurement.mock(Motion.MOVING,now);
        v.navigation=Measurement.mock(Navigation.GUIDING,now); v.alertsQuality=AVAILABLE;
        if (order != 1) v.voice=new VoiceSession(VoiceSession.State.JOINED,false);
        return v;
    }
    private static void arrive(Builder v,long now) {
        v.distance=Measurement.mock(0d,now); v.remainingSeconds=Measurement.mock(0d,now);
        v.motion=Measurement.mock(Motion.STOPPED,now); v.navigation=Measurement.mock(Navigation.ARRIVED,now);
    }
}
