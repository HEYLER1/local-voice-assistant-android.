package com.heyler.voicelab
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import org.json.JSONObject

fun deviceSample(context:Context):JSONObject {
    val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val manager=context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return JSONObject().apply {
        put("pss_mb",Debug.getPss()/1024.0)
        put("battery_percent",battery?.let {100.0*it.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)/it.getIntExtra(BatteryManager.EXTRA_SCALE,100)}?:JSONObject.NULL)
        put("battery_temperature_c",battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)?.div(10.0)?:JSONObject.NULL)
        put("charging",manager.isCharging)
        put("thermal_status",(context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus)
    }
}
