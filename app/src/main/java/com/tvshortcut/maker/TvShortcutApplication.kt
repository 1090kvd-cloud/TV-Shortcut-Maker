package com.tvshortcut.maker

import android.app.Application
import com.tvshortcut.maker.data.AppRepository
import com.tvshortcut.maker.data.BannerFactory
import com.tvshortcut.maker.data.FavoritesStore
import com.tvshortcut.maker.data.ShortcutHelper

/**
 * Application entry point.
 *
 * The project deliberately avoids a DI framework (Hilt/Koin) to keep the sample
 * copy-paste friendly: a tiny hand-rolled service locator is more than enough
 * for four collaborators.
 */
class TvShortcutApplication : Application() {

    /** Lazily created singletons shared by every ViewModel. */
    val container: AppContainer by lazy { AppContainer(this) }

    companion object {
        /** Convenience accessor used from [androidx.lifecycle.ViewModelProvider.Factory]. */
        lateinit var instance: TvShortcutApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

/** Holds the app-scoped collaborators. */
class AppContainer(application: Application) {
    val bannerFactory: BannerFactory = BannerFactory(application)
    val favoritesStore: FavoritesStore = FavoritesStore(application)
    val appRepository: AppRepository = AppRepository(application, bannerFactory, favoritesStore)
    val shortcutHelper: ShortcutHelper = ShortcutHelper(application, bannerFactory)
}
