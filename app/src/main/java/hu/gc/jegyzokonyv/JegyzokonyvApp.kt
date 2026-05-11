package hu.gc.jegyzokonyv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import hu.gc.jegyzokonyv.di.IoDispatcher
import hu.gc.jegyzokonyv.domain.usecase.SeedTemplatesUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JegyzokonyvApp : Application() {

    @Inject lateinit var seedTemplatesUseCase: SeedTemplatesUseCase
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        scope.launch { seedTemplatesUseCase() }
    }
}
