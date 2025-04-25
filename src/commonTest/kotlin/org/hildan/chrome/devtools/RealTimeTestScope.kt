package org.hildan.chrome.devtools

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes

interface RealTimeTestScope : CoroutineScope {
    val backgroundScope: CoroutineScope
}

/**
 * Provides the same facilities as [runTest] but without delay skipping.
 */
fun runTestWithRealTime(
    nestedContext: CoroutineContext = Dispatchers.Default,
    block: suspend RealTimeTestScope.() -> Unit,
) = runTest(timeout = 3.minutes) { // increased timeout for local server setup
    val testScopeBackground = backgroundScope + CoroutineExceptionHandler { _, e ->
        println("Error in background scope: $e")
    }
    withContext(nestedContext) {
        val regularScope = this
        val scopeWithBackground = object : RealTimeTestScope, CoroutineScope by regularScope {
            override val backgroundScope: CoroutineScope = testScopeBackground
        }
        scopeWithBackground.block()
    }
}
