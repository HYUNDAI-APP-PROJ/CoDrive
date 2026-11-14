package com.codrive.group.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.codrive.group.data.model.GroupMember;
import com.codrive.group.data.model.GroupSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Local installation only: no cross-device synchronization.
 * Lifecycle callbacks must not call leaveGroup or setConnected.
 */
public final class MockGroupRepository {
    private static MockGroupRepository instance;
    private final SharedPreferences preferences;
    private final Map<String, GroupSession> groups = new LinkedHashMap<>();
    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expiry = this::expire;
    private final SecureRandom random = new SecureRandom();
    private String activeGroupId, activeMemberId;
    private long revision;

    public static synchronized MockGroupRepository getInstance(Context context) {
        if (instance == null) instance = new MockGroupRepository(context.getApplicationContext());
        return instance;
    }

    private MockGroupRepository(Context context) {
        preferences = context.getSharedPreferences("codrive_groups_v1", Context.MODE_PRIVATE);
        restore();
        expire();
    }

    public LiveData<Long> changes() { return changes; }

    public synchronized String activeGroupId() {
        expire();
        GroupSession group = groups.get(activeGroupId);
        return group != null && !GroupSession.CLOSED.equals(group.status)
                && group.member(activeMemberId) != null ? activeGroupId : null;
    }

    public synchronized String activeMemberId() { return activeMemberId; }

    public synchronized GroupSession getGroup(String id) {
        expire();
        return groups.get(id);
    }

    public synchronized GroupSession createGroup(String name, String alias, int count, String rule) {
        validateVehicle(alias, count);
        if (name == null || name.trim().isEmpty() || name.trim().length() > 30)
            throw new IllegalArgumentException("그룹 이름을 1~30자로 입력해 주세요.");
        if (!"MAJORITY".equals(rule) && !"LEADER".equals(rule))
            throw new IllegalArgumentException("의사결정 방식을 선택해 주세요.");
        requireNoActiveGroup();
        String code;
        do {
            String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            code = builder.toString();
        } while (hasCode(code));
        activeGroupId = UUID.randomUUID().toString();
        activeMemberId = UUID.randomUUID().toString();
        List<GroupMember> members = new ArrayList<>();
        members.add(new GroupMember(activeMemberId, alias.trim(), count, true, false, true));
        GroupSession group = new GroupSession(activeGroupId, name.trim(), code, activeMemberId,
                rule, GroupSession.WAITING, members, 0);
        groups.put(group.groupId, group);
        save();
        return group;
    }

    public synchronized GroupSession joinGroup(String code, String alias, int count) {
        requireNoActiveGroup();
        validateVehicle(alias, count);
        expire();
        if (code == null) throw new IllegalArgumentException("초대 코드를 입력해 주세요.");
        for (GroupSession group : groups.values()) {
            if (group.inviteCode.equals(code.trim().toUpperCase(Locale.ROOT))
                    && GroupSession.WAITING.equals(group.status)) {
                if (group.members.size() >= 12) throw new IllegalArgumentException("참여 차량 수가 가득 찼어요.");
                activeMemberId = UUID.randomUUID().toString();
                activeGroupId = group.groupId;
                List<GroupMember> members = new ArrayList<>(group.members);
                members.add(new GroupMember(activeMemberId, alias.trim(), count, false, false, true));
                GroupSession updated = copy(group, group.status, members, group.leaderDisconnectDeadline);
                groups.put(group.groupId, updated);
                save();
                return updated;
            }
        }
        throw new IllegalArgumentException("참여 가능한 그룹이 없어요. 초대 코드를 확인해 주세요.");
    }

    public synchronized void updateReadyStatus(String groupId, String memberId, boolean ready) {
        GroupSession group = requireWaiting(groupId);
        GroupMember member = requireMember(group, memberId);
        if (!member.isConnected) throw new IllegalArgumentException("차량 연결 상태를 확인해 주세요.");
        replaceMember(group, new GroupMember(member.id, member.alias, member.passengerCount,
                member.isLeader, ready, true), group.leaderDisconnectDeadline);
    }

