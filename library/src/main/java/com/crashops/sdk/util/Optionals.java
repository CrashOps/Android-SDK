package com.crashops.sdk.util;

public class Optionals {
    public static <T> T safelyUnwrap(T obj, T inCaseOfNull) {
        return obj != null ? obj : inCaseOfNull;
    }
}
