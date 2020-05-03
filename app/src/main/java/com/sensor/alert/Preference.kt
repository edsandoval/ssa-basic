package com.sensor.alert

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor


class Preference {
    private var appSharedPrefs: SharedPreferences
    private var prefsEditor: Editor? = null

    private val APP_SHARED_PREFS = "com.sensoralert"
    private val APP_STATE = "app_state"
    private val NRO_HOURS = "nro_hours"
    private val SMS_NUMBER = "sms_number"
    private val UNLOCK_CODE = "unlock_code"

    constructor(context: Context) {
        appSharedPrefs = context.getSharedPreferences(APP_SHARED_PREFS, Activity.MODE_PRIVATE)
        prefsEditor = appSharedPrefs.edit()
    }

    fun setSmsNumber(number: String?) {
        prefsEditor!!.putString(SMS_NUMBER, number)
        prefsEditor!!.commit()
    }

    fun getSmsNumber(): String? {
        return appSharedPrefs.getString(SMS_NUMBER, "")
    }

    fun setUnlockCode(unlockCode: String?) {
        prefsEditor!!.putString(UNLOCK_CODE, unlockCode)
        prefsEditor!!.commit()
    }

    fun getUnlockCode(): String? {
        return appSharedPrefs.getString(UNLOCK_CODE, "")
    }

    fun setAppStatus(appStatus:String?) {
        prefsEditor!!.putString(APP_STATE, appStatus)
        prefsEditor!!.commit()
    }

    fun getAppStatus(): String? {
        return appSharedPrefs.getString(APP_STATE, "")
    }

    fun setNroHours(hours:Int) {
        prefsEditor!!.putInt(NRO_HOURS, hours.plus(1))
        prefsEditor!!.commit()
    }

    fun getNroHours(): Int? {
        return appSharedPrefs.getInt(NRO_HOURS, 1)
    }

}