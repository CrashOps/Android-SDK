package com.crashops.sdk.logic

import android.app.Activity
import com.crashops.sdk.data.model.ActivityDetails

class ActivityTracer: ActivityTraceable {
    private val activitiesTrace: ArrayList<ActivityDetails> = arrayListOf()

    override fun breadcrumbsReport(): List<ActivityDetails> {
        return activitiesTrace.toList()
    }

    fun addActivityToTrace(activity: Activity) {
        activitiesTrace.add(ActivityDetails(activity))
    }
}

interface ActivityTraceable {
    fun breadcrumbsReport(): List<ActivityDetails>
}
