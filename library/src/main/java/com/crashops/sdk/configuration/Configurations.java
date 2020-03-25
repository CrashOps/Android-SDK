package com.crashops.sdk.configuration;

import com.crashops.sdk.BuildConfig;
import com.crashops.sdk.R;
import com.crashops.sdk.util.Constants;
import com.crashops.sdk.util.Utils;

/**
 * Created by Perry on 08/03/2018.
 * Provides a configurable provider for RUNTIME configurations from XML resource files.
 *
 * The custom configurations will take effect only if when the attributes are accessed programmatically (in contrary when they are configured only via the XML layouts / animations / drawable).
 */
public class Configurations {

    private static final boolean _isAllowedToToast;
    private static final boolean isEnabled;
    public static final String clientId;
    private static final boolean _isAllowedToAlert;

    public static final long intervalMilliseconds = Constants.ONE_MINUTE_MILLISECONDS * 3;

    static {
        isEnabled = ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_enabled);

        _isAllowedToToast = isEnabled && ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_allowed_to_toast);
        _isAllowedToAlert = isEnabled && ConfigurationsProvider.getBoolean(R.bool.co_is_crashops_allowed_to_alert);

        String _clientId = ConfigurationsProvider.getString(R.string.co_crashops_client_id);
        if (!_clientId.equalsIgnoreCase("unknown")) {
            clientId = _clientId;
        } else {
            clientId = null;
        }
    }

    public static boolean isAllowedToToast() {
        return _isAllowedToToast && isEnabled();
    }

    public static boolean isAllowedToAlert() {
        return _isAllowedToAlert && isEnabled();
    }

    public static boolean isEnabled() {
        return isEnabled;
    }
}