package de.robinrehbein.birdy.platform.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.robinrehbein.birdy.platform.ReminderPlan

/** Alarms do not survive a reboot: re-arm the stored plan if it is still in the future. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(AndroidReminders.PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong(AndroidReminders.KEY_AT, 0L)
        val title = prefs.getString(AndroidReminders.KEY_TITLE, null) ?: return
        val body = prefs.getString(AndroidReminders.KEY_BODY, null) ?: return
        if (at > System.currentTimeMillis()) runCatching { AndroidReminders.arm(context, ReminderPlan(at, title, body)) }
    }
}
