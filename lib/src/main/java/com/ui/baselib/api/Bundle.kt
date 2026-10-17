package com.ui.baselib.api

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import java.io.Serializable

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> BundleCompat.getParcelable(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> IntentCompat.getParcelableExtra(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}

inline fun <reified T : Parcelable> Intent.parcelableList(key: String): List<T>? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> IntentCompat.getParcelableArrayListExtra(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
}

inline fun <reified T : Parcelable> Bundle.parcelableList(key: String): List<T>? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> BundleCompat.getParcelableArrayList(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getParcelableArrayList(key)
}

inline fun <reified T : Serializable> Bundle.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> BundleCompat.getSerializable(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getSerializable(key) as? T
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> IntentCompat.getSerializableExtra(
        this,
        key,
        T::class.java
    )
    else -> @Suppress("DEPRECATION") getSerializableExtra(key) as? T
}
@Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
fun Bundle.putAnySafe(key: String, value: Any?) {
    when (value) {
        null -> putSerializable(key, null as java.io.Serializable?)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Boolean -> putBoolean(key, value)
        is Float -> putFloat(key, value)
        is Double -> putDouble(key, value)
        is Short -> putShort(key, value)
        is Byte -> putByte(key, value)
        is Char -> putChar(key, value)
        is String -> putString(key, value)
        is CharSequence -> putCharSequence(key, value)
        is Bundle -> putBundle(key, value)
        is Parcelable -> putParcelable(key, value)
        is Serializable -> putSerializable(key, value)
        is IntArray -> putIntArray(key, value)
        is LongArray -> putLongArray(key, value)
        is BooleanArray -> putBooleanArray(key, value)
        is FloatArray -> putFloatArray(key, value)
        is DoubleArray -> putDoubleArray(key, value)
        is ByteArray -> putByteArray(key, value)
        is CharArray -> putCharArray(key, value)
        is ShortArray -> putShortArray(key, value)
        is Array<*> -> when {
            value.isArrayOf<String>() -> {
                putStringArray(key, value as Array<String>)
            }
            value.isArrayOf<CharSequence>() -> putCharSequenceArray(
                key,
                value as Array<CharSequence>
            )
            value.isArrayOf<Parcelable>() ->
                putParcelableArray(key, value as Array<Parcelable>)
            else -> putSerializable(key, value as java.io.Serializable?)
        }
        is List<*> -> {
            when {
                value.isEmpty() -> putStringArrayList(key, arrayListOf())
                value.all { it is Parcelable } -> {
                    putParcelableArrayList(
                        key,
                        ArrayList(value as List<android.os.Parcelable>)
                    )
                }
                value.all { it is String } -> {
                    putStringArrayList(key, ArrayList(value as List<String>))
                }
                value.all { it is CharSequence } -> {
                    putCharSequenceArrayList(
                        key,
                        ArrayList(value as List<CharSequence>)
                    )
                }
                value.all { it is java.io.Serializable } -> {
                    putSerializable(key, ArrayList(value as List<java.io.Serializable>))
                }
                else -> {
                    throw IllegalArgumentException("Unsupported mixed List for key=\"$key\"")
                }
            }
        }
        is android.util.Size -> putSize(key, value)
        is android.util.SizeF -> putSizeF(key, value)
        else -> throw IllegalArgumentException(
            "Unsupported type for key=\"$key\": ${value::class.java.name}"
        )
    }
}
@Suppress("KotlinConstantConditions", "UNCHECKED_CAST")
fun Intent.putExtraSmart(key: String, v: Any?) {
    when (v) {
        null -> putExtra(key, null as Serializable?)
        is Int -> putExtra(key, v)
        is Long -> putExtra(key, v)
        is Boolean -> putExtra(key, v)
        is Float -> putExtra(key, v)
        is Double -> putExtra(key, v)
        is Short -> putExtra(key, v)
        is Byte -> putExtra(key, v)
        is Char -> putExtra(key, v)
        is String -> putExtra(key, v)
        is CharSequence -> putExtra(key, v)
        is Bundle -> putExtra(key, v)
        is Parcelable -> putExtra(key, v)
        is Serializable -> putExtra(key, v)
        is IntArray -> putExtra(key, v)
        is LongArray -> putExtra(key, v)
        is BooleanArray -> putExtra(key, v)
        is FloatArray -> putExtra(key, v)
        is DoubleArray -> putExtra(key, v)
        is ByteArray -> putExtra(key, v)
        is CharArray -> putExtra(key, v)
        is ShortArray -> putExtra(key, v)
        is Array<*> -> when {
            v.isArrayOf<String>() -> putExtra(key, v as Array<String>)
            v.isArrayOf<CharSequence>() -> putExtra(key, v as Array<CharSequence>)
            v.isArrayOf<android.os.Parcelable>() -> putExtra(key, v as Array<android.os.Parcelable>)
            else -> putExtra(key, v as java.io.Serializable?)
        }
        is List<*> -> when {
            v.isEmpty() -> putStringArrayListExtra(key, arrayListOf())
            v.all { it is android.os.Parcelable } ->
                putParcelableArrayListExtra(key, ArrayList(v as List<android.os.Parcelable>))
            v.all { it is String } ->
                putStringArrayListExtra(key, ArrayList(v as List<String>))
            v.all { it is CharSequence } ->
                putCharSequenceArrayListExtra(key, ArrayList(v as List<CharSequence>))
            v.all { it is java.io.Serializable } ->
                putExtra(key, ArrayList(v as List<java.io.Serializable>)) // <-- fixed bug
            else -> throw IllegalArgumentException("Unsupported mixed List for key=\"$key\"")
        }
        else -> throw IllegalArgumentException("Unsupported extra type for key=\"$key\": ${v::class.java}")
    }
}

fun Bundle.putArgsSafely(
    vararg params: Pair<String, Any?>,
    logTag: String = "Bundle"
): Bundle = apply {
    params.forEach { (key, value) ->
        runCatching { putAnySafe(key, value) }
            .onFailure { Log.e(logTag, "Skip argument \"$key\"", it) }
    }
}

fun safeBundleOf(vararg params: Pair<String, Any?>): Bundle =
    Bundle().putArgsSafely(*params)

fun Intent.putExtrasSafely(
    vararg params: Pair<String, Any?>,
    logTag: String = "Intent"
): Intent = apply {
    params.forEach { (key, value) ->
        runCatching { putExtraSmart(key, value) }
            .onFailure { Log.e(logTag, "Skip extra \"$key\"", it) }
    }
}

inline fun <reified T> Activity.getData(key: String?): T? =
    intent?.safeGet<T>(key)
inline fun <reified T> Activity.getData(key: String, default:T): T =
    intent?.safeGet<T>(key) ?: default

fun Boolean?.orFalse() = this == true

inline fun <reified T : Any> ActivityResult.getResultData(key: String): T? =
    data?.safeGet<T>(key)

inline fun <reified T : Any?> Intent.safeGet(key: String?): T? {
    if (key.isNullOrEmpty()) return null
    val extras = extras ?: return null
    if (!extras.containsKey(key)) return null
    @Suppress("UNCHECKED_CAST")
    when (T::class) {
        Int::class -> return getIntExtra(key, 0) as T
        Long::class -> return getLongExtra(key, 0L) as T
        Boolean::class -> return getBooleanExtra(key, false) as T
        Float::class -> return getFloatExtra(key, 0f) as T
        Double::class -> return getDoubleExtra(key, 0.0) as T
        Char::class -> return getCharExtra(key, Char.MIN_VALUE) as T
        String::class -> return getStringExtra(key) as T
        CharSequence::class -> return getCharSequenceExtra(key) as T
    }
    if (Parcelable::class.java.isAssignableFrom(T::class.java)) {
        return if (Build.VERSION.SDK_INT >= 33)
            getParcelableExtra(key, T::class.java)
        else
            @Suppress("DEPRECATION")
            getParcelableExtra(key) as? T
    }
    when (T::class.java) {
        IntArray::class.java -> return getIntArrayExtra(key) as T?
        LongArray::class.java -> return getLongArrayExtra(key) as T?
        BooleanArray::class.java -> return getBooleanArrayExtra(key) as T?
        FloatArray::class.java -> return getFloatArrayExtra(key) as T?
        DoubleArray::class.java -> return getDoubleArrayExtra(key) as T?
        CharArray::class.java -> return getCharArrayExtra(key) as T?
        ByteArray::class.java -> return getByteArrayExtra(key) as T?
        Array<String>::class.java -> return getStringArrayExtra(key) as T?
        Array<CharSequence>::class.java -> return getCharSequenceArrayExtra(key) as T?
    }
    runCatching {
        @Suppress("UNCHECKED_CAST")
        when {
            T::class.java == java.util.ArrayList::class.java -> {
                getStringArrayListExtra(key) as T?
                    ?: getCharSequenceArrayListExtra(key) as T?
                    ?: if (Build.VERSION.SDK_INT >= 33)
                        getParcelableArrayListExtra(key, Parcelable::class.java)?.let { it as T? }
                    else
                        @Suppress("DEPRECATION")
                        getParcelableArrayListExtra<Parcelable>(key)?.let { it as T? }
            }
            else -> null
        }?.let { return it }
    }
    if (Serializable::class.java.isAssignableFrom(T::class.java)) {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        return getSerializableExtra(key) as? T
    }
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    return extras.get(key) as? T
}

inline fun <reified T : Any> Fragment.getData(key: String?): T? =
    arguments?.safeGet<T>(key)

inline fun <reified T : Any> Fragment.getData(key: String, default: T): T =
    arguments?.safeGet<T>(key) ?: default

inline fun <reified T : Any> Bundle.safeGet(key: String?): T? {
    if (key.isNullOrEmpty() || !containsKey(key)) return null
    @Suppress("UNCHECKED_CAST")
    when (T::class) {
        Int::class -> return getInt(key, 0) as T
        Long::class -> return getLong(key, 0L) as T
        Boolean::class -> return getBoolean(key, false) as T
        Float::class -> return getFloat(key, 0f) as T
        Double::class -> return getDouble(key, 0.0) as T
        Char::class -> return getChar(key, Char.MIN_VALUE) as T
        String::class -> return getString(key) as T
        CharSequence::class -> return getCharSequence(key) as T
    }

    if (Parcelable::class.java.isAssignableFrom(T::class.java)) {
        @Suppress("UNCHECKED_CAST")
        return (if (Build.VERSION.SDK_INT >= 33)
            getParcelable(key, T::class.java)
        else
            @Suppress("DEPRECATION")
            getParcelable<Parcelable>(key) as? T)
    }
    @Suppress("UNCHECKED_CAST")
    when (T::class.java) {
        IntArray::class.java -> return getIntArray(key) as T?
        LongArray::class.java -> return getLongArray(key) as T?
        BooleanArray::class.java -> return getBooleanArray(key) as T?
        FloatArray::class.java -> return getFloatArray(key) as T?
        DoubleArray::class.java -> return getDoubleArray(key) as T?
        CharArray::class.java -> return getCharArray(key) as T?
        ByteArray::class.java -> return getByteArray(key) as T?
        Array<String>::class.java -> return getStringArray(key) as T?
        Array<CharSequence>::class.java -> return getCharSequenceArray(key) as T?
    }
    runCatching {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        when {
            getStringArrayList(key) != null -> return getStringArrayList(key) as T
            getCharSequenceArrayList(key) != null -> return getCharSequenceArrayList(key) as T
            else -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    getParcelableArrayList(key, Parcelable::class.java)?.let { return it as T }
                } else {
                    getParcelableArrayList<Parcelable>(key)?.let { return it as T }
                }
            }
        }
    }.getOrDefault(null)
    if (Serializable::class.java.isAssignableFrom(T::class.java)) {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        return getSerializable(key) as? T
    }
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    return get(key) as? T
}

inline fun <reified T> T.logD(message: String) {
    Log.d(T::class.java.simpleName, message)
}

inline fun <reified T> T.logE(message: String) {
    Log.e(T::class.java.simpleName, message)
}

inline fun <reified T> T.logW(message: String) {
    Log.w(T::class.java.simpleName, message)
}
