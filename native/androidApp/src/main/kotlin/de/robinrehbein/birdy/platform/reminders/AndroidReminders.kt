package de.robinrehbein.birdy.platform.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import de.robinrehbein.birdy.platform.ReminderPermission
import de.robinrehbein.birdy.platform.ReminderPlan
import de.robinrehbein.birdy.platform.Reminders

/**
 * [Reminders] on AlarmManager: one inexact alarm ([AlarmManager.setAndAllowWhileIdle], no exact-alarm
 * permission) whose [ReminderReceiver] posts the notification. The plan is mirrored into
 * SharedPreferences so [BootReceiver] can re-arm it after a reboot.
 *
 * [permissionRequester] is set by the Activity (it owns the ActivityResult launcher) and must be
 * safe to call from any thread.
 */
class AndroidReminders(context: Context) : Reminders {
    private val app = context.applicationContext
    @Volatile var permissionRequester: ((onResult: (Boolean) -> Unit) -> Unit)? = null

    override val permissionState: ReminderPermission
        get() = if (NotificationManagerCompat.from(app).areNotificationsEnabled()) ReminderPermission.Granted else ReminderPermission.Denied

    override fun schedule(plan: ReminderPlan) {
        runCatching {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_AT, plan.fireAtMillis).putString(KEY_TITLE, plan.title).putString(KEY_BODY, plan.body).apply()
            arm(app, plan)
        }
    }

    override fun cancelAll() {
        runCatching {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            alarmManager(app)?.cancel(pendingIntent(app, null))
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (permissionState == ReminderPermission.Granted) { onResult(true); return }
        val requester = permissionRequester
        if (requester == null) onResult(false) else requester(onResult)
    }

    companion object {
        const val PREFS = "birdy_reminders"
        const val KEY_AT = "at"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"

        private fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        private fun pendingIntent(context: Context, plan: ReminderPlan?): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
            if (plan != null) intent.putExtra(EXTRA_TITLE, plan.title).putExtra(EXTRA_BODY, plan.body)
            return PendingIntent.getBroadcast(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun arm(context: Context, plan: ReminderPlan) {
            alarmManager(context)?.setAndAllowWhileIdle(AlarmManager.RTC, plan.fireAtMillis, pendingIntent(context, plan))
        }
    }
}
