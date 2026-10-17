package com.ui.baselib.lifecycle

import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

fun Fragment.runAfter(delayMillis: Long = 1000L, block: () -> Unit) {
    android.os.Handler(Looper.getMainLooper()).postDelayed({
        // A fragment on the back stack is still added but has no view to update.
        if (isAdded && view != null) {
            block()
        }
    }, delayMillis)
}

fun runAfter(delayMillis: Long = 1000L, block: () -> Unit) {
    android.os.Handler(Looper.getMainLooper()).postDelayed({
        block()
    }, delayMillis)
}

fun <T> StateFlow<T>.launchCollect(
    owner: LifecycleOwner,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    collector: (T) -> Unit
): Job = owner.lifecycleScope.launch(dispatcher) {
    owner.repeatOnLifecycle(state) {
        collect { collector(it) }
    }
}

fun <T> Flow<T>.launchCollect(
    owner: LifecycleOwner,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    collector: (T) -> Unit
): Job = owner.lifecycleScope.launch(dispatcher) {
    owner.repeatOnLifecycle(state) {
        collect { collector(it) }
    }
}

// ==================== Fragment Extensions ====================
/**
 * Launch a coroutine in the Fragment's lifecycleScope on Main dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleMainScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.Main) {
        action()
    }
/**
 * Launch a coroutine in the Fragment's lifecycleScope on IO dispatcher
 * Useful for database operations, file I/O, network calls
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleIOScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.IO) {
        action()
    }
/**
 * Launch a coroutine in the Fragment's lifecycleScope on Default dispatcher
 * Useful for CPU-intensive work
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleDefaultScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.Default) {
        action()
    }
/**
 * Launch a coroutine with custom dispatcher
 * @param dispatcher Custom coroutine dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleCustomScope(
    dispatcher: CoroutineDispatcher,
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    action()
}
/**
 * Launch a coroutine whose children run in a supervisorScope (still cancelled with the lifecycle)
 * Children failures won't cancel siblings or parent
 * @param dispatcher Coroutine dispatcher (default: Main)
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleSupervisorScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    supervisorScope { action() }
}
/**
 * Launch coroutine on IO thread and switch to Main for result
 * Common pattern: IO work → UI update
 * @param ioAction Background work on IO dispatcher
 * @param mainAction UI update on Main dispatcher (receives result from ioAction)
 * @return Job for the launched coroutine
 */
fun <T> Fragment.lifecycleIOToMain(
    ioAction: suspend CoroutineScope.() -> T,
    mainAction: suspend CoroutineScope.(T) -> Unit
): Job = lifecycleScope.launch(Dispatchers.IO) {
    val result = ioAction()
    withContext(Dispatchers.Main) {
        mainAction(result)
    }
}
/**
 * Launch coroutine with exception handling
 * @param dispatcher Coroutine dispatcher (default: Main)
 * @param onError Error handler
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun Fragment.lifecycleSafeScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    onError: (Throwable) -> Unit = {},
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
// ==================== Activity Extensions ====================
/**
 * Launch a coroutine in the Activity's lifecycleScope on Main dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleMainScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.Main) {
        action()
    }
/**
 * Launch a coroutine in the Activity's lifecycleScope on IO dispatcher
 * Useful for database operations, file I/O, network calls
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleIOScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.IO) {
        action()
    }
/**
 * Launch a coroutine in the Activity's lifecycleScope on Default dispatcher
 * Useful for CPU-intensive work
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleDefaultScope(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch(Dispatchers.Default) {
        action()
    }
/**
 * Launch a coroutine with custom dispatcher
 * @param dispatcher Custom coroutine dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleCustomScope(
    dispatcher: CoroutineDispatcher,
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    action()
}
/**
 * Launch a coroutine whose children run in a supervisorScope (still cancelled with the lifecycle)
 * Children failures won't cancel siblings or parent
 * @param dispatcher Coroutine dispatcher (default: Main)
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleSupervisorScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    supervisorScope { action() }
}
/**
 * Launch coroutine on IO thread and switch to Main for result
 * Common pattern: IO work → UI update
 * @param ioAction Background work on IO dispatcher
 * @param mainAction UI update on Main dispatcher (receives result from ioAction)
 * @return Job for the launched coroutine
 */
