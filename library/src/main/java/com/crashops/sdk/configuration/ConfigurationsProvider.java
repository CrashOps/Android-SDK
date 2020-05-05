package com.crashops.sdk.configuration;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import androidx.annotation.BoolRes;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import com.crashops.sdk.COHostApplication;
import com.crashops.sdk.util.SdkLogger;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Created by Perry on 08/03/2018.
 * Provides a configurable provider for RUNTIME configurations from XML resource files.
 *
 * The custom configurations will take effect only if when the attributes are accessed programmatically (in contrary when they are configured only via the XML layouts / animations / drawable).
 */
public class ConfigurationsProvider {
    private static final String TAG = ConfigurationsProvider.class.getSimpleName();
    private static final String SHARED_PREFERENCES_FILE_NAME = "runtime-config";

    static private SparseArray<Object> values;
    private static final SharedPreferences sharedPreferences;

    static {
        values = new SparseArray<>();

        Context context = COHostApplication.sharedInstance();
        sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(@BoolRes int resId) {
        return getBoolean(resId, false);
    }

    public static boolean getBoolean(@BoolRes int resId, boolean defaultValue) {
        Boolean result = (Boolean) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            try {
                result = sharedPreferences.getBoolean(String.valueOf(resId), context.getResources().getBoolean(resId));
            } catch (Resources.NotFoundException e) {
                // Theoretically speaking, this should never happen....
                result = defaultValue;
            }

            values.put(resId, result);
        }

        return result;
    }

    public static float getDimension(@DimenRes int resId) {
        Float result = (Float) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            try {
                result = sharedPreferences.getFloat(String.valueOf(resId), context.getResources().getDimension(resId));
                values.put(resId, result);
            } catch (Resources.NotFoundException e) {
                SdkLogger.error(TAG, "getDimension: " + e);
                result = (float) -1;
            }
        }

        return result;
    }

    public static Drawable getDrawable(@DrawableRes int resId) {
        Drawable result = (Drawable) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            try {
                result = ResourcesCompat.getDrawable(context.getResources(), resId, null);
                values.put(resId, result);
            } catch (Resources.NotFoundException e) {
                SdkLogger.error(TAG, "getDrawable: " + e);
            }
        }

        return result;
    }

    public static int getInteger(@IntegerRes int resId) throws Resources.NotFoundException {
        Integer result = (Integer) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            result = sharedPreferences.getInt(String.valueOf(resId), context.getResources().getInteger(resId));
            values.put(resId, result);
        }

        return result;
    }

    public static String getString(@StringRes int resId) throws Resources.NotFoundException {
        String result = (String) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            result = sharedPreferences.getString(String.valueOf(resId), context.getResources().getString(resId));
            values.put(resId, result);
        }

        return result;
    }

    public static int getColor(@ColorRes int resId) {
        Integer result = (Integer) values.get(resId);
        if (result == null) {
            Context context = COHostApplication.sharedInstance();
            try {
                result = sharedPreferences.getInt(String.valueOf(resId), ResourcesCompat.getColor(context.getResources(), resId, null));
                values.put(resId, result);
            } catch (Resources.NotFoundException e){
                SdkLogger.error(TAG, "getColor: " + e);
                result = -1;
            }
        }

        return result;
    }

    /**
     * Sets a custom string value
     * @param resId      The ID of the custom value
     * @param stringValue The custom string value for the given ID
     */
    public static void set(int resId, String stringValue) {
        sharedPreferences.edit().putString(String.valueOf(resId), stringValue).apply();

        save(resId, stringValue);
    }

    /**
     * Sets a custom boolean value, used for toggle behaviours.
     *
     * @param resId      The ID of the custom value
     * @param booleanValue The custom int value for the given ID
     */
    public static void set(int resId, boolean booleanValue) {
        set(resId, booleanValue, false);
    }

    /**
     * Sets a custom boolean value, used for toggle behaviours
     *
     * @param resId      The ID of the custom value
     * @param booleanValue The custom int value for the given ID
     * @param isPersistent Tells if the configuration is should be remembered
     */
    public static void set(int resId, boolean booleanValue, boolean isPersistent) {
         if (isPersistent) {
             sharedPreferences.edit().putBoolean(String.valueOf(resId), booleanValue).apply();
         }

        save(resId, booleanValue);
    }

    /**
     * Sets a custom float value, used for dimensions
     * @param resId      The ID of the custom value
     * @param floatValue The custom float value for the given ID
     */
    public static void set(int resId, float floatValue) {
        // For colors AND integers, it doesn't matter because the ID is different for each resource.
        sharedPreferences.edit().putFloat(String.valueOf(resId), floatValue).apply();

        save(resId, floatValue);
    }

    /**
     * Sets a custom int value, used for integers and colors
     * @param resId      The ID of the custom value
     * @param intValue The custom int value for the given ID
     */
    public static void set(int resId, int intValue) {
        // For colors AND integers, it doesn't matter because the ID is different for each resource.
        sharedPreferences.edit().putInt(String.valueOf(resId), intValue).apply();

        save(resId, intValue);
    }


    /**
     * Resets to default value (will be determined by the XML)
     * @param resId      The ID to reset
     */
    public static void reset(int resId) {
        save(resId, null);
    }

    private static void save(int resId, @Nullable Object resValue) {
        if (resValue == null) {
            values.remove(resId);
            sharedPreferences.edit().remove(String.valueOf(resId)).apply();
        } else {
            values.put(resId, resValue);
        }
    }

    /**
     * Using reflection to fetch all resources names of and their IDs from a given resource class.
     * @param rInnerClass A class from teh generated R file
     */
    public static HashMap<Integer, String> scanXmlIdsAndNames(Class<?> rInnerClass) {
        @SuppressLint("UseSparseArrays") HashMap<Integer, String> fieldIdMap = new HashMap<>(); // Intentionally using 'UseSparseArrays', because later we'll use the ID from the key.
        if (rInnerClass == null) return fieldIdMap;

        try {
            Field[] fields = rInnerClass.getFields();
            for (Field field : fields) {
                if (field == null) continue;
                int id = field.getInt(null);
                fieldIdMap.put(id, field.getName());
            }
        } catch (final Exception e) {
            SdkLogger.error(TAG, "Failed to scan XML IDs and names", e);
        }

        return fieldIdMap;
    }
}
