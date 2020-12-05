# Chrome Devtools Kotlin

[![Maven central version](https://img.shields.io/maven-central/v/org.hildan.chrome/chrome-devtools-kotlin.svg)](http://mvnrepository.com/artifact/org.hildan.chrome/chrome-devtools-kotlin)
[![Bintray Download](https://img.shields.io/bintray/v/joffrey-bion/maven/chrome-devtools-kotlin)](https://bintray.com/joffrey-bion/maven/chrome-devtools-kotlin/_latestVersion)
[![Github Build](https://img.shields.io/github/workflow/status/joffrey-bion/chrome-devtools-kotlin/CI%20Build?label=build&logo=github)](https://github.com/joffrey-bion/chrome-devtools-kotlin/actions?query=workflow%3A%22CI+Build%22)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/joffrey-bion/chrome-devtools-kotlin/blob/master/LICENSE)

An asynchronous coroutine-based Kotlin client for the [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/).

Currently, this client only runs on the JVM, but this library can easily become multiplatform, as it is not tied to any 
JVM-specific API.

## Protocol version & Code generation

This client is partly generated based on information from the "latest" JSON descriptors found in the 
[ChromeDevTools/devtools-protocol](https://github.com/ChromeDevTools/devtools-protocol/tree/master/json) repository.
All the domains' commands and events defined in these protocol descriptors are therefore available in
`chrome-devtools-kotlin`, as well as their doc and deprecated/experimental status. 

The protocol definitions are automatically updated daily.
If you're missing some APIs or updates, don't hesitate to open an issue to request a new release with updated protocol.

## Usage

### About the API

The Chrome Devtools Protocol defines "domains" that expose some commands and events.
You can find the list and documentation of all domains in the 
[protocol's web page](https://chromedevtools.github.io/devtools-protocol/).

The protocol requires to attach to a "target" (via a web socket connection) in order to interact with it.
Targets can be the browser itself, a browser tab, a service worker, etc.
Each type of target supports only a subset of the available domains.

This library exposes the protocol's API via session objects, such as `ChromeBrowserSession` or `ChromePageSession`. 
They represent web socket sessions with attached targets, for different types of target.
The supported domains are defined as properties on these session objects, so accessing these properties is a type-safe
way to know which domains can actually be used.

> Note: The supported set of domains for each target type is not clearly defined by the protocol, so there might be some
> missing domains on some target types.
> If this is the case, use the `unsafe()` method on the session object to get full access to all domains
> (also, please open an issue so I can fix the missing domain).

Each domain type exposes:

* a `suspend` method for each command, accepting a *request* type and returning a *response* type,
both of which are defined based on the input and output parameters defined in the protocol for that command.
* a method for each type of event, returning a `Flow` of this particular event type
* an `events()` method, returning a `Flow` of all events of the domain

The domains usually expose an `enable()` command which is required to enable the emission of events.

### Connecting to the browser

You first need to have a browser running, exposing a debugger server.
For instance, you can start a headless chrome with the following docker command, which exposes the debugger server at `http://localhost:9222`:

```
docker container run -d -p 9222:9222 zenika/alpine-chrome --no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank
```

The starting point of this library is the `ChromeDPClient`, which is created using the "remote-debugging" URL that was passed to Chrome.
You can then open a `webSocket()` to the browser debugger, which starts a "browser session":

```kotlin
val client = ChromeDPClient("http://localhost:9222")
val browserSession: ChromeBrowserSession = client.webSocket()
```

The `ChromeBrowserSession` is not attached to a page target, so you can only interact with a subset of the protocol's API.
All the supported domains for the browser sessions are available as properties.
Here are a couple examples using the `target` and `storage` domains:

```kotlin
val targets = browserSession.target.getTargets()
browserSession.storage.clearCookies(ClearCookiesRequest())
```

### Connecting to targets

The browser is a target in itself, but we're usually interested in more useful targets, such as pages (tabs).
Once you have your browser session, you can use it to create targets and attach to them.
Here is an example to create a new page target and attach to it:

```kotlin
val browserSession = ChromeDPClient("http://localhost:9222").webSocket()
val pageSession = browserSession.attachToNewPage("http://example.com")

// This page session has access to many useful protocol domains
val doc = pageSession.dom.getDocument(GetDocumentRequest()).root
val base64Img = pageSession.page.captureScreenshot(CaptureScreenshotRequest(format = "jpg", quality = 80))
```

### High level extensions

In addition to the generated domain commands and events, some extensions are provided to provide higher-level 
functionality.

For instance, `Runtime.evaluateJs(js: String)`:

```kotlin
@Serializable
data class Person(val firstName: String, val lastName: String)

val pageSession = ChromeDPClient().webSocket().attachToNewPage("http://google.com")

val evaluatedInt = page.runtime.evaluateJs<Int>("42")
assertEquals(42, evaluatedInt)

val evaluatedPerson = page.runtime.evaluateJs<Person>("""eval({firstName: "Bob", lastName: "Lee Swagger"})""")
assertEquals(Person("Bob", "Lee Swagger"), evaluatedPerson)
```

### Troubleshooting

> Host header is specified and is not an IP address or localhost

Sometimes this error also appears in the form of an HTTP 500.

Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments it might be hard
to provide this (e.g. docker services in a docker swarm, communicating using service names).

To work around this problem, simply set `overrideHostHeader` to true when creating `ChromeDPClient`.
This overrides the `Host` header to "localhost" in the HTTP requests to the Chrome debugger to make it happy, and
also replaces the host in subsequent web socket URLs (returned by Chrome) by the initial host provided in
`remoteDebugUrl`.
This is necessary because Chrome uses the `Host` header to build these URLs, and it would be incorrect to keep this.

## Add the dependency

Using Gradle:

```
compile("org.hildan.chrome:chrome-devtools-kotlin:$version")
```

Using Maven:

```
<dependency>
  <groupId>org.hildan.chrome</groupId>
  <artifactId>chrome-devtools-kotlin</artifactId>
  <version>$VERSION</version>
</dependency>
```

## License

This project is distributed under the MIT license.

## Alternatives

You can find a some Chrome-Devtools-related libraries in this
[awesome list](https://github.com/ChromeDevTools/awesome-chrome-devtools).

If you're looking for Chrome Devtools Protocol clients in Kotlin, I have only found one so far, 
[chrome-reactive-kotlin](https://github.com/wendigo/chrome-reactive-kotlin).
It is useful if you're looking for a reactive API.

I needed a coroutine-based API instead, which is how this `chrome-devtools-kotlin` project was born.

## Credits

Special thanks to @wendigo and his project [chrome-reactive-kotlin](https://github.com/wendigo/chrome-reactive-kotlin)
which inspired the creation of this project.