fun <T> FragmentActivity.lifecycleIOToMain(
    ioAction: suspend CoroutineScope.() -> T,
    mainAction: suspend CoroutineScope.(T) -> Unit
): Job = lifecycleScope.launch(Dispatchers.IO) {
    val result = ioAction()
    withContext(Dispatchers.Main) {
        mainAction(result)
    }
}
/**
 * Launch coroutine with exception handling
 * @param dispatcher Coroutine dispatcher (default: Main)
 * @param onError Error handler
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun FragmentActivity.lifecycleSafeScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    onError: (Throwable) -> Unit = {},
    action: suspend CoroutineScope.() -> Unit
): Job = lifecycleScope.launch(dispatcher) {
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
// ==================== Lifecycle State-Aware Extensions ====================
/**
 * Launch and repeat coroutine when Fragment reaches RESUMED state
 * Automatically cancels when lifecycle falls below RESUMED (e.g., onPause)
 * Best for UI updates, animations, location updates
 * @param action The coroutine action that repeats on RESUMED
 * @return Job for the launched coroutine
 */
fun Fragment.launchWhenResumed(lifecycleOwner: LifecycleOwner = this,action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            action()
        }
    }
/**
 * Launch and repeat coroutine when Fragment reaches STARTED state
 * Automatically cancels when lifecycle falls below STARTED (e.g., onStop)
 * Best for collecting flows, observing data
 * @param action The coroutine action that repeats on STARTED
 * @return Job for the launched coroutine
 */
fun Fragment.launchWhenStarted(action: suspend CoroutineScope.() -> Unit): Job =
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            action()
        }
    }
/**
 * Launch and repeat coroutine when Fragment reaches CREATED state
 * Automatically cancels when lifecycle falls below CREATED (e.g., onDestroy)
 * @param action The coroutine action that repeats on CREATED
 * @return Job for the launched coroutine
 */
fun Fragment.launchWhenCreated(action: suspend CoroutineScope.() -> Unit): Job =
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
            action()
        }
    }
/**
 * Collect Flow only when Fragment is at least RESUMED
 * Automatically stops collection when paused and restarts when resumed
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> Fragment.collectWhenResumed(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        flow.collect { action(it) }
    }
}
/**
 * Collect Flow only when Fragment is at least STARTED
 * Automatically stops collection when stopped and restarts when started
 * Recommended for most Flow collections in Fragments
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> Fragment.collectWhenStarted(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collect { action(it) }
    }
}
/**
 * Collect Flow with collectLatest only when Fragment is at least STARTED
 * Cancels previous collection if new value is emitted before completion
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> Fragment.collectLatestWhenStarted(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collectLatest { action(it) }
    }
}
// ==================== Activity Lifecycle State-Aware Extensions ====================
/**
 * Launch and repeat coroutine when Activity reaches RESUMED state
 * Automatically cancels when lifecycle falls below RESUMED (e.g., onPause)
 * Best for UI updates, animations, location updates
 * @param action The coroutine action that repeats on RESUMED
 * @return Job for the launched coroutine
 */
fun FragmentActivity.launchWhenResumed(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            action()
        }
    }
/**
 * Launch and repeat coroutine when Activity reaches STARTED state
 * Automatically cancels when lifecycle falls below STARTED (e.g., onStop)
 * Best for collecting flows, observing data
 * @param action The coroutine action that repeats on STARTED
 * @return Job for the launched coroutine
 */
fun FragmentActivity.launchWhenStarted(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            action()
        }
    }
/**
 * Launch and repeat coroutine when Activity reaches CREATED state
 * Automatically cancels when lifecycle falls below CREATED (e.g., onDestroy)
 * @param action The coroutine action that repeats on CREATED
 * @return Job for the launched coroutine
 */
fun FragmentActivity.launchWhenCreated(action: suspend CoroutineScope.() -> Unit): Job =
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            action()
        }
    }
/**
 * Collect Flow only when Activity is at least RESUMED
 * Automatically stops collection when paused and restarts when resumed
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> FragmentActivity.collectWhenResumed(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.RESUMED) {
        flow.collect { action(it) }
    }
}
/**
 * Collect Flow only when Activity is at least STARTED
 * Automatically stops collection when stopped and restarts when started
 * Recommended for most Flow collections in Activities
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> FragmentActivity.collectWhenStarted(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collect { action(it) }
    }
}
/**
 * Collect Flow with collectLatest only when Activity is at least STARTED
 * Cancels previous collection if new value is emitted before completion
 * @param flow The Flow to collect
 * @param action Action to perform for each emitted value
 * @return Job for the collection
 */
fun <T> FragmentActivity.collectLatestWhenStarted(
    flow: Flow<T>,
    action: suspend (T) -> Unit
): Job = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collectLatest { action(it) }
    }
}

