package com.codrive.group.ui.lobby;

public final class GroupLobbyUiState {
    public final boolean joining;
    public final String name, code, alias, passengers, rule;
    public final String nameError, codeError, aliasError, passengersError, message, groupId;
    public GroupLobbyUiState(boolean joining, String name, String code, String alias,
            String passengers, String rule, String nameError, String codeError,
            String aliasError, String passengersError, String message, String groupId) {
        this.joining = joining; this.name = name; this.code = code; this.alias = alias;
        this.passengers = passengers; this.rule = rule; this.nameError = nameError;
        this.codeError = codeError; this.aliasError = aliasError;
        this.passengersError = passengersError; this.message = message; this.groupId = groupId;
    }
}
