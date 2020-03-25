package com.crashops.sdk;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.crashops.sdk.configuration.Configurations;
import com.crashops.sdk.configuration.ConfigurationsProvider;
import com.crashops.sdk.data.Repository;
import com.crashops.sdk.service.LogsHistoryWorker;
import com.crashops.sdk.service.exceptionshandler.CrashOpsErrorHandler;
import com.crashops.sdk.util.Constants;
import com.crashops.sdk.util.DeviceInfoFetcher;
import com.crashops.sdk.util.Optionals;
import com.crashops.sdk.util.SdkLogger;
import com.crashops.sdk.util.Strings;
import com.crashops.sdk.util.Utils;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Created by perrchick on 18/10/2018.
 */
public class CrashOps {
    private static final String TAG = CrashOps.class.getSimpleName();
    private Bundle hostAppMetadata;

    @Nullable
    private PreviousLogsListener previousLogsListener;

    /**
     * A type 4 (pseudo randomly generated) UUID. Generated only once per session.
     * <br/><br/>
     * A session starts on app launch and continues until the app is killed.
     */
    @NonNull
    public final String sessionId;

    private static boolean isApplicationInForeground() {
        return COHostApplication.sharedInstance().isInForeground();
    }

    @NonNull
    public Bundle appMetadata() {
        return Optionals.safelyUnwrap(hostAppMetadata, new Bundle());
    }

    public void setMetadata(Bundle metadata) {
        setMetadata(metadata, true);
    }

    public void setMetadata(Bundle metadata, boolean merge) {
        if (merge && metadata != null) {
            this.hostAppMetadata.putAll(metadata);
        } else {
            // override
            this.hostAppMetadata = Optionals.safelyUnwrap(metadata, new Bundle());
        }
    }

    public static class ExtraKeys {
        public static final String THROWABLE = Strings.SDK_NAME + " EXTRA_KEY_THROWABLE";
    }

    public static class Action {
        public static final String CRASH_OCCURRED = Strings.SDK_IDENTIFIER + " - ACTION_CRASH_OCCURRED";
        public static final String CRASHOPS_INTENT = Strings.SDK_IDENTIFIER + " - CRASHOPS_INTENT";
    }

    // init façade stuff
    private CrashOps() {
        hostAppMetadata = new Bundle();
        sessionId = UUID.randomUUID().toString();
    }

    public static CrashOps getInstance() {
        return CrashOpsController.sdkInstance.facade;
    }

    /**
     * Crash the app for testing purposes
     */
    public void crash() {
        if (!CrashOpsController.sdkInstance.isCrashOpsEnabled) return;

        throw new RuntimeException(Strings.TestedExceptionName);
    }

    /**
     * Logs a non-fatal error.
     */
    public void logError(String title) {
        logError(title, new Bundle());
    }

    /**
     * Logs a non-fatal error with extra details if needed.
     */
    public void logError(String title, Bundle errorDetails) {
        if (title == null) return;
        Bundle details = Optionals.safelyUnwrap(errorDetails, new Bundle());

        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        StackTraceElement[] errorStackTrace = Arrays.copyOfRange(stackTraceElements, 3, stackTraceElements.length);

        CrashOpsController.sdkInstance.logError(title, details, errorStackTrace);
    }

    // TODO Allow toggling between enable / disable
    public boolean isEnabled() {
        return CrashOpsController.sdkInstance.isCrashOpsEnabled && Configurations.isEnabled();
    }

    public void enable() {
        CrashOpsController.sdkInstance.toggle(true);
    }

    public void disable() {
        CrashOpsController.sdkInstance.toggle(false);
    }

    private boolean eraseHistory() {
        boolean didDelete = Repository.getInstance().clearAllHistory();
        if (!didDelete) {
            SdkLogger.error(TAG, "Failed to delete history");
        }

        return didDelete;
    }

    public void cleanup() {
        CrashOpsErrorHandler.getInstance().revert();
        COHostApplication.sharedInstance().cleanup();
    }

    /**
     * Sets the client ID.
     *
     * @param clientId The client ID received by CrashOps customer services.
     */
    public boolean setClientId(String clientId) {
        if (clientId == null) return false;
        if (clientId.isEmpty()) return false;
        if (clientId.length() > 100) return false;

        boolean didSet = false;
        String previousClientId = Repository.getInstance().loadCustomValue(Constants.Keys.ClientId, null);
        if (previousClientId == null) {
            didSet = Repository.getInstance().storeCustomValue(Constants.Keys.ClientId, clientId, true);
        } else {
            if (previousClientId.equalsIgnoreCase(clientId)) {
                didSet = true;
            }

            clientId = previousClientId;
        }

        Utils.debugToast("CrashOps client ID is: " + clientId);

        return didSet;
    }

    public void removeOnCrashListener() {
        setOnCrashListener(null);
    }

    public void removePreviousLogsListener() {
        setPreviousLogsListener(null);
    }

    /**
     * Sets the responsible listener to be notified when a crash occurs.
     * <br/><br/>
     * Holds a strong reference to the listener.
     * Meaning that if the listener is an activity, for example, it might create a
     * memory leak in case the activity didn't make a proper cleanup and removed this reference.
     *<br/><br/>
     * Recommendations to avoid memory leaks:
     * <ol>
     *     <li>Use the Application instance as a listener.</li>
     *     <li>Call the `removeOnCrashListener` method when `Activity#onDestroy` is called, in case the listener is an Activity instance.</li>
     * </ol>
     *
     *
     * @param onCrashListener The listener that will be notified upon crash is detected.
     */
    public void setOnCrashListener(OnCrashListener onCrashListener) {
        // TOD(O) ? Use LiveData to solve it upfront (will it solve memory leak potential for services?)
        CrashOpsErrorHandler.getInstance().setOnCrashListener(onCrashListener);
    }

