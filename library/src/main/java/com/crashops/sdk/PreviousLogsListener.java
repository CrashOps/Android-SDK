package com.crashops.sdk;

import androidx.annotation.NonNull;

import java.util.List;

public interface PreviousLogsListener {
    void onPreviousLogsDetected(@NonNull List<String> previousCrashes);
}
