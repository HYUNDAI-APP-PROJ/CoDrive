package com.codrive.convoy.data.source;

/** Callbacks and repository access use the same serialized execution context. */
public interface TaskScheduler {
    void after(long delayMillis, Runnable task);
}
