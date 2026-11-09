package com.lime.odyssey.lib.api.sched;

/** Handle to a scheduled task. Cancelling is idempotent and safe from any thread. */
public interface TaskHandle {
    void cancel();

    boolean isCancelled();
}
