package com.sabaferoz.apptreminder

import android.app.Application
import android.content.Context
import com.sabaferoz.apptreminder.calendar.CalendarApi
import com.sabaferoz.apptreminder.calendar.CalendarSync
import com.sabaferoz.apptreminder.calendar.GoogleAuth
import com.sabaferoz.apptreminder.core.TimeZoneResolver
import com.sabaferoz.apptreminder.data.AppDatabase
import com.sabaferoz.apptreminder.data.Repository
import com.sabaferoz.apptreminder.data.SettingsRepo
import com.sabaferoz.apptreminder.work.Notifier
import com.sabaferoz.apptreminder.work.Scheduling

/** Hand-rolled service locator (Spring's ApplicationContext, minus the framework). */
class Graph(ctx: Context) {
    val db: AppDatabase by lazy { AppDatabase.create(ctx) }
    val settings: SettingsRepo by lazy { SettingsRepo(db.kv()) }
    val repo: Repository by lazy { Repository(db, settings) }
    val auth = GoogleAuth(ctx)
    val api = CalendarApi()
    val sync: CalendarSync by lazy { CalendarSync(repo, settings, auth, api) }

    /** Offline country→zones via the platform's ICU data (no network). */
    val zones = TimeZoneResolver { iso ->
        android.icu.util.TimeZone.getAvailableIDs(iso).toList()
    }
}

class App : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(applicationContext)
        Notifier.createChannel(this)
        Scheduling.ensurePeriodic(this)
    }
}
