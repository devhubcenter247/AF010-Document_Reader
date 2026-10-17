package com.af010.creator.docreader.di

import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.scan.ScanSessionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::DocumentsViewModel)
    viewModelOf(::ScanSessionViewModel)
}
