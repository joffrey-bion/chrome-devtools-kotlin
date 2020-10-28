package org.hildan.chrome.devtools.protocol

/**
 * This annotation is used on DevTools Protocol APIs and types that are marked as experimental in the protocol itself.
 */
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPEALIAS
)
annotation class ExperimentalChromeApi
