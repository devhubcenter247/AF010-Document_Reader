package com.af010.creator.docreader.app.remote

val remoteConfig get() = RemoteConfig.Instance
class RemoteConfig : RemoteConfigBase() {

    companion object {
        val Instance: RemoteConfig by lazy { RemoteConfig() }
    }

    val paywallEnabled  by  remote("paywall_enabled", false)

}