    public synchronized void startTrip(String groupId, String actorId) {
        GroupSession group = requireWaiting(groupId);
        requireLeader(group, actorId);
        if (!group.canStart()) throw new IllegalArgumentException("모든 차량의 연결과 준비 완료가 필요해요.");
        groups.put(groupId, copy(group, GroupSession.STARTED, group.members, 0));
        save();
    }

    public synchronized void leaveGroup(String groupId, String memberId) {
        GroupSession group = requireGroup(groupId);
        GroupMember member = requireMember(group, memberId);
        if (member.isLeader) {
            dissolveGroup(groupId, memberId);
            return;
        }
        List<GroupMember> members = new ArrayList<>(group.members);
        members.remove(member);
        groups.put(groupId, copy(group, group.status, members, group.leaderDisconnectDeadline));
        if (groupId.equals(activeGroupId) && memberId.equals(activeMemberId)) {
            activeGroupId = null;
            activeMemberId = null;
        }
        save();
    }

    public synchronized void dissolveGroup(String groupId, String actorId) {
        GroupSession group = requireGroup(groupId);
        requireLeader(group, actorId);
        groups.put(groupId, copy(group, GroupSession.CLOSED, group.members, 0));
        if (groupId.equals(activeGroupId)) {
            activeGroupId = null;
            activeMemberId = null;
        }
        save();
    }

    /** Transport/test seam; no demo controls are exposed in the UI. */
    public synchronized void setConnected(String groupId, String memberId, boolean connected) {
        GroupSession group = requireGroup(groupId);
        GroupMember member = requireMember(group, memberId);
        long deadline = group.leaderDisconnectDeadline;
        if (member.isLeader) {
            if (connected) deadline = 0;
            else if (deadline == 0) deadline = System.currentTimeMillis() + 30_000;
        }
        replaceMember(group, new GroupMember(member.id, member.alias, member.passengerCount,
                member.isLeader, member.isReady, connected), deadline);
    }

    /** Integration test seam for another vehicle, preserving the current UI identity. */
    public synchronized String addLocalVehicle(String groupId, String alias, int count) {
        GroupSession group = requireWaiting(groupId);
        validateVehicle(alias, count);
        if (group.members.size() >= 12) throw new IllegalArgumentException("참여 차량 수가 가득 찼어요.");
        String id = UUID.randomUUID().toString();
        List<GroupMember> members = new ArrayList<>(group.members);
        members.add(new GroupMember(id, alias.trim(), count, false, false, true));
        groups.put(groupId, copy(group, group.status, members, group.leaderDisconnectDeadline));
        save();
        return id;
    }

    /** Switches local client identity in tests without implicitly leaving a group. */
    public synchronized void clearLocalIdentityForTesting() {
        activeGroupId = null;
        activeMemberId = null;
        save();
    }

    private void replaceMember(GroupSession group, GroupMember replacement, long deadline) {
        List<GroupMember> members = new ArrayList<>();
        for (GroupMember member : group.members)
            members.add(member.id.equals(replacement.id) ? replacement : member);
        groups.put(group.groupId, copy(group, group.status, members, deadline));
        save();
    }

