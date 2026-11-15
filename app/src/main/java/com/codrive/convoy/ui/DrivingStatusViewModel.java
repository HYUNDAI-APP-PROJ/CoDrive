package com.codrive.convoy.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.codrive.convoy.data.model.VehicleRequest;
import com.codrive.convoy.data.repository.*;
import com.codrive.convoy.data.source.*;
import java.io.Serializable;
import java.time.ZoneId;

public final class DrivingStatusViewModel extends ViewModel {
    public static final class SavedSession implements Serializable {
        final MockVehicleStatusRepository.Snapshot trip;
        final ZoneId zone;
        SavedSession(MockVehicleStatusRepository.Snapshot trip,ZoneId zone) {
            this.trip=trip; this.zone=zone;
        }
    }
    private final MockVehicleStatusRepository repository;
    private final MutableLiveData<DrivingStatusUiState> state=new MutableLiveData<>();
    private final VehicleStatusRepository.Subscription subscription;
    private final ZoneId zone;
    public DrivingStatusViewModel(TaskScheduler scheduler,SavedSession saved) {
        zone=saved==null ? ZoneId.systemDefault() : saved.zone;
        repository=new MockVehicleStatusRepository(new MockClock(MockClock.localStart(zone),zone),scheduler,saved==null ? null : saved.trip);
        subscription=repository.observe(trip -> publish());
    }
    private void publish() { state.setValue(new DrivingStatusUiState(repository.current(),repository.now(),zone)); }
    public LiveData<DrivingStatusUiState> state() { return state; }
    public SavedSession save() { return new SavedSession(repository.save(),zone); }
    public void request(VehicleRequest.Type type) { repository.toggleRequest(type); }
    public void scenario(DemoScenario scenario) { repository.select(scenario); }
    public DemoScenario scenario() { return repository.scenario(); }
    public void recover() { repository.recover(); }
    public void advance(long millis) { repository.advance(millis); }
    @Override protected void onCleared() { subscription.close(); repository.close(); }
}
