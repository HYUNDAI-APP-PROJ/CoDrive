package com.codrive.convoy.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.codrive.convoy.data.model.VehicleRequest;
import com.codrive.convoy.data.repository.*;
import com.codrive.convoy.data.source.*;
import com.codrive.voice.data.model.VoiceSession;
import com.codrive.voice.data.repository.MockVoiceSessionController;
import java.io.Serializable;
import java.time.ZoneId;

public final class DrivingStatusViewModel extends ViewModel {
    public static final class SavedSession implements Serializable {
        final MockVehicleStatusRepository.Snapshot trip;
        final MockVoiceSessionController.Snapshot voice;
        final ZoneId zone;
        SavedSession(MockVehicleStatusRepository.Snapshot trip,MockVoiceSessionController.Snapshot voice,ZoneId zone) {
            this.trip=trip; this.voice=voice; this.zone=zone;
        }
    }
    private final MockVehicleStatusRepository repository;
    private final MockVoiceSessionController voice;
    private final MutableLiveData<DrivingStatusUiState> state=new MutableLiveData<>();
    private final VehicleStatusRepository.Subscription subscription;
    private final ZoneId zone;
    public DrivingStatusViewModel(TaskScheduler scheduler,SavedSession saved) {
        zone=saved==null ? ZoneId.systemDefault() : saved.zone;
        repository=new MockVehicleStatusRepository(new MockClock(MockClock.localStart(zone),zone),scheduler,saved==null ? null : saved.trip);
        voice=new MockVoiceSessionController(scheduler::after,saved==null ? null : saved.voice);
        voice.observe(this::publish); subscription=repository.observe(trip -> publish());
    }
    private void publish() { state.setValue(new DrivingStatusUiState(repository.current(),voice.current(),repository.now(),zone)); }
    public LiveData<DrivingStatusUiState> state() { return state; }
    public SavedSession save() { return new SavedSession(repository.save(),voice.save(),zone); }
    public void request(VehicleRequest.Type type) { repository.toggleRequest(type); }
    public void voiceAction() {
        if(!repository.current().actionsAllowed) return;
        if(voice.current().state==VoiceSession.State.JOINED || voice.current().state==VoiceSession.State.CONNECTING) voice.leave(); else voice.join();
    }
    public void microphone() { if(repository.current().actionsAllowed) voice.toggleMuted(); }
    public void scenario(DemoScenario scenario) { repository.select(scenario); voice.reset(scenario==DemoScenario.VOICE_FAILURE); }
    public DemoScenario scenario() { return repository.scenario(); }
    public void recover() { repository.recover(); }
    public void advance(long millis) { repository.advance(millis); }
    @Override protected void onCleared() { subscription.close(); repository.close(); voice.close(); }
}
