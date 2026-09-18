package com.wmt.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps the Main dispatcher for a test one, so a ViewModel launching into viewModelScope
 * can be driven from a plain JVM test. Unconfined so work started in init has already run
 * by the time the test makes its first assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {

    private val dispatcher = UnconfinedTestDispatcher()

    /**
     * Pass this to runTest so the test body and the ViewModel share one clock. Without
     * it a debounce would sit on a scheduler the test cannot advance.
     */
    val scheduler get() = dispatcher.scheduler

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
