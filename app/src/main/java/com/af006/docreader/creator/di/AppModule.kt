package com.af010.creator.docreader.di

import androidx.room.Room
import com.af010.creator.docreader.database.AppDatabase
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.database.DocumentRepositoryImpl
import com.af010.creator.docreader.pdf.ContentIndex
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.pdf.PdfTranslator
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.utils.PreferenceHelper
import com.af010.creator.docreader.utils.SharedPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    single { SharedPreference(get()) }
    single { PreferenceHelper(get()) }
    single {
        Room.databaseBuilder(androidApplication(), AppDatabase::class.java, "documents.db").build()
    }
    single { get<AppDatabase>().documentDao() }
    single { get<AppDatabase>().favoriteDao() }
    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single<DocumentRepository> {
        DocumentRepositoryImpl(androidContext(), get(), get(), get(named("appScope")))
    }
    single { PdfToolbox(androidContext()) }
    single { PdfTranslator(get()) }
    single { ContentIndex(androidContext(), get()) }
    single { ToolRunner(androidContext(), get(), get(), get(), get(named("appScope"))) }
}