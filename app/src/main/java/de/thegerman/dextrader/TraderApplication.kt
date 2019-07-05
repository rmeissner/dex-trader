package de.thegerman.dextrader

import android.app.Application
import com.squareup.moshi.Moshi
import de.thegerman.dextrader.bridge.BridgeServer
import de.thegerman.dextrader.repositories.SessionRepository
import de.thegerman.dextrader.repositories.SessionRepositoryImpl
import de.thegerman.dextrader.ui.main.MainViewModel
import de.thegerman.dextrader.ui.main.MainViewModelContract
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.walletconnect.impls.FileWCSessionStore
import org.walletconnect.impls.WCSessionStore
import java.io.File

class TraderApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // start Koin!
        startKoin {
            // Android context
            androidContext(this@TraderApplication)
            // modules
            modules(listOf(myModule, repositoryModule, viewModelModule))

            val bridge: BridgeServer by inject()
            bridge.init()
        }
    }

    private val myModule = module {

        single { OkHttpClient.Builder().build() }

        single { Moshi.Builder().build() }

        single<WCSessionStore> { FileWCSessionStore(File(cacheDir, "session_store.json").apply { createNewFile() }, get()) }

        single { BridgeServer(get()).apply { start() } }
    }

    private val repositoryModule = module {
        single<SessionRepository> { SessionRepositoryImpl(get(), get(), get()) }
    }

    private val viewModelModule = module {
        viewModel<MainViewModelContract> { MainViewModel(get()) }
    }
}