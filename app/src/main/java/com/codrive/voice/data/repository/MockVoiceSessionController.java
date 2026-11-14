package com.codrive.voice.data.repository;

import com.codrive.voice.data.model.VoiceSession;
import java.io.Serializable;

/** State simulation only: no microphone permission, recording or network operations. */
public final class MockVoiceSessionController implements VoiceSessionController {
    /** The caller supplies delayed execution without coupling voice to another feature. */
    @FunctionalInterface
    public interface Scheduler {
        void after(long delayMillis, Runnable task);
    }
    public static final class Snapshot implements Serializable {
        final VoiceSession session;
        final boolean failNext, pendingFailure;
        Snapshot(VoiceSession session,boolean failNext,boolean pendingFailure) {
            this.session=session; this.failNext=failNext; this.pendingFailure=pendingFailure;
        }
    }
    private final Scheduler scheduler;
    private Runnable onChange=() -> {};
    private VoiceSession session=VoiceSession.idle();
    private long generation;
    private boolean failNext, pendingFailure;
    public MockVoiceSessionController(Scheduler scheduler,Snapshot saved) {
        this.scheduler=scheduler;
        if(saved!=null) {
            session=saved.session; failNext=saved.failNext; pendingFailure=saved.pendingFailure;
            if(session.state==VoiceSession.State.CONNECTING) completeLater(++generation,pendingFailure);
        }
    }
    public Snapshot save() { return new Snapshot(session,failNext,pendingFailure); }
    public void observe(Runnable callback) { onChange=callback; }
    @Override public VoiceSession current() { return session; }
    @Override public void join() {
        if(session.state==VoiceSession.State.CONNECTING || session.state==VoiceSession.State.JOINED) return;
        pendingFailure=failNext; failNext=false;
        session=new VoiceSession(VoiceSession.State.CONNECTING,true); onChange.run();
        completeLater(++generation,pendingFailure);
    }
    private void completeLater(long token,boolean failure) {
        scheduler.after(800,() -> {
            if(token!=generation) return;
            session=new VoiceSession(failure ? VoiceSession.State.FAILED : VoiceSession.State.JOINED,true); onChange.run();
        });
    }
    @Override public void leave() { generation++; session=VoiceSession.idle(); onChange.run(); }
    @Override public void toggleMuted() {
        if(session.state!=VoiceSession.State.JOINED) return;
        session=new VoiceSession(VoiceSession.State.JOINED,!session.muted); onChange.run();
    }
    public void reset(boolean failFirstJoin) { leave(); failNext=failFirstJoin; }
    public void close() { generation++; onChange=() -> {}; }
}
