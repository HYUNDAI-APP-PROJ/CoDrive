package com.codrive.convoy;

import com.codrive.convoy.data.model.*;
import com.codrive.convoy.data.repository.MockVehicleStatusRepository;
import com.codrive.convoy.data.source.*;
import com.codrive.convoy.domain.usecase.TripComparison;
import com.codrive.convoy.ui.DrivingStatusUiState;
import com.codrive.convoy.ui.DrivingText;
import org.junit.Test;
import java.io.*;
import java.time.ZoneId;
import java.util.*;
import static org.junit.Assert.*;
import static com.codrive.convoy.data.model.Measurement.Quality.*;
import static com.codrive.convoy.data.model.VehicleRequest.Type.*;

public class DrivingStatusTest {
    private static final long NOW=MockClock.START;
    private static final class Queue implements TaskScheduler {
        final List<Runnable> tasks=new ArrayList<>();
        public void after(long delay,Runnable task) { tasks.add(task); }
        void drain() { List<Runnable> copy=new ArrayList<>(tasks); tasks.clear(); copy.forEach(Runnable::run); }
    }
    private MockVehicleStatusRepository repository(Queue queue) { return new MockVehicleStatusRepository(new MockClock(NOW,ZoneId.of("Asia/Seoul")),queue,null); }
    private TripStatus fixture(DemoScenario scenario) {
        MockVehicleStatusRepository r=repository(new Queue()); r.select(scenario); return r.current();
    }
    private TripStatus replace(TripStatus trip,int index,VehicleStatus.Builder b) {
        List<VehicleStatus> next=new ArrayList<>(trip.vehicles); next.set(index,b.build()); return trip.withVehicles(next);
    }
    private DrivingStatusUiState ui(TripStatus trip) { return new DrivingStatusUiState(trip,NOW,ZoneId.of("Asia/Seoul")); }

    @Test public void demoStartsAt1430InTheSelectedTimezoneAndKeepsItOnReset() {
        for(String name:new String[]{"Asia/Seoul","America/Los_Angeles","UTC"}) {
            ZoneId zone=ZoneId.of(name);
            MockClock clock=new MockClock(MockClock.localStart(zone),zone);
            MockVehicleStatusRepository repo=new MockVehicleStatusRepository(clock,new Queue(),null);
            assertEquals("14:30",DrivingText.clock(repo.now(),zone));
            repo.advance(11000); repo.select(DemoScenario.NORMAL);
            DrivingStatusUiState state=new DrivingStatusUiState(repo.current(),repo.now(),zone);
            assertEquals("42km · 15:18 도착 예정",state.arrivalDetail(repo.current().mine()));
        }
    }

