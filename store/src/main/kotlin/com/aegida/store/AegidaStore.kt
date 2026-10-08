package com.aegida.store

import android.content.Context
import com.aegida.core.health.CheckLog

/**
 * What the rest of the app gets from :store: the :core ports, never Room types. Make one per process
 * (the app's composition root holds it as a singleton).
 */
class AegidaStore(context: Context) {
    private val db = AegidaDatabase.open(context.applicationContext)

    val checkLog: CheckLog = RoomCheckLog(db.checkRuns())
}
