package com.codrive.voice.data.repository;

import com.codrive.voice.data.model.VoiceSession;

public interface VoiceSessionController {
    VoiceSession current();
    void join();
    void leave();
    void toggleMuted();
}
