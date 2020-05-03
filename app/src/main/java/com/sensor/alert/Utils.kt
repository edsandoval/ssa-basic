package com.sensor.alert

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

fun log(msg: String) {
    Log.d("SENSOR-ALERT-SERVICE", msg)
}

fun formatDate(timestamp: Long): String? {
    return try {
        val sdf = SimpleDateFormat("yyyy-MMM-dd'T'HH:mm:ss")
        val tsDate = Date(timestamp)
        sdf.format(tsDate)
    } catch (e: Exception) {
        e.toString()
    }
}

object SensorParameters {
    const val STARTED_SERVICE_STATE = "1"
    const val MIN_UPDATE_TIME = 2000
    const val ERROR = 0.15.toFloat()
    const val MIN_INACTIVITY_TIME = 60 * 60 * 1000
    const val RETRIES_TIME = 30 * 60 * 1000
    const val MAX_RETRIES = 3
    const val HOUR_TAG = "#HT#"
    const val ALERT_MESSAGE = "ALERT!: The sensor has not detected motion for $HOUR_TAG hour(s)."
}