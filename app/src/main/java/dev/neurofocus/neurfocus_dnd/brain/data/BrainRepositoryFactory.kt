package dev.neurofocus.neurfocus_dnd.brain.data

import android.app.Application
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BleEegRepository

object BrainRepositoryFactory {

    fun create(application: Application): BrainDataRepository {
        return BleEegRepository(application)
    }
}
