package hu.gc.jegyzokonyv.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.gc.jegyzokonyv.data.db.AppDatabase
import hu.gc.jegyzokonyv.data.db.DraftDao
import hu.gc.jegyzokonyv.data.db.TemplateDao
import hu.gc.jegyzokonyv.data.file.FileStorage
import hu.gc.jegyzokonyv.data.file.FileStorageImpl
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.ProfileRepositoryImpl
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.data.repo.DraftRepositoryImpl
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.data.repo.TemplateRepositoryImpl
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.html.JsoupHtmlEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideDraftDao(db: AppDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun bindFileStorage(impl: FileStorageImpl): FileStorage

    @Binds
    @Singleton
    abstract fun bindHtmlEngine(impl: JsoupHtmlEngine): HtmlEngine

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: TemplateRepositoryImpl): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
