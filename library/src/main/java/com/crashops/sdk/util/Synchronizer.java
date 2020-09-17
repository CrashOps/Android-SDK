package com.crashops.sdk.util;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;

/**
 * A helper class that syncs all callbacks into one callback.
 *
 * Created by CrashOps on 01/01/2020.
 */
public class Synchronizer<T> {
    public interface SynchronizerCallback<T> {
        void done(ArrayList<T> extra);
    }

    private boolean hasBeenCanceled;
    private int holdersCount = 0;
    private final SynchronizerCallback<T> futureTask;
    private ArrayList<T> allHoldersResults;
    private final Handler handler;

    public Synchronizer(SynchronizerCallback<T> lastAction) {
        handler = new Handler(Looper.getMainLooper());
        futureTask = lastAction;
        allHoldersResults = new ArrayList<>();
        hasBeenCanceled = false;
    }

    public Holder createHolder() {
        holdersCount++;

        return new Holder();
    }


    public void done() {
        if (futureTask != null) futureTask.done(null);
    }

    public boolean isHasBeenCanceled() {
        return hasBeenCanceled;
    }

    public boolean isWaiting() {
        return holdersCount > 0;
    }

    public void cancel() {
        hasBeenCanceled = true;
    }

    public ArrayList<T> getAllHoldersResults() {
        return allHoldersResults;
    }

    public boolean didAllDone() {
        return holdersCount == 0;
    }

    public class Holder {
        private boolean isReleased;


        private Holder() {
            isReleased = false;
        }

        public void release() {
            release(null);
        }

        public void release(T extra) {
            release(extra, false);
        }

        private void release(final T extra, boolean afterDelay) {
            if (hasBeenCanceled) return;

            if (isReleased) return;

            if (holdersCount == 1 && !afterDelay) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        release(extra, true);
                    }
                }, 100);
                return;
            }

            isReleased = true;

            allHoldersResults.add(extra);
            holdersCount--;

            if (holdersCount == 0) {
                futureTask.done(allHoldersResults);
            }
        }
    }
}