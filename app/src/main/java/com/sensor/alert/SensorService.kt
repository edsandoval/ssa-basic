package com.sensor.alert

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class SensorService : Service(), SensorEventListener {

    private lateinit var mSensorManager: SensorManager
    private lateinit var mAudioManager: AudioManager

    private var mAccelerometer : Sensor ?= null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var lastSensorUpdate = 0.toLong()

    private var lastV0 = 0.0.toFloat()
    private var lastV1 = 0.0.toFloat()
    private var lastV2 = 0.0.toFloat()

    private var lastMoveTimestamp = 0.toLong()
    private var preference: Preference? = null
    private var sendingSmsAlerts: Int = 0

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        val notification = createNotification()
        preference =
            Preference(this.applicationContext)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // focus in accelerometer
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(
            this,
            ServiceState.STARTED
        )

        // Registramos el oyente del acelerometro
        mSensorManager.registerListener(this,mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {
                    checkInactivityAndSendAlert()
                }
                delay(1 * 60 * 1000)
            }
            log("End of the loop for the service")
        }
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            // desregisrtamos el oyente del sensor.
            mSensorManager.unregisterListener(this)

            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(
            this,
            ServiceState.STOPPED
        )
    }

    private fun checkInactivityAndSendAlert() {
        val currentTimestamp: Long = System.currentTimeMillis()
        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val maxInactivity = preference!!.getNroHours()
        val originalVolume: Int = mAudioManager.getStreamVolume(AudioManager.STREAM_RING)

        try {
            val sInfo =
                """
                {
                    "deviceId": "$deviceId",
                    "maxInactivity:" $maxInactivity,
                    "currentTimestamp:" ${formatDate(currentTimestamp)},
                    "lastMoveTimestamp:" ${formatDate(lastMoveTimestamp)},
                    "originalVolume:" $originalVolume,
                    "sendingSmsAlerts:" $sendingSmsAlerts,
                    "X:" $lastV0,
                    "Y:" $lastV1,
                    "Z:" $lastV2
                }
            """

            log("CHECK-INFORMATION -> $sInfo")
            // Control the retries and reset time.
            if(sendingSmsAlerts >= SensorParameters.MAX_RETRIES) {
                lastMoveTimestamp = currentTimestamp
                sendingSmsAlerts = 0
            }

            if(currentTimestamp - lastMoveTimestamp > maxInactivity!!.times(SensorParameters.MIN_INACTIVITY_TIME)) {
                log(">>>> SENDING ALERT <<<<")
                lastMoveTimestamp += SensorParameters.RETRIES_TIME
                sendingSmsAlerts++

                // Set ring/music volume to max.
                mAudioManager.setStreamVolume(AudioManager.STREAM_RING, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)

                // run alarm phone how alert!
                //val ringtone: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                //val mp: MediaPlayer = MediaPlayer.create(applicationContext, ringtone)
                //mp.start()

                // Send alert by sms.
                val message = SensorParameters.ALERT_MESSAGE.replace(SensorParameters.HOUR_TAG, maxInactivity.toString())
                val manager: SmsManager = SmsManager.getDefault()
                manager.sendTextMessage(preference!!.getSmsNumber(), null, message, null, null)
            }
        } catch (e: Exception) {
            log("Error checking alert process: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "SENSOR SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Sensor Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Sensor Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Sensor Service")
            .setContentText("Sensor Service Alert is working")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val currentTimestamp: Long = System.currentTimeMillis()
        val diff = currentTimestamp.toLong() - lastSensorUpdate
        if (event != null && diff > SensorParameters.MIN_UPDATE_TIME)
        {
            lastSensorUpdate = currentTimestamp

            val value0 = event.values[0]
            val value1 = event.values[1]
            val value2 = event.values[2]

            if(isBetweenErrorInterval(value0, lastV0)
                && isBetweenErrorInterval(value1, lastV1)
                && isBetweenErrorInterval(value2, lastV2)) {
                log("The phone does not move...");
            } else {
                log("** PHONE MOVED **");
                lastMoveTimestamp = currentTimestamp
                log("X: $value0, Y: $value1, Z: $value2")

                lastV0 = value0
                lastV1 = value1
                lastV2 = value2
            }
        }
    }

    private fun isBetweenErrorInterval(aCurrentVal: Float, aLastVal: Float): Boolean {
        val minVal = aLastVal - SensorParameters.ERROR
        val maxVal = aLastVal + SensorParameters.ERROR
        return (aCurrentVal > minVal && aCurrentVal < maxVal)
    }

}