    @Test public void baselineUsesRemainingDistanceAndAbsoluteArrivals() {
        TripStatus trip=fixture(DemoScenario.NORMAL); TripComparison c=new TripComparison(trip);
        assertEquals(0.3,c.positions.get("self"),0.00001);
        assertEquals(1-25d/60,c.positions.get("a"),0.00001); assertEquals(0,c.positions.get("b"),0);
        assertEquals(Long.valueOf(26*60000),c.arrivalGapMillis);
        assertEquals("42km · 15:18 도착 예정",ui(trip).arrivalDetail(trip.mine()));
        assertEquals("나보다 12분 먼저 도착 예정",ui(trip).relative(trip.vehicles.get(1)));
        assertEquals("나보다 14분 후 도착 예정",ui(trip).relative(trip.vehicles.get(2)));
        assertFalse(trip.mine().range.available()); assertFalse(trip.mine().charging.available());
    }
    @Test public void arrivalDifferenceUsesObservationTimeInsteadOfSubtractingMinutes() {
        TripStatus trip=fixture(DemoScenario.NORMAL); VehicleStatus.Builder a=trip.vehicles.get(1).copy();
        a.remainingSeconds=Measurement.mock(2880d,NOW-120000);
        TripStatus shifted=replace(trip,1,a);
        assertEquals("나보다 2분 먼저 도착 예정",ui(shifted).relative(shifted.vehicles.get(1)));
        assertEquals("비슷한 시간에 도착 예정",DrivingText.relative(59000));
        assertEquals("나보다 1분 후 도착 예정",DrivingText.relative(60000));
    }
    @Test public void formattingHandlesSubMinuteAndSubKilometerValues() {
        assertEquals("곧 도착",DrivingText.remaining(59)); assertEquals("1시간 2분 남음",DrivingText.remaining(3720));
        assertEquals("900m",DrivingText.distance(900)); assertEquals("1.3km",DrivingText.distance(1250));
    }
    @Test public void sameNameDoesNotMakeDifferentDestinationVersionsComparable() {
        TripStatus trip=fixture(DemoScenario.DESTINATION_MISMATCH);
        assertFalse(new TripComparison(trip).positions.containsKey("b"));
        assertFalse(new TripComparison(trip).arrivalTimes.containsKey("b"));
        assertEquals("2/3대 목적지 반영",ui(trip).destinationSummary);
        VehicleStatus.Builder b=trip.vehicles.get(2).copy(); b.destinationVersion=1; b.destinationId="different-id";
        assertFalse(new TripComparison(replace(trip,2,b)).positions.containsKey("b"));
    }
    @Test public void distanceAndTimeAreIndependentlyEligible() {
        TripStatus trip=fixture(DemoScenario.NO_SELF_ETA); TripComparison c=new TripComparison(trip);
        assertEquals(3,c.positions.size()); assertEquals(2,c.arrivalTimes.size());
        assertEquals("",ui(trip).relative(trip.vehicles.get(1))); assertEquals("2대 정보 기준",ui(trip).comparisonScope);
        VehicleStatus.Builder a=trip.vehicles.get(1).copy(); a.distance=Measurement.missing(NOT_SHARED);
        TripComparison result=new TripComparison(replace(trip,1,a));
        assertFalse(result.positions.containsKey("a")); assertTrue(result.arrivalTimes.containsKey("a"));
    }
    @Test public void invalidNumbersAndPrivateValuesAreNeverAvailable() {
        for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            TripStatus trip=fixture(DemoScenario.NORMAL); VehicleStatus.Builder b=trip.mine().copy(); b.distance=Measurement.mock(bad,NOW);
            assertFalse(new TripComparison(replace(trip,0,b)).positions.containsKey("self"));
        }
        Measurement<Double> privateValue=Measurement.number(90d,NOT_SHARED,NOW,Measurement.Source.VEHICLE_SDK,100);
        assertNull(privateValue.value); assertEquals(NOT_SHARED,privateValue.quality);
        assertNull(Measurement.number(500d,NOT_SHARED,NOW,Measurement.Source.VEHICLE_SDK,100).value);
        assertFalse(Measurement.number(101d,AVAILABLE,NOW,Measurement.Source.MOCK,100).available());
        assertFalse(Measurement.mock(10d,0).available());
        assertNull(TripComparison.absoluteArrival(Measurement.mock(Double.MAX_VALUE,NOW)));
    }
    @Test public void zeroIsNotAnArrivalEvent() {
        TripStatus trip=fixture(DemoScenario.ZERO_DISTANCE); TripComparison c=new TripComparison(trip);
        for(double p:c.positions.values()) assertEquals(1,p,0);
        assertFalse(c.allArrived); assertEquals("도착 확인 중",ui(trip).eta(trip.mine()));
        assertEquals("도착",ui(trip).eta(trip.vehicles.get(1)));
        assertTrue(new TripComparison(fixture(DemoScenario.ALL_ARRIVED)).allArrived);
    }
    @Test public void staleThresholdIsStrictAndHeartbeatDoesNotFreshenSensors() {
        MockVehicleStatusRepository r=repository(new Queue()); r.advance(10000);
        assertEquals(AVAILABLE,r.current().mine().distance.quality);
        r.advance(1); assertEquals(STALE,r.current().mine().distance.quality);
        r.heartbeat("self"); assertEquals(STALE,r.current().mine().distance.quality);
        assertEquals(NOW,r.current().mine().distance.observedAt);
        assertEquals(VehicleStatus.Connection.ONLINE,r.current().mine().connection);
    }
    @Test public void offlineThresholdIsStrictAndRecoveryPreservesWarningsAndTrip() {
        MockVehicleStatusRepository r=repository(new Queue()); r.advance(30000);
        assertEquals(VehicleStatus.Connection.ONLINE,r.current().mine().connection);
        r.advance(1); assertEquals(VehicleStatus.Connection.OFFLINE,r.current().mine().connection);
        r.select(DemoScenario.OFFLINE); String id=r.current().tripId;
        assertEquals(1,r.current().vehicles.get(2).alerts.size()); r.recover();
        assertEquals(VehicleStatus.Connection.ONLINE,r.current().vehicles.get(2).connection);
        assertEquals(1,r.current().vehicles.get(2).alerts.size()); assertEquals(id,r.current().tripId);
    }
    @Test public void staleAndOfflinePeersAreExcludedButLastCardInformationRemains() {
        for(DemoScenario scenario:new DemoScenario[]{DemoScenario.STALE,DemoScenario.OFFLINE}) {
            TripStatus trip=fixture(scenario); TripComparison c=new TripComparison(trip);
            assertEquals(2,c.positions.size()); assertEquals(2,c.arrivalTimes.size());
            assertTrue(trip.vehicles.get(2).distance.hasLastValue());
            assertTrue(ui(trip).freshness(trip.vehicles.get(2)).contains("마지막 정보"));
        }
    }
    @Test public void noRouteReconnectAndNonAvailableQualitiesExcludeComparisons() {
        assertEquals(2,new TripComparison(fixture(DemoScenario.NO_ROUTE)).positions.size());
        assertEquals(2,new TripComparison(fixture(DemoScenario.RECONNECTING)).positions.size());
        for(Measurement.Quality quality:Measurement.Quality.values()) if(quality!=AVAILABLE) {
            TripStatus trip=fixture(DemoScenario.NORMAL); VehicleStatus.Builder b=trip.mine().copy();
            b.distance=new Measurement<>(42000d,quality,NOW,Measurement.Source.MOCK);
            assertFalse(new TripComparison(replace(trip,0,b)).positions.containsKey("self"));
        }
    }
    @Test public void missingDataAndSingleVehicleDoNotInventGroupComparisons() {
        TripComparison empty=new TripComparison(fixture(DemoScenario.NO_DATA)); assertTrue(empty.positions.isEmpty()); assertNull(empty.arrivalGapMillis);
        TripComparison solo=new TripComparison(fixture(DemoScenario.SOLO)); assertEquals(1,solo.positions.size()); assertNull(solo.arrivalGapMillis);
        assertEquals(2,fixture(DemoScenario.TWO_VEHICLES).vehicles.size());
    }
    @Test public void orderAndVehicleIdentityStayStableWhenDistanceChanges() {
        TripStatus trip=fixture(DemoScenario.CLOSE_MARKERS); TripComparison c=new TripComparison(trip);
        assertEquals(Arrays.asList("self","a","b"),new ArrayList<>(c.positions.keySet()));
        assertEquals(1-59800d/60000,c.positions.get("a"),0.0000001);
        assertEquals(1-59500d/60000,c.positions.get("b"),0.0000001);
        List<VehicleStatus> reversed=new ArrayList<>(trip.vehicles); Collections.reverse(reversed);
        assertEquals("a",trip.withVehicles(reversed).vehicles.get(1).vehicleId);
    }
    @Test public void requestDoubleTapDoesNotDuplicateAndLateResponseCannotReplaceNewIntent() {
        Queue queue=new Queue(); MockVehicleStatusRepository r=repository(queue);
        r.toggleRequest(REST); long first=r.current().mine().request.id; r.toggleRequest(REST);
        assertEquals(1,queue.tasks.size()); r.toggleRequest(CHARGE); long second=r.current().mine().request.id;
        assertTrue(second>first); r.completeRequest(first,true);
        assertEquals(VehicleRequest.Status.PENDING,r.current().mine().request.status);
        queue.drain(); assertEquals(CHARGE,r.current().mine().request.shared);
        assertEquals(second,r.current().mine().request.id);
    }
    @Test public void requestCanFailRetryThenCancel() {
        Queue q=new Queue(); MockVehicleStatusRepository r=repository(q); r.select(DemoScenario.REQUEST_FAILURE);
        r.toggleRequest(REST); q.drain(); assertEquals(VehicleRequest.Status.FAILED,r.current().mine().request.status);
        assertNull(r.current().mine().request.shared);
        r.toggleRequest(REST); q.drain(); assertEquals(REST,r.current().mine().request.shared);
        r.toggleRequest(REST); assertEquals(VehicleRequest.Status.CANCELLING,r.current().mine().request.status);
        assertEquals(REST,r.current().mine().request.shared); q.drain(); assertNull(r.current().mine().request.shared);
    }
    @Test public void failedCancellationRetainsSharedRequestUntilSuccessfulRetry() {
        Queue q=new Queue(); MockVehicleStatusRepository r=repository(q); r.toggleRequest(REST); q.drain();
        r.toggleRequest(REST); r.completeRequest(r.current().mine().request.id,false); q.drain();
        assertEquals(REST,r.current().mine().request.shared); assertEquals(VehicleRequest.Status.FAILED,r.current().mine().request.status);
        r.toggleRequest(REST); assertEquals(VehicleRequest.Status.CANCELLING,r.current().mine().request.status); q.drain();
        assertEquals(VehicleRequest.Status.IDLE,r.current().mine().request.status); assertNull(r.current().mine().request.shared);
    }
    @Test public void localRequestsStayInTheCardWithoutAddingAGlobalNotice() {
        for(VehicleRequest.Type type:new VehicleRequest.Type[]{REST,CHARGE}) {
            Queue queue=new Queue(); MockVehicleStatusRepository r=repository(queue);
            r.toggleRequest(type);
            assertTrue(ui(r.current()).notices.isEmpty());
            queue.drain();
            assertTrue(ui(r.current()).notices.isEmpty());
            assertEquals(type,r.current().mine().request.shared);
            assertTrue(ui(r.current()).cardNotice(r.current().mine()).contains(DrivingText.request(type)));
            r.toggleRequest(type);
            assertTrue(ui(r.current()).notices.isEmpty());
            queue.drain();
            assertTrue(ui(r.current()).notices.isEmpty());
            assertNull(r.current().mine().request.shared);
        }
    }
    @Test public void peerRequestsCannotBeCancelledByLocalButtons() {
        Queue q=new Queue(); MockVehicleStatusRepository r=repository(q); r.select(DemoScenario.PEER_REQUEST);
        r.toggleRequest(REST); q.drain(); r.toggleRequest(REST); q.drain();
        assertEquals(REST,r.current().vehicles.get(1).request.shared);
        assertTrue(ui(r.current()).notices.stream().anyMatch(n->n.id.equals("request-a")));
    }
    @Test public void scenarioReplacementInvalidatesPendingRequestCallbacks() {
        Queue q=new Queue(); MockVehicleStatusRepository r=repository(q); r.toggleRequest(REST);
        r.select(DemoScenario.LOW_ENERGY); q.drain(); assertNull(r.current().mine().request.shared);
    }
    @Test public void savedPendingRequestResumesOnceAndKeepsTripIdentity() throws Exception {
        Queue q=new Queue(); MockVehicleStatusRepository original=repository(q); original.toggleRequest(REST);
        String tripId=original.current().tripId; long requestId=original.current().mine().request.id;
        MockVehicleStatusRepository.Snapshot saved=roundTrip(original.save()); original.close(); q.drain();
        Queue next=new Queue(); MockVehicleStatusRepository restored=new MockVehicleStatusRepository(new MockClock(NOW),next,saved);
        assertEquals(1,next.tasks.size()); assertEquals(requestId,restored.current().mine().request.id);
        next.drain(); assertEquals(REST,restored.current().mine().request.shared); assertEquals(tripId,restored.current().tripId);
    }
    @Test public void unsubscribedObserversStopReceivingUpdates() {
        MockVehicleStatusRepository r=repository(new Queue()); int[] calls={0};
        com.codrive.convoy.data.repository.VehicleStatusRepository.Subscription sub=r.observe(t->calls[0]++);
        sub.close(); r.advance(1); assertEquals(1,calls[0]);
    }
    @Test public void alertsHaveStablePriorityAndDisconnectedWarningsAreIdentified() {
        DrivingStatusUiState state=ui(fixture(DemoScenario.WARNINGS)); assertEquals("tire-a",state.notices.get(0).id);
        assertTrue(ui(fixture(DemoScenario.OFFLINE)).notices.get(0).text.contains("마지막 수신 경고"));
        assertTrue(ui(fixture(DemoScenario.NO_DATA)).cardNotice(fixture(DemoScenario.NO_DATA).mine()).contains("정보 없음"));
    }
    @Test public void optionalRangeAndEnergyTypesStaySeparate() {
        TripStatus trip=fixture(DemoScenario.PHEV); assertEquals(68d,trip.mine().battery.value,0); assertEquals(45d,trip.mine().fuel.value,0);
        assertEquals(ENERGY,ui(trip).energyRequest());
        assertTrue(ui(fixture(DemoScenario.OPTIONAL_RANGE)).notices.get(0).text.contains("충전 경유 검토"));
        TripStatus privateTrip=fixture(DemoScenario.PRIVATE_UNSUPPORTED);
        assertEquals(UNSUPPORTED,privateTrip.vehicles.get(1).fuel.quality); assertNull(privateTrip.vehicles.get(2).battery.value);
    }
    @Test public void allScenariosProduceBoundedCoordinatesAndArrivalSummaries() {
        for(DemoScenario scenario:DemoScenario.values()) {
            TripStatus trip=fixture(scenario); DrivingStatusUiState state=ui(trip);
            for(double p:state.comparison.positions.values()) assertTrue(Double.isFinite(p)&&p>=0&&p<=1);
            assertNotNull(state.arrivalSummary);
        }
    }
    @Test public void restrictionsPreventRequestMutation() {
        Queue q=new Queue(); MockVehicleStatusRepository r=repository(q); r.select(DemoScenario.RESTRICTED);
        r.toggleRequest(REST); assertTrue(q.tasks.isEmpty()); assertEquals(VehicleRequest.Status.IDLE,r.current().mine().request.status);
    }
    @SuppressWarnings("unchecked") private <T> T roundTrip(T input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ObjectOutputStream objects=new ObjectOutputStream(out)) { objects.writeObject(input); }
        try(ObjectInputStream objects=new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) { return (T)objects.readObject(); }
    }
}
