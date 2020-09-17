package com.crashops.sdk.configuration;

import com.crashops.sdk.R;
import com.crashops.sdk.util.Constants;

/**
 * Created by Perry on 08/03/2018.
 * Provides a configurable provider for RUNTIME configurations from XML resource files.
 *
 * The custom configurations will take effect only if when the attributes are accessed programmatically (in contrary when they are configured only via the XML layouts / animations / drawable).
 */
public class Configurations {
    public static final String appKey;

    public static final long intervalMilliseconds = Constants.ONE_MINUTE_MILLISECONDS * 3;

    static {
        String _appKey = ConfigurationsProvider.getString(R.string.co_crashops_app_key);
        if (!_appKey.equalsIgnoreCase("unknown")) {
            appKey = _appKey;
        } else {
            appKey = null;
        }
    }

    public static boolean isEnabled() {
        return ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_enabled, true);
    }

    private static boolean isTracingScreens() {
        return ConfigurationsProvider.getBoolean(R.bool.co_is_using_screen_traces, true);
    }

    public static boolean isAllowedToToast() {
        return isEnabled() && ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_allowed_to_toast, false);
    }

    public static boolean isAllowedToAlert() {
        return isEnabled() && ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_allowed_to_alert, false);
    }

    public static boolean shouldExportWireframes() {
        return isEnabled() && isTracingScreens();
    }
}