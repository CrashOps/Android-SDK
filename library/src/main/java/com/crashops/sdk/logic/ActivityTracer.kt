package com.crashops.sdk.logic

import android.app.Activity
import com.crashops.sdk.data.Repository
import com.crashops.sdk.data.model.ActivityDetails

class ActivityTracer: ActivityTraceable {
    private val tracers: HashMap<Activity,ActivityDetails> = hashMapOf()

    override fun addActivityToTrace(activity: Activity) {
        val activityDetails = ActivityDetails(activity)
        tracers[activity] = activityDetails
        Repository.instance.persistBreadcrumb(activityDetails)
    }

    override fun tracesReport(sessionId: String): List<ActivityDetails> {
        return Repository.instance.traces(sessionId)
    }

    override fun stopTracing(activity: Activity) {
        val activityDetails = tracers[activity]
        activityDetails?.stopObserving(activity)
        tracers.remove(activity)
    }
}

interface ActivityTraceable {
    fun tracesReport(sessionId: String): List<ActivityDetails>
    fun stopTracing(activity: Activity)
    fun addActivityToTrace(activity: Activity)
}
