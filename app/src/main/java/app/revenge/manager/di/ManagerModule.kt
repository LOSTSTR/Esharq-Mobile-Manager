package app.revenge.manager.di

import app.revenge.manager.domain.manager.DownloadManager
import app.revenge.manager.domain.manager.InstallManager
import app.revenge.manager.domain.manager.PreferenceManager
import app.revenge.manager.esharq.EsharqAuth
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val managerModule = module {
    singleOf(::DownloadManager)
    singleOf(::PreferenceManager)
    singleOf(::InstallManager)
    singleOf(::EsharqAuth)
}