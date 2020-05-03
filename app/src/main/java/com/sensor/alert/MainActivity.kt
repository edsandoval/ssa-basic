package com.sensor.alert

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    private var preference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preference =
            Preference(this.applicationContext)

        title = "Sensor SMS Alert"

        val phoneNumber = findViewById<EditText>(R.id.phone_number)
        val unlockCode = findViewById<EditText>(R.id.unlock_code)
        val btnStartService = findViewById<Button>(R.id.btnStartService)
        val nroInactivityHours = findViewById<Spinner>(R.id.nro_inactivity_hours)

        btnStopService.isEnabled = false;
        if(preference!!.getAppStatus().equals(SensorParameters.STARTED_SERVICE_STATE)) {
            phoneNumber.setText(preference!!.getSmsNumber())
            unlockCode.setText(preference!!.getUnlockCode())
            nroInactivityHours.setSelection(preference!!.getNroHours()!!.minus(1))
            btnStopService.isEnabled = true;
            enabledOrDisabled(phoneNumber, unlockCode, btnStartService, nroInactivityHours,false)
        }

        btnStartService.let {
            it.setOnClickListener {
                log("START THE FOREGROUND SERVICE ON DEMAND")

                val prefPhoneNro =  phoneNumber.text.toString()
                val prefUnlockCode = unlockCode.text.toString()
                val hourValue = nroInactivityHours.selectedItemPosition

                if(prefPhoneNro.isEmpty() || prefUnlockCode.isEmpty()) {
                    Toast.makeText(this, "Empty unlock code or phone nro", Toast.LENGTH_LONG).show();
                    return@setOnClickListener;
                }

                preference?.setAppStatus(SensorParameters.STARTED_SERVICE_STATE)
                preference?.setSmsNumber(prefPhoneNro)
                preference?.setUnlockCode(prefUnlockCode)
                preference?.setNroHours(hourValue)

                btnStopService.isEnabled = true;
                enabledOrDisabled(phoneNumber, unlockCode, btnStartService, nroInactivityHours, false)

                actionOnService(Actions.START)
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                log("STOP THE FOREGROUND SERVICE ON DEMAND")

                val builder = AlertDialog.Builder(this)
                builder.setTitle("Stop monitoring?")
                val input = EditText(this)
                input.hint = "Unlock code"
                input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                builder.setView(input)

                builder.setPositiveButton("Ok") { dialog, _ ->
                    if (input.text.toString() == preference!!.getUnlockCode()) {
                        log("MonitorActivity - INPUT " + input.text.toString());
                        log("MonitorActivity - STORED " + preference!!.getUnlockCode());
                        dialog.dismiss();

                        preference?.setAppStatus("")
                        btnStopService.isEnabled = false;
                        enabledOrDisabled(phoneNumber, unlockCode, btnStartService, nroInactivityHours, true)

                        actionOnService(Actions.STOP)
                    } else {
                        dialog.dismiss();
                        Toast.makeText(applicationContext, "Wrong unlock code", Toast.LENGTH_SHORT).show();
                    }
                }

                builder.show()
            }
        }
    }

    fun enabledOrDisabled(comp1:View, comp2:View, comp3:View, comp4:View, enabled:Boolean) {
        comp1.isEnabled = enabled
        comp2.isEnabled = enabled
        comp3.isEnabled = enabled
        comp4.isEnabled = enabled
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, SensorService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            startService(it)
        }
    }
}
