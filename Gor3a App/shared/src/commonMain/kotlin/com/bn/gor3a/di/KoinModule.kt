package com.bn.gor3a.di

import com.bn.gor3a.data.local.LocalPrescriptionDataSource
import com.bn.gor3a.data.local.LocalReminderDataSource
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.data.repository.DrugRepositoryImpl
import com.bn.gor3a.data.repository.PrescriptionRepositoryImpl
import com.bn.gor3a.data.repository.ReminderRepositoryImpl
import com.bn.gor3a.domain.repository.DrugRepository
import com.bn.gor3a.domain.repository.PrescriptionRepository
import com.bn.gor3a.domain.repository.ReminderRepository
import com.bn.gor3a.domain.usecase.AnalyzePrescriptionUseCase
import com.bn.gor3a.domain.usecase.GetDrugAlternativesUseCase
import com.bn.gor3a.domain.usecase.GetDrugDetailsUseCase
import com.bn.gor3a.domain.usecase.DeletePrescriptionUseCase
import com.bn.gor3a.domain.usecase.GetSavedPrescriptionsUseCase
import com.bn.gor3a.domain.usecase.ManageRemindersUseCase
import com.bn.gor3a.domain.usecase.SavePrescriptionUseCase
import com.bn.gor3a.domain.usecase.SearchDrugsUseCase
import com.bn.gor3a.presentation.viewmodel.DrugExplorerViewModel
import com.bn.gor3a.presentation.viewmodel.HistoryViewModel
import com.bn.gor3a.presentation.viewmodel.HomeViewModel
import com.bn.gor3a.presentation.viewmodel.RemindersViewModel
import com.bn.gor3a.presentation.viewmodel.ResultsViewModel
import com.bn.gor3a.presentation.viewmodel.ScannerViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Data Sources
    single { Gor3aApiClient() }
    single { LocalPrescriptionDataSource() }
    single { LocalReminderDataSource() }

    // Repositories
    singleOf(::PrescriptionRepositoryImpl) bind PrescriptionRepository::class
    singleOf(::DrugRepositoryImpl) bind DrugRepository::class
    singleOf(::ReminderRepositoryImpl) bind ReminderRepository::class

    // Use Cases
    singleOf(::AnalyzePrescriptionUseCase)
    singleOf(::SearchDrugsUseCase)
    singleOf(::GetDrugDetailsUseCase)
    singleOf(::GetDrugAlternativesUseCase)
    singleOf(::GetSavedPrescriptionsUseCase)
    singleOf(::SavePrescriptionUseCase)
    singleOf(::DeletePrescriptionUseCase)
    singleOf(::ManageRemindersUseCase)

    // ViewModels (App-scoped singletons)
    singleOf(::HomeViewModel)
    singleOf(::ScannerViewModel)
    singleOf(::ResultsViewModel)
    singleOf(::DrugExplorerViewModel)
    singleOf(::RemindersViewModel)
    singleOf(::HistoryViewModel)
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(appModule)
}

private var isKoinStarted = false

fun startGor3aKoin() {
    if (!isKoinStarted) {
        try {
            initKoin()
        } catch (_: Exception) {}
        isKoinStarted = true
    }
}
