package org.hildan.chrome.devtools.protocol.config

interface ChromeDPConfig {
    /**
     * A way to pre- or post-process JSON elements to compensate when debugger servers deviate from the protocol, or
     * when the protocol definitions don't match reality.
     */
    val jsonPatcher: ChromeDPJsonPatcher

    companion object {
        /**
         * Creates a new [ChromeDPConfig] configured with the given [configure] function.
         */
        operator fun invoke(configure: ChromeDPConfigBuilder.() -> Unit = {}): ChromeDPConfig =
            ChromeDPConfigBuilder().apply(configure).build()
    }
}


@RequiresOptIn("JSON transformations are experimental and require opt-in")
annotation class ExperimentalJsonTransformations

class ChromeDPConfigBuilder internal constructor() {

    private val jsonPatcherBuilder: ChromeDPJsonPatcherBuilder = ChromeDPJsonPatcherBuilder()

    /**
     * Configures the [ChromeDPJsonPatcher] to pre- or post-process JSON elements to compensate when debugger servers deviate
     * from the protocol, or when the protocol definitions don't match reality.
     */
    @ExperimentalJsonTransformations
    fun jsonPatcher(configure: ChromeDPJsonPatcherBuilder.() -> Unit) {
        jsonPatcherBuilder.configure()
    }

    internal fun build(): ChromeDPConfig = object : ChromeDPConfig {
        override val jsonPatcher: ChromeDPJsonPatcher = jsonPatcherBuilder.build()
    }
}
