package com.codrive.voice.data.model;

import java.io.Serializable;

public final class VoiceSession implements Serializable {
    public enum State { NOT_JOINED, CONNECTING, JOINED, FAILED }
    public final State state;
    public final boolean muted;
    public VoiceSession(State state, boolean muted) {
        this.state = state;
        this.muted = state != State.JOINED || muted;
    }
    public static VoiceSession idle() { return new VoiceSession(State.NOT_JOINED, true); }
}
