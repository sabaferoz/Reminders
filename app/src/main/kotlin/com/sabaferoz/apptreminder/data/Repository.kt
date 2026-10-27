package com.sabaferoz.apptreminder.data

import androidx.room.withTransaction
import com.sabaferoz.apptreminder.core.AppSettings
import com.sabaferoz.apptreminder.core.AppointmentInfo
import com.sabaferoz.apptreminder.core.BuildResult
import com.sabaferoz.apptreminder.core.OutgoingMessageBuilder
import com.sabaferoz.apptreminder.core.ReminderKey
import com.sabaferoz.apptreminder.core.ReminderReconciler
import com.sabaferoz.apptreminder.core.ReminderStatus
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsRepo(private val dao: KvDao) {
    private val mutex = Mutex()
    val flow: Flow<AppSettings> = dao.observe(KEY).map { decode(it) }

    suspend fun get(): AppSettings = decode(dao.get(KEY))

    suspend fun update(f: (AppSettings) -> AppSettings) = mutex.withLock {
        dao.put(KvEntity(KEY, AppJson.encodeToString(AppSettings.serializer(), f(decode(dao.get(KEY))))))
    }

    private fun decode(s: String?) = s?.let { runCatching { AppJson.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()

    private companion object { const val KEY = "settings" }
}

/** Single place that touches the DB; business rules live in :core. */
class Repository(val db: AppDatabase, private val settingsRepo: SettingsRepo) {
    private val patients = db.patients()
    private val appointments = db.appointments()
    private val reminders = db.reminders()

    fun observePatients() = patients.observeAll()
    fun observeAppointments() = appointments.observeAll()
    fun observeReminders() = reminders.observeAll()

    suspend fun savePatient(p: PatientEntity): Long = patients.upsert(p)
    suspend fun deletePatient(id: Long) = patients.delete(id) // reminders cascade-delete
    suspend fun allPatients() = patients.all()

    suspend fun loadAppointments(demo: Boolean): List<AppointmentEntity> = appointments.all().filter { it.isDemo == demo }

    suspend fun upsertAppointments(items: List<AppointmentEntity>) = appointments.upsertAll(items)

    /** Recompute every reminder from appointments + patients + settings. Idempotent. */
    suspend fun reconcile(now: Instant = Instant.now()) = db.withTransaction {
        val settings = settingsRepo.get()
        val apptEntities = appointments.all()
        val result = ReminderReconciler.reconcile(
            appointments = apptEntities.map { it.toCore() },
            patients = patients.all().map { it.toCore() },
            existing = reminders.all().map { it.toCore() },
            settings = settings,
            now = now,
        )
        result.deletes.forEach { reminders.delete(it.appointmentId, it.patientId, it.kind.name) }
        reminders.upsertAll(result.upserts.map { it.toEntity() })
        val changed = apptEntities.filter { it.reviewNote != result.appointmentReviews[it.id] }
            .map { it.copy(reviewNote = result.appointmentReviews[it.id]) }
        if (changed.isNotEmpty()) appointments.upsertAll(changed)
        // Data minimisation: drop appointments (and their reminders) that ended more than 30 days ago.
        appointments.deleteEndedBefore(now.minus(Duration.ofDays(RETENTION_DAYS)).toEpochMilli())
    }

    suspend fun buildMessage(key: ReminderKey, now: Instant = Instant.now()): BuildResult {
        val settings = settingsRepo.get().reminder
        val state = reminders.get(key.appointmentId, key.patientId, key.kind.name)?.toCore()
            ?: return BuildResult.Blocked("Reminder no longer exists.")
        return OutgoingMessageBuilder.build(
            key.kind,
            appointments.get(key.appointmentId)?.toCore(),
            patients.get(key.patientId)?.toCore(),
            settings, state.draft, now,
        )
    }

    private suspend fun mutate(key: ReminderKey, f: (ReminderEntity) -> ReminderEntity) {
        val cur = reminders.get(key.appointmentId, key.patientId, key.kind.name) ?: return
        reminders.upsertAll(listOf(f(cur)))
    }

    suspend fun markOpened(key: ReminderKey, now: Instant = Instant.now()) =
        mutate(key) { it.copy(status = ReminderStatus.WHATSAPP_OPENED.name, locked = true, openedAtMs = now.toEpochMilli()) }

    suspend fun confirmSent(key: ReminderKey, now: Instant = Instant.now()) =
        mutate(key) { it.copy(status = ReminderStatus.SENT_CONFIRMED.name, locked = true, confirmedAtMs = now.toEpochMilli()) }

    suspend fun skip(key: ReminderKey, now: Instant = Instant.now()) =
        mutate(key) { it.copy(status = ReminderStatus.SKIPPED.name, locked = true, reason = "Skipped by user", skippedAtMs = now.toEpochMilli()) }

    /** "I did not send it": let automation take over again. */
    suspend fun notSent(key: ReminderKey) = mutate(key) { it.copy(locked = false, status = ReminderStatus.READY.name, notifiedAtMs = null) }

    suspend fun saveDraft(key: ReminderKey, text: String?) = mutate(key) { it.copy(draft = text?.takeIf { t -> t.isNotBlank() }) }

    /** Marks all READY reminders as notified and returns how many are READY. */
    suspend fun readyCountAndMarkNotified(now: Instant): Pair<Int, Boolean> = db.withTransaction {
        val ready = reminders.all().filter { it.status == ReminderStatus.READY.name }
        val fresh = ready.any { it.notifiedAtMs == null }
        if (fresh) reminders.upsertAll(ready.filter { it.notifiedAtMs == null }.map { it.copy(notifiedAtMs = now.toEpochMilli()) })
        ready.size to fresh
    }

    suspend fun nextDueAfter(now: Instant): Instant? = reminders.all()
        .filter { it.status == ReminderStatus.SCHEDULED.name }
        .mapNotNull { it.dueAtMs }.filter { it > now.toEpochMilli() }.minOrNull()?.let(Instant::ofEpochMilli)

    suspend fun deleteDemoData() = db.withTransaction { appointments.deleteDemo(); patients.deleteDemo() }
    suspend fun deleteRealAppointments() = appointments.deleteReal()
    suspend fun setDemoPhone(phone: String) = patients.setDemoPhone(phone)

    suspend fun wipeEverything() = db.withTransaction {
        appointments.deleteAll(); patients.deleteAll(); db.kv().deleteAll()
    }

    companion object { const val RETENTION_DAYS = 30L }
}

fun AppointmentInfo.isDemoId() = id.startsWith("demo-")