    /**
     * Sets the responsible listener to be notified when a crash occurs.
     * <br/><br/>
     * Holds a strong reference to the listener.
     * Meaning that if the listener is an activity, for example, it might create a
     * memory leak in case the activity didn't make a proper cleanup and removed this reference.
     *<br/><br/>
     * Recommendations to avoid memory leaks:
     * <ol>
     *     <li>Use the Application instance as a listener.</li>
     *     <li>Call the `removeOnCrashListener` method when `Activity#onDestroy` is called, in case the listener is an Activity instance.</li>
     * <ol/>
     *
     * @param previousLogsListener The listener that will be notified upon previous crash logs were detected.
     */
    public void setPreviousLogsListener(@Nullable PreviousLogsListener previousLogsListener) {
        this.previousLogsListener = previousLogsListener;
    }

    public void onPreviousCrashLogsUpdated(@NonNull final List<String> previousCrashLogs) {
        if (Configurations.isEnabled() && previousLogsListener != null) {
            CrashOpsController.sdkInstance.mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    previousLogsListener.onPreviousLogsDetected(previousCrashLogs);
                }
            });
        }
    }

    private enum CrashOpsController { // I have found that using enum as singleton helps with memory management, the enum has unique characteristics when trying to create a stable singleton.
        sdkInstance;

        private boolean isCrashOpsEnabled = true;
        private static final String CO_THREAD_TAG = "CrashOps_Thread";
        private final Handler mainHandler;
        /**
         * The façade bridge
         */
        private final CrashOps facade = new CrashOps();
        HandlerThread bgThread;
        private Handler bgThreadHandler;
        private WeakReference<Context> contextHolder; // This could never be null, how am I so sure? Google has found a way: https://youtu.be/AJqakuas_6g?t=20m38s
        @Nullable
        private Timer timer;
        @Nullable
        private WeakReference<Dialog> dialogWeakReference;
        private Map<String, Object> deviceInfo;

        CrashOpsController() {
            bgThread = new HandlerThread(CO_THREAD_TAG);
            bgThread.start();
            bgThreadHandler = new Handler(bgThread.getLooper());
            mainHandler = new Handler(); // The SDK Counts in that it will run on main thread
        }

        private void setContext(final Context context) {
            if (context == null) {
                SdkLogger.error(TAG, "Someone.... tried to send a 'null context' to CrashOps SDK... hmmm....");
                return;
            }

            if (contextHolder != null && contextHolder.get() != null) {
                if (contextHolder.get().getApplicationContext() != context.getApplicationContext()) {
                    // TODO Investigate if it may even occur
                    SdkLogger.error(TAG, "Someone.... was the app context change? That's simply cannot be...");
                }
            }

            synchronized (CrashOps.class) {
                if (contextHolder == null || contextHolder.get() == null) {
                    // Discussion: https://stackoverflow.com/questions/28440368/how-to-get-application-from-context
                    this.contextHolder = new WeakReference<>(context.getApplicationContext());

                    if (context.getApplicationContext() instanceof Application) {
                        COHostApplication.setContext(context.getApplicationContext());
                    } else {
                        // Hmmmm.... this should never happen
                        SdkLogger.error(TAG, "setContext: Failed to observe activity life cycles");
                    }

                    bgThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            CrashOpsController.sdkInstance.deviceInfo = DeviceInfoFetcher.getDeviceInfo();
                        }
                    });

                    CrashOps.initiate();
                }
            }

            if (Utils.isDebugMode()) {
                Utils.runTests();
            }
        }

        public Context getContext() {
            return contextHolder.get();
        }

        private void start() {
            if (timer != null) return;

            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    //
                }
            }, 0, Configurations.intervalMilliseconds);
        }

        private void stop() {
            if (timer == null) return;
            timer.cancel();
            timer = null;
        }

        private void toggle(boolean isOn) {
            isCrashOpsEnabled = isOn;
            ConfigurationsProvider.set(R.bool.co_is_crashops_enabled, isOn);

            if (isOn) {
                CrashOpsErrorHandler
                        .getInstance()
                        .initiate();
            } else {
                CrashOpsErrorHandler.getInstance().revert();
            }
        }

        public void logError(String title, Bundle errorDetails, StackTraceElement[] errorStackTrace) {
            CrashOpsErrorHandler.getInstance().onError(title, errorDetails, errorStackTrace);
        }
    }

    public static void setContext(Context context) {
        CrashOpsController.sdkInstance.setContext(context);
    }

    private static void initiate() {
        if (CrashOps.getInstance().setClientId(Configurations.clientId)) {
            Utils.debugToast("CrashOps client ID is: " + Configurations.clientId);
        }

        if (Configurations.isEnabled()) {
            CrashOpsErrorHandler
                    .getInstance()
                    .initiate();
        }

        Context context = COHostApplication.sharedInstance();

        if (!LogsHistoryWorker.registerSelf(context)) {
            Utils.debugDialog("Failed to register job!");
        }

        LogsHistoryWorker.runIfIdle(context, new Function1<Boolean, Unit>() {
            @Override
            public Unit invoke(Boolean result) {
                SdkLogger.log(TAG, result != null ? result.toString() : "null");

                return Unit.INSTANCE;
            }
        });
    }

    public static Context getApplicationContext() {
        return CrashOpsController.sdkInstance.getContext();
    }
}
