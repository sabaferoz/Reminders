package com.sabaferoz.apptreminder.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.sabaferoz.apptreminder.security.KeyVault
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "patients", indices = [Index("email")])
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alias: String,
    val email: String,
    val phone: String,
    val countryIso: String,
    val region: String,
    val timeZoneId: String,
    val consent: Boolean,
    val consentAtMs: Long?,
    val overrideAJson: String?,
    val overrideBJson: String?,
    val templateA: String?,
    val templateB: String?,
    val isDemo: Boolean = false,
)

/** Deliberately stores no title/description – only what reminders need. */
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey val id: String,
    val startMs: Long,
    val endMs: Long,
    val cancelled: Boolean,
    val meetLink: String?,
    val attendeesJson: String,
    val changeNote: String?,
    val reviewNote: String?,
    val isDemo: Boolean,
)

@Entity(
    tableName = "reminders",
    primaryKeys = ["appointmentId", "patientId", "kind"],
    foreignKeys = [
        ForeignKey(PatientEntity::class, ["id"], ["patientId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AppointmentEntity::class, ["id"], ["appointmentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("patientId"), Index("appointmentId")],
)
data class ReminderEntity(
    val appointmentId: String,
    val patientId: Long,
    val kind: String,
    val status: String,
    val dueAtMs: Long?,
    val startSnapshotMs: Long,
    val reason: String?,
    val locked: Boolean,
    val draft: String?,
    val createdAtMs: Long,
    val readyAtMs: Long?,
    val notifiedAtMs: Long?,
    val openedAtMs: Long?,
    val confirmedAtMs: Long?,
    val skippedAtMs: Long?,
)

@Entity(tableName = "kv")
data class KvEntity(@PrimaryKey val name: String, val json: String)

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY alias COLLATE NOCASE") fun observeAll(): Flow<List<PatientEntity>>
    @Query("SELECT * FROM patients") suspend fun all(): List<PatientEntity>
    @Query("SELECT * FROM patients WHERE id = :id") suspend fun get(id: Long): PatientEntity?
    @Upsert suspend fun upsert(p: PatientEntity): Long
    @Query("DELETE FROM patients WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM patients WHERE isDemo = 1") suspend fun deleteDemo()
    @Query("UPDATE patients SET phone = :phone WHERE isDemo = 1") suspend fun setDemoPhone(phone: String)
    @Query("DELETE FROM patients") suspend fun deleteAll()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY startMs") fun observeAll(): Flow<List<AppointmentEntity>>
    @Query("SELECT * FROM appointments") suspend fun all(): List<AppointmentEntity>
    @Query("SELECT * FROM appointments WHERE id = :id") suspend fun get(id: String): AppointmentEntity?
    @Upsert suspend fun upsertAll(items: List<AppointmentEntity>)
    @Query("DELETE FROM appointments WHERE isDemo = 1") suspend fun deleteDemo()
    @Query("DELETE FROM appointments WHERE isDemo = 0") suspend fun deleteReal()
    @Query("DELETE FROM appointments WHERE endMs < :cutoffMs") suspend fun deleteEndedBefore(cutoffMs: Long)
    @Query("DELETE FROM appointments") suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders") fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders") suspend fun all(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE appointmentId = :a AND patientId = :p AND kind = :k")
    suspend fun get(a: String, p: Long, k: String): ReminderEntity?
    @Upsert suspend fun upsertAll(items: List<ReminderEntity>)
    @Query("DELETE FROM reminders WHERE appointmentId = :a AND patientId = :p AND kind = :k")
    suspend fun delete(a: String, p: Long, k: String)
}

@Dao
interface KvDao {
    @Query("SELECT json FROM kv WHERE name = :name") fun observe(name: String): Flow<String?>
    @Query("SELECT json FROM kv WHERE name = :name") suspend fun get(name: String): String?
    @Upsert suspend fun put(e: KvEntity)
    @Query("DELETE FROM kv") suspend fun deleteAll()
}

@Database(
    entities = [PatientEntity::class, AppointmentEntity::class, ReminderEntity::class, KvEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patients(): PatientDao
    abstract fun appointments(): AppointmentDao
    abstract fun reminders(): ReminderDao
    abstract fun kv(): KvDao

    companion object {
        fun create(ctx: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(KeyVault.databasePassphrase(ctx))
            return Room.databaseBuilder(ctx, AppDatabase::class.java, "reminders.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