// ==================== ViewModel Extensions ====================
/**
 * Launch a coroutine in the ViewModel's viewModelScope on IO dispatcher
 * Useful for database operations, file I/O, network calls
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchIo(action: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(Dispatchers.IO) {
        action()
    }

/**
 * Launch a coroutine in the ViewModel's viewModelScope on Main dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchMain(action: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(Dispatchers.Main) {
        action()
    }

/**
 * Launch a coroutine in the ViewModel's viewModelScope on Default dispatcher
 * Useful for CPU-intensive work
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchDefault(action: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch(Dispatchers.Default) {
        action()
    }

/**
 * Launch a coroutine with custom dispatcher
 * @param dispatcher Custom coroutine dispatcher
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchOn(
    dispatcher: CoroutineDispatcher,
    action: suspend CoroutineScope.() -> Unit
): Job = viewModelScope.launch(dispatcher) {
    action()
}

/**
 * Launch coroutine on IO thread and switch to Main for result
 * Common pattern: IO work → UI update
 * @param ioAction Background work on IO dispatcher
 * @param mainAction UI update on Main dispatcher (receives result from ioAction)
 * @return Job for the launched coroutine
 */
fun <T> ViewModel.launchIoToMain(
    ioAction: suspend CoroutineScope.() -> T,
    mainAction: suspend CoroutineScope.(T) -> Unit
): Job = viewModelScope.launch(Dispatchers.IO) {
    val result = ioAction()
    withContext(Dispatchers.Main) {
        mainAction(result)
    }
}

/**
 * Launch coroutine with exception handling
 * @param dispatcher Coroutine dispatcher (default: IO)
 * @param onError Error handler
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchSafe(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    onError: (Throwable) -> Unit = {},
    action: suspend CoroutineScope.() -> Unit
): Job = viewModelScope.launch(dispatcher) {
    try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}

/**
 * Launch a coroutine whose children run in a supervisorScope (still cancelled with the lifecycle)
 * Children failures won't cancel siblings or parent
 * @param dispatcher Coroutine dispatcher (default: IO)
 * @param action The coroutine action to perform
 * @return Job for the launched coroutine
 */
fun ViewModel.launchSupervisor(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    action: suspend CoroutineScope.() -> Unit
): Job = viewModelScope.launch(dispatcher) {
    supervisorScope { action() }
}

// ==================== Context Switching Extensions ====================
/**
 * Switch to IO dispatcher and execute block
 * Use for database operations, file I/O, network calls
 * @param block The suspend block to execute on IO
 * @return Result of the block
 */
suspend fun <T> withIo(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.IO) { block() }

/**
 * Switch to Main dispatcher and execute block
 * Use for UI updates
 * @param block The suspend block to execute on Main
 * @return Result of the block
 */
suspend fun <T> withMain(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.Main) { block() }

/**
 * Switch to Default dispatcher and execute block
 * Use for CPU-intensive operations
 * @param block The suspend block to execute on Default
 * @return Result of the block
 */
suspend fun <T> withDefault(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.Default) { block() }

/**
 * Switch to Unconfined dispatcher and execute block
 * Starts in caller thread, resumes in whatever thread the suspending function resumes in
 * @param block The suspend block to execute
 * @return Result of the block
 */
suspend fun <T> withUnconfined(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.Unconfined) { block() }

/**
 * Execute IO operation and return result to current context
 * Common pattern: fetch data on IO, continue on current thread
 * @param block The suspend block to execute on IO
 * @return Result of the block
 */
suspend fun <T> fetchOnIo(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.IO) { block() }

/**
 * Execute block with exception handling
 * @param onError Error handler (optional)
 * @param block The suspend block to execute
 * @return Result of the block or null if exception occurred
 */
suspend fun <T> withSafe(
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> T
): T? = try {
    withContext(Dispatchers.IO) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}

/**
 * Execute block on IO with exception handling
 * @param onError Error handler (optional)
 * @param block The suspend block to execute on IO
 * @return Result of the block or null if exception occurred
 */
suspend fun <T> withIoSafe(
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> T
): T? = try {
    withContext(Dispatchers.IO) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}

/**
 * Execute block on Main with exception handling
 * @param onError Error handler (optional)
 * @param block The suspend block to execute on Main
 * @return Result of the block or null if exception occurred
 */
suspend fun <T> withMainSafe(
    onError: (Throwable) -> Unit = {},
    block: suspend CoroutineScope.() -> T
): T? = try {
    withContext(Dispatchers.Main) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    onError(e)
    null
}