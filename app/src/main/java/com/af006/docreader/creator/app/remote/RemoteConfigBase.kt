package com.af010.creator.docreader.app.remote
import android.util.Log
import com.af010.creator.docreader.utils.SharedPreference
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import java.lang.reflect.Type
import kotlin.getValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class RemoteConfigBase : KoinComponent {

    companion object {
        const val TAG = "RemoteConfig"
    }

    @PublishedApi
    internal val sharedPreference: SharedPreference by inject()
    @PublishedApi
    internal val fetchers = mutableListOf<(Map<String, FirebaseRemoteConfigValue>) -> Unit>()
    @PublishedApi
    internal val keys = mutableListOf<String>()
    @PublishedApi
    internal val missingKeys = mutableListOf<String>()

    @Volatile
    private var fetchDeferred: Deferred<Unit>? = null

    @Synchronized
    fun fetchRemoteConfigAsync(scope: CoroutineScope, isDebug: Boolean): Deferred<Unit> =
        fetchDeferred ?: scope.async {
            runCatching { setupRemoteConfig(isDebug) }
                .onFailure { Log.w(TAG, "setupRemoteConfig failed", it) }
            Unit
        }.also { fetchDeferred = it }

    suspend fun setupRemoteConfig(isDebug: Boolean) {
        val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(30L)
            .setMinimumFetchIntervalInSeconds(if (isDebug) 0L else 3600L)
            .build()
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings).await()
        val isSuccess = runCatching {
            firebaseRemoteConfig.fetchAndActivate().await()
        }.getOrDefault(false)
        if (isSuccess) {
            withContext(Dispatchers.IO) { syncRemote(firebaseRemoteConfig) }
            if (isDebug) logKeyReport(firebaseRemoteConfig)
        } else if (isDebug) {
            Log.w(TAG, "fetch thất bại — dùng giá trị đã lưu/default")
        }
    }

    fun syncRemote(remoteConfig: FirebaseRemoteConfig) {
        val all = remoteConfig.all
        missingKeys.clear()
        fetchers.forEach { it(all) }
    }

    fun missingRemoteKeys(rc: FirebaseRemoteConfig): List<String> {
        val all = rc.all
        return keys.filter { all[it]?.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
    }

    private fun logKeyReport(rc: FirebaseRemoteConfig) {
        val all = rc.all
        keys.forEach { key ->
            val v = all[key]
            if (v == null) {
                Log.d(TAG, "✗ $key = (default)")
            } else {
                val raw = v.asString().let { if (it.length > 120) it.take(120) + "…" else it }
                Log.d(TAG, "✓ $key = $raw")
            }
        }
        Log.d(
            TAG, "Remote: ${keys.size - missingKeys.size}/${keys.size} keys" +
                    if (missingKeys.isNotEmpty()) " (thiếu ${missingKeys.size})" else ""
        )
    }

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : Any> parseValueByType(
        v: FirebaseRemoteConfigValue,
        type: Type,
        klass: KClass<T>,
    ): T? = when (klass) {
        Boolean::class -> v.asBoolean() as T
        Int::class -> v.asLong().toInt() as T
        Long::class -> v.asLong() as T
        String::class -> v.asString() as T
        else -> {
            val raw = v.asString().trim()
            if (raw.isBlank()) null
            else runCatching {
                sharedPreference.gson.fromJson<T>(raw, type)
            }.getOrNull()
        }
    }

    @PublishedApi
    internal inline fun <reified T : Any> remote(
        key: String,
        default: T,
        noinline block: (T.() -> Unit)? = null,
    ): ReadWriteProperty<Any?, T> {
        val type = object : TypeToken<T>() {}.type
        return remoteImpl(key, default, type, T::class, block)
    }

    @PublishedApi
    internal fun <T : Any> remoteImpl(
        key: String,
        default: T,
        type: Type,
        klass: KClass<T>,
        block: (T.() -> Unit)?,
    ): ReadWriteProperty<Any?, T> {
        keys += key
        fetchers += { all ->
            val rcVal = all[key]
            if (rcVal != null && rcVal.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                parseValueByType(rcVal, type, klass)?.let { value ->
                    sharedPreference.writeRuntime(key, value)
                }
            } else {
                missingKeys += key
            }
        }
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T {
                val value = if (GlobalContext.getOrNull() == null) {
                    default
                } else {
                    sharedPreference.readRuntime(key, default, type)
                }
                return value.also { block?.invoke(it) }
            }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (GlobalContext.getOrNull() != null) {
                    sharedPreference.writeRuntime(key, value)
                }
            }
        }
    }

    @PublishedApi
    internal inline fun <reified T : Any> remoteCustom(
        key: String,
        default: T,
        crossinline parse: (String) -> T,
    ): ReadWriteProperty<Any?, T> {
        val type = object : TypeToken<T>() {}.type
        return remoteCustomImpl(key, default, type) { parse(it) }
    }

    @PublishedApi
    internal fun <T : Any> remoteCustomImpl(
        key: String,
        default: T,
        type: Type,
        parse: (String) -> T,
    ): ReadWriteProperty<Any?, T> {
        keys += key
        fetchers += { all ->
            val rcVal = all[key]
            if (rcVal != null && rcVal.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                sharedPreference.writeRuntime(key, parse(rcVal.asString()))
            } else {
                missingKeys += key
            }
        }
        return object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): T =
                if (GlobalContext.getOrNull() == null) {
                    default
                } else {
                    sharedPreference.readRuntime(key, default, type)
                }

            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (GlobalContext.getOrNull() != null) {
                    sharedPreference.writeRuntime(key, value)
                }
            }
        }
    }
}
