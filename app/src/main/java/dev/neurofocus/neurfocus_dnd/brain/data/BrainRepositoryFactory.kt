package dev.neurofocus.neurfocus_dnd.brain.data

import android.app.Application
import android.content.pm.PackageManager
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleEegRepository
import dev.neurofocus.neurfocus_dnd.util.DefaultDispatcherProvider

object BrainRepositoryFactory {

    fun create(application: Application): BrainDataRepository {
        val pm = application.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return FakeEegRepository(DefaultDispatcherProvider())
        }
        return BleEegRepository(application)
    }
}
