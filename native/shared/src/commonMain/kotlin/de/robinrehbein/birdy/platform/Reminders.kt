package de.robinrehbein.birdy.platform

/** One local reminder: fire time (epoch millis, UTC based) and the notification text. */
data class ReminderPlan(val fireAtMillis: Long, val title: String, val body: String)

enum class ReminderPermission { Granted, Denied }

/**
 * Local (no server) reminder notifications. Only one reminder is ever pending: [schedule]
 * replaces the previous plan. Implementations must be callable from any thread and swallow
 * platform errors. Desktop/tests use [NoReminders].
 */
interface Reminders {
    /** Whether notifications may be posted right now (always [ReminderPermission.Granted] where no permission exists). */
    val permissionState: ReminderPermission
    fun schedule(plan: ReminderPlan)
    fun cancelAll()
    /** Shows the system permission prompt if needed; [onResult] gets whether notifications are allowed. */
    fun requestPermission(onResult: (Boolean) -> Unit)
}

object NoReminders : Reminders {
    override val permissionState: ReminderPermission = ReminderPermission.Denied
    override fun schedule(plan: ReminderPlan) = Unit
    override fun cancelAll() = Unit
    override fun requestPermission(onResult: (Boolean) -> Unit) = onResult(false)
}
