package dev.neurofocus.neurfocus_dnd

import android.app.Application
import dev.neurofocus.neurfocus_dnd.cactus.CactusModelPrefs
import dev.neurofocus.neurfocus_dnd.cactus.CactusModelRepository

class NeuroApp : Application() {

    lateinit var cactusModelRepository: CactusModelRepository
        private set

    override fun onCreate() {
        super.onCreate()
        cactusModelRepository = CactusModelRepository(this, CactusModelPrefs(this))
    }
}
