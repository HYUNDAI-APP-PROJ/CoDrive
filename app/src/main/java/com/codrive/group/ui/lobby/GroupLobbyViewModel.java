package com.codrive.group.ui.lobby;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.codrive.group.data.repository.MockGroupRepository;
import java.util.Locale;

public final class GroupLobbyViewModel extends ViewModel {
    private final SavedStateHandle saved;
    private final MockGroupRepository repository;
    private final MutableLiveData<GroupLobbyUiState> state = new MutableLiveData<>();
    public GroupLobbyViewModel(SavedStateHandle saved, MockGroupRepository repository) {
        this.saved = saved; this.repository = repository;
        publish(null, null, null, null, null, null);
    }
    public LiveData<GroupLobbyUiState> state() { return state; }
    private String value(String key, String fallback) {
        String result = saved.get(key); return result == null ? fallback : result;
    }
    public boolean joining() { return Boolean.TRUE.equals(saved.get("joining")); }
    private String prefix() { return joining() ? "join" : "create"; }
    public void input(String name, String code, String alias, String count, String rule) {
        saved.set("name", name); saved.set("code", code);
        saved.set(prefix() + "Alias", alias); saved.set(prefix() + "Count", count);
        saved.set("rule", rule);
    }
    public void tab(boolean joining) {
        saved.set("joining", joining); publish(null, null, null, null, null, null);
    }
    public void resumeGroup() { publish(null, null, null, null, null, repository.activeGroupId()); }
    public void submit() {
        String name = value("name", "").trim();
        String code = value("code", "").trim().toUpperCase(Locale.ROOT);
        String alias = value(prefix() + "Alias", "").trim();
        int count;
        try { count = Integer.parseInt(value(prefix() + "Count", "1")); }
        catch (NumberFormatException e) { count = 0; }
        String ne = !joining() && (name.isEmpty() || name.length() > 30) ? "그룹 이름을 1~30자로 입력해 주세요." : null;
        String ce = joining() && !code.matches("[A-Z0-9]{6}") ? "6자리 영문·숫자 코드를 입력해 주세요." : null;
        String ae = alias.isEmpty() || alias.length() > 20 ? "차량 별칭을 1~20자로 입력해 주세요." : null;
        String pe = count < 1 || count > 9 ? "1~9명을 입력해 주세요." : null;
        if (ne != null || ce != null || ae != null || pe != null) {
            publish(ne, ce, ae, pe, null, null); return;
        }
        try {
            String id = joining() ? repository.joinGroup(code, alias, count).groupId
                    : repository.createGroup(name, alias, count, value("rule", "MAJORITY")).groupId;
            publish(null, null, null, null, null, id);
        } catch (IllegalArgumentException e) {
            publish(null, joining() ? e.getMessage() : null, null, null,
                    joining() ? null : e.getMessage(), null);
        }
    }
    private void publish(String ne, String ce, String ae, String pe, String message, String id) {
        state.setValue(new GroupLobbyUiState(joining(), value("name", ""), value("code", ""),
                value(prefix() + "Alias", ""), value(prefix() + "Count", "1"),
                value("rule", "MAJORITY"), ne, ce, ae, pe, message, id));
    }
}
