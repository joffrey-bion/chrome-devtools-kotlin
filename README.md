# Chrome Devtools Kotlin

[![Maven central version](https://img.shields.io/maven-central/v/org.hildan.chrome/chrome-devtools-kotlin.svg)](http://mvnrepository.com/artifact/org.hildan.chrome/chrome-devtools-kotlin)
[![Github Build](https://img.shields.io/github/actions/workflow/status/joffrey-bion/chrome-devtools-kotlin/build.yml?branch=main)](https://github.com/joffrey-bion/chrome-devtools-kotlin/actions/workflows/build.yml)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/joffrey-bion/chrome-devtools-kotlin/blob/master/LICENSE)

An asynchronous coroutine-based Kotlin client for the [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/).

Currently, this client is a JVM library, but it can easily become multiplatform, as it is not tied to any JVM-specific API.

## Protocol version & Code generation

Part of this client is generated based on information from the "latest" (a.k.a 
["tip-of-tree"](https://chromedevtools.github.io/devtools-protocol/tot/)) JSON descriptors found in the 
[ChromeDevTools/devtools-protocol](https://github.com/ChromeDevTools/devtools-protocol/tree/master/json) repository.
All the domains' commands and events defined in these protocol descriptors are therefore available in
`chrome-devtools-kotlin`, as well as their doc and deprecated/experimental status. 

The protocol definitions are automatically updated daily, but releases of `chrome-devtools-kotlin` are still manual.
If you're missing some APIs or updates, don't hesitate to open an issue to request a new release with updated protocol.

You can find the protocol version used by `chrome-devtools-kotlin` after the `-` in the version number.
For instance, version `1.3.0-861504` of `chrome-devtools-kotlin` was built using version `861504` of the Chrome 
DevTools Protocol (this is technically the Chromium revision, but it gives the tip-of-tree version number of the 
protocol).

## Concepts

### Domains

The Chrome Devtools Protocol defines **_domains_** that expose some commands and events.
They are subsets of the protocol's API.
You can find the list and documentation of all domains in the 
[protocol's web page](https://chromedevtools.github.io/devtools-protocol/).

This library defines a type for each domain (e.g. `PageDomain`, `StorageDomain`...), which exposes:

* a `suspend` method for each command, accepting a *request* type and returning a *response* type,
  respectively containing the input and output parameters defined in the protocol for that command.
* additional `suspend` functions for each command with a more convenient signature.
* a method for each type of event, returning a `Flow` of this particular event type
* an `events()` method, returning a `Flow` of all events of the domain

The domains usually also expose an `enable()` command which is required to enable events.
Without calling it, you will receive no events in the `Flow` subscriptions.

### Targets

Clients can interact with different parts of Chrome such as pages (tabs), service workers, and extensions. 
These parts are called **_targets_**.
The browser itself is also a target.

Each type of target supports a different subset of the domains defined in the protocol.

### Sessions

The protocol requires you to attach to a target in order to interact with it.
Attaching to a target opens a target **_session_** of the relevant type, such as `ChromeBrowserSession` or 
`ChromePageSession`.

When connecting to Chrome, a browser target is automatically attached, thus you obtain a `ChromeBrowserSession`.
You can then use this session to attach to other targets (child targets), such as pages (tabs).

Each of the supported domains are defined as properties of the session type, which provides a type-safe way to know
if the attached target supports a given domain.
For instance, `ChromePageSession.dom` gives access to the DOM domain in `this` page session,
which allows to issue commands and listen to DOM events.

> Note: The supported set of domains for each target type is not clearly defined by the protocol, so I had to
> extract this information from
> [Chromium's source code itself](https://source.chromium.org/search?q=%22session-%3ECreateAndAddHandler%22%20f:devtools&ss=chromium)
> and define my own extra definition file: [target_types.json](./protocol/target_types.json).
> 
> Because of this, there might be some missing domains on some session types at some point in time that require
> manual adjustment.
> If this is the case, use the `ChromePageSession.unsafe()` method on the session object to get full access to all domains
> (also, please open an issue so I can fix the missing domain).

## Usage

### Connecting to the browser

You first need to have a browser running, exposing a debugger server.
For instance, you can start a headless chrome with the following docker command, 
which exposes the debugger server at `http://localhost:9222`:

```
docker container run -d -p 9222:9222 zenika/alpine-chrome --no-sandbox --remote-debugging-address=0.0.0.0 --remote-debugging-port=9222 about:blank
```

The starting point of this library is the `ChromeDPClient`, which is created using the 
"remote-debugging" URL that was passed to Chrome.
You can then open a `webSocket()` to the browser debugger, which automatically attaches to the browser target and 
starts a "browser session":

```kotlin
val client = ChromeDPClient("http://localhost:9222")
val browserSession: ChromeBrowserSession = client.webSocket()
```

The `ChromeBrowserSession` is not attached to a page target (tab), so you can only interact with 
a subset of the protocol's API.
All the supported domains for the browser sessions are available as properties.
Here are a couple examples using the `target` and `storage` domains:

```kotlin
val targets = browserSession.target.getTargets()
```

```kotlin
browserSession.storage.clearCookies()
```

### Connecting to targets

The browser is a target in itself, but we're usually interested in more useful targets, such as pages (tabs).
Once you have your browser session, you can use it to create targets and attach to them.
Here is an example to create a new page target and attach to it:

```kotlin
val browserSession = ChromeDPClient("http://localhost:9222").webSocket()
val pageSession = browserSession.attachToNewPage("http://example.com")

// This page session has access to many useful protocol domains (e.g. dom, page...)
val doc = pageSession.dom.getDocument().root
val base64Img = pageSession.page.captureScreenshot {
    format = ScreenshotFormat.jpeg
    quality = 80
}
```

### High level extensions

In addition to the generated domain commands and events, some extensions are provided for higher-level functionality.
Here are some of them:

* `DOMDomain.findNodeBySelector(selector: String): NodeId?`: finds a node using a selector query
* `ChromePageSession.clickOnElement(selector: String, clickDurationMillis, mouseButton)`: finds a node using a selector 
  query and simulates a click on it
* `ChromePageSession.navigateAndAwaitPageLoad(url: String)`: navigates and also waits for the next `frameStoppedLoading` event
* `PageDomain.captureScreenshotToFile(outputFile: Path, request: CaptureScreenshotRequest = ...)`
* `Runtime.evaluateJs(js: String): T?`: evaluates JS and returns the result
  (uses Kotlinx serialization to deserialize JS results)

#### Examples

Example usage of `Runtime.evaluateJs(js: String)`:

```kotlin
@Serializable
data class Person(val firstName: String, val lastName: String)

val pageSession = ChromeDPClient().webSocket().attachToNewPage("http://google.com")

val evaluatedInt = pageSession.runtime.evaluateJs<Int>("42")
assertEquals(42, evaluatedInt)

val evaluatedPerson = pageSession.runtime.evaluateJs<Person>("""eval({firstName: "Bob", lastName: "Lee Swagger"})""")
assertEquals(Person("Bob", "Lee Swagger"), evaluatedPerson)
```

Note that the deserialization here uses [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization), 
which requires annotating the deserialized classes with `@Serializable` _and_ using the corresponding compiler plugin.

### Troubleshooting

> *Host header is specified and is not an IP address or localhost*

Sometimes this error also appears in the form of an HTTP 500.

Chrome doesn't accept a `Host` header that is not an IP nor `localhost`, but in some environments it might be hard
to provide this (e.g. docker services in a docker swarm, communicating using service names).

To work around this problem, simply set `overrideHostHeader` to true when creating `ChromeDPClient`.
This overrides the `Host` header to "localhost" in the HTTP requests to the Chrome debugger to make it happy, and
also replaces the host in subsequent web socket URLs (returned by Chrome) by the initial host provided in
`remoteDebugUrl`.
This is necessary because Chrome uses the `Host` header to build these URLs, and it would be incorrect to keep this.

## Add the dependency

This library is available on Maven Central.

Using Gradle:

```
implementation("org.hildan.chrome:chrome-devtools-kotlin:$version")
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

If you're looking for Chrome Devtools Protocol clients _in Kotlin_, I have only found one other so far, 
[chrome-reactive-kotlin](https://github.com/wendigo/chrome-reactive-kotlin).
This is the reactive equivalent of this project.
The main differences are the following:

* it uses a _reactive_ API (as opposed to coroutines and suspend functions)
* it doesn't distinguish target types, and thus doesn't restrict the available domains at compile time
  (it's the equivalent of always using `unsafe()` in `chrome-devtools-kotlin`)

I needed a coroutine-based API instead and more type-safety, which is how this `chrome-devtools-kotlin` 
project was born.

You can find alternative Chrome DevTools libraries in other languages in this
[awesome list](https://github.com/ChromeDevTools/awesome-chrome-devtools).

## Credits

Special thanks to @wendigo and his project [chrome-reactive-kotlin](https://github.com/wendigo/chrome-reactive-kotlin)
which inspired the creation of this project.
