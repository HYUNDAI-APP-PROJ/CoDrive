package com.codrive.group.ui.waiting;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import com.codrive.group.data.model.GroupMember;
import com.codrive.group.data.model.GroupSession;
import com.codrive.group.data.repository.MockGroupRepository;

public final class GroupWaitingViewModel extends ViewModel {
    private final MockGroupRepository repository;
    private final String groupId, memberId;
    private final MutableLiveData<GroupWaitingUiState> state = new MutableLiveData<>();
    private final Observer<Long> observer = ignored -> refresh(null);
    public GroupWaitingViewModel(MockGroupRepository repository, String groupId) {
        this.repository = repository; this.groupId = groupId;
        memberId = repository.activeMemberId();
        repository.changes().observeForever(observer);
    }
    public LiveData<GroupWaitingUiState> state() { return state; }
    private void refresh(String message) {
        state.setValue(new GroupWaitingUiState(repository.getGroup(groupId), memberId, message));
    }
    public void refresh() { refresh(null); }
    public void toggleReady() {
        run(() -> {
            GroupSession group = repository.getGroup(groupId);
            GroupMember me = group == null ? null : group.member(memberId);
            if (me != null) repository.updateReadyStatus(groupId, memberId, !me.isReady);
        });
    }
    public void start() { run(() -> repository.startTrip(groupId, memberId)); }
    public void leave() { run(() -> repository.leaveGroup(groupId, memberId)); }
    private void run(Runnable action) {
        try { action.run(); refresh(null); }
        catch (IllegalArgumentException e) { refresh(e.getMessage()); }
    }
    @Override protected void onCleared() { repository.changes().removeObserver(observer); }
}