    private synchronized void expire() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (GroupSession group : new ArrayList<>(groups.values())) {
            if (!GroupSession.CLOSED.equals(group.status) && group.leaderDisconnectDeadline > 0
                    && now >= group.leaderDisconnectDeadline) {
                groups.put(group.groupId, copy(group, GroupSession.CLOSED, group.members, 0));
                changed = true;
            }
        }
        if (changed) save(); else schedule();
    }

    private void schedule() {
        handler.removeCallbacks(expiry);
        long earliest = Long.MAX_VALUE;
        for (GroupSession group : groups.values())
            if (!GroupSession.CLOSED.equals(group.status) && group.leaderDisconnectDeadline > 0)
                earliest = Math.min(earliest, group.leaderDisconnectDeadline);
        if (earliest != Long.MAX_VALUE)
            handler.postDelayed(expiry, Math.max(1, earliest - System.currentTimeMillis()));
    }

    private void validateVehicle(String alias, int count) {
        if (alias == null || alias.trim().isEmpty() || alias.trim().length() > 20)
            throw new IllegalArgumentException("차량 별칭을 1~20자로 입력해 주세요.");
        if (count < 1 || count > 9) throw new IllegalArgumentException("탑승 인원은 1~9명으로 입력해 주세요.");
    }

    private void requireNoActiveGroup() {
        if (activeGroupId() != null) throw new IllegalArgumentException("이미 참여 중인 그룹이 있어요.");
    }

    private boolean hasCode(String code) {
        for (GroupSession group : groups.values()) if (group.inviteCode.equals(code)) return true;
        return false;
    }

    private GroupSession requireGroup(String id) {
        GroupSession group = getGroup(id);
        if (group == null || GroupSession.CLOSED.equals(group.status))
            throw new IllegalArgumentException("그룹이 종료되었어요.");
        return group;
    }

    private GroupSession requireWaiting(String id) {
        GroupSession group = requireGroup(id);
        if (!GroupSession.WAITING.equals(group.status)) throw new IllegalArgumentException("이미 여행이 시작되었어요.");
        return group;
    }

    private GroupMember requireMember(GroupSession group, String id) {
        GroupMember member = group.member(id);
        if (member == null) throw new IllegalArgumentException("그룹에 참여한 차량이 아니에요.");
        return member;
    }

    private void requireLeader(GroupSession group, String id) {
        if (!requireMember(group, id).isLeader) throw new IllegalArgumentException("리더만 사용할 수 있어요.");
    }

    private GroupSession copy(GroupSession group, String status, List<GroupMember> members, long deadline) {
        return new GroupSession(group.groupId, group.groupName, group.inviteCode, group.leaderId,
                group.decisionRule, status, members, deadline);
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (GroupSession group : groups.values()) {
                JSONObject json = new JSONObject();
                json.put("id", group.groupId).put("name", group.groupName).put("code", group.inviteCode)
                    .put("leader", group.leaderId).put("rule", group.decisionRule)
                    .put("status", group.status).put("deadline", group.leaderDisconnectDeadline);
                JSONArray members = new JSONArray();
                for (GroupMember member : group.members)
                    members.put(new JSONObject().put("id", member.id).put("alias", member.alias)
                        .put("count", member.passengerCount).put("leader", member.isLeader)
                        .put("ready", member.isReady).put("connected", member.isConnected));
                json.put("members", members);
                array.put(json);
            }
            preferences.edit().putString("groups", array.toString())
                .putString("activeGroup", activeGroupId).putString("activeMember", activeMemberId).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("그룹 저장에 실패했어요.", e);
        }
        schedule();
        changes.postValue(++revision);
    }

    private void restore() {
        try {
            JSONArray array = new JSONArray(preferences.getString("groups", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.getJSONObject(i);
                List<GroupMember> members = new ArrayList<>();
                JSONArray list = json.getJSONArray("members");
                for (int j = 0; j < list.length(); j++) {
                    JSONObject member = list.getJSONObject(j);
                    members.add(new GroupMember(member.getString("id"), member.getString("alias"),
                            member.getInt("count"), member.getBoolean("leader"),
                            member.getBoolean("ready"), member.getBoolean("connected")));
                }
                GroupSession group = new GroupSession(json.getString("id"), json.getString("name"),
                        json.getString("code"), json.getString("leader"), json.getString("rule"),
                        json.getString("status"), members, json.optLong("deadline"));
                groups.put(group.groupId, group);
            }
            activeGroupId = preferences.getString("activeGroup", null);
            activeMemberId = preferences.getString("activeMember", null);
        } catch (JSONException e) {
            groups.clear();
            activeGroupId = null;
            activeMemberId = null;
        }
    }
}
