package org.hildan.chrome.devtools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

interface RealTimeTestScope : CoroutineScope {
    val backgroundScope: CoroutineScope
}

/**
 * Provides the same facilities as [runTest] but without delay skipping.
 */
fun runTestWithRealTime(block: suspend RealTimeTestScope.() -> Unit) = runTest {
    val testScopeBackground = backgroundScope
    withContext(Dispatchers.Default) {
        val regularScope = this
        val scopeWithBackground = object : RealTimeTestScope, CoroutineScope by regularScope {
            override val backgroundScope: CoroutineScope = testScopeBackground
        }
        scopeWithBackground.block()
    }
}
