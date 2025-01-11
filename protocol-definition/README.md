# Protocol definitions

This README describes how the files in this directory are updated or maintained.

## Automatic protocol definition updates

The `browser_protocol.json` and `js_protocol.json` files are the official protocol definitions from the "latest" (a.k.a
["tip-of-tree"](https://chromedevtools.github.io/devtools-protocol/tot/)) version of the protocol.

The `version.txt` file contains the Chromium revision that produced these JSON protocol definitions.
It's a monotonic version number referring to the chromium master commit position.

These files are automatically and regularly updated based on the state of the
[ChromeDevTools/devtools-protocol](https://github.com/ChromeDevTools/devtools-protocol) repository, via the
[update-protocol.yml](../.github/workflows/update-protocol.yml) GitHub Actions workflow:

* The JSON definitions are taken as-is from the [json](https://github.com/ChromeDevTools/devtools-protocol/tree/master/json) directory.
* The Chromium revision is extracted from the npm version defined in the 
[package.json](https://github.com/ChromeDevTools/devtools-protocol/blob/master/package.json).

For more details, see the corresponding [Gradle task](..%2FbuildSrc%2Fsrc%2Fmain%2Fkotlin%2FUpdateProtocolDefinitionsTask.kt) from `buildSrc`.
This task can also be run locally using `./gradlew updateProtocolDefinitions`.

## Manual updates of target types

The protocol files are missing important information that is necessary for Chrome DevTools Kotlin's codegen:

* the existing target types
* the domains supported by each target type

This information has to be inferred from the Chromium sources, and it is stored in the `target_types.json` so it can be
consumed by the code generator.

This file is manually maintained at the moment, as the process is a bit tricky to automate.

### Background information

There are 2 kinds of "target types":

* the **"protocol" target types** are the ones that are used in the protocol for the
  [TargetInfo.type](https://chromedevtools.github.io/devtools-protocol/tot/Target/#type-TargetInfo) field.
  They define real-life types of targets (`page`, `iframe`, `worker`), but they don't all differ in their capabilities on the server.
  For this reason, they don't correspond one-to-one to Kotlin interfaces in this library.
* the **"Chromium" target types** (or "DevTools agent host" types) directly relate to Chromium's implementation,
  which means they represent the target types based on their capabilities (the domains they support).
  This is why they are represented by `*Target` interfaces in the Kotlin code generation of this library.
  Each of those can handle one or more "protocol" target types. For example, the Chromium target type "RenderFrame" is the
  implementation handling the `page`, `iframe` and `webview` target types from the protocol.

The list of "protocol" target types is effectively defined by the set of `const char DevToolsAgentHost::kTypeX[]`
[constants in Chromium's source](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc;l=126-140).

The DevTools agent host types are defined in the `<target_type_name>_devtools_agent_host.cc` source files.

### Update procedure

Here are the steps performed to get the information from the Chromium source code into `target_types.json`:

1. Discover all Chromium target types by looking at [files named `*_devtools_agent_host.cc`](https://source.chromium.org/search?q=f:devtools_agent_host.cc).
   Each of them contains a subclass of `DevToolsAgentHostImpl`, named after the filename.
   Almost all of those should have a corresponding entry in `target_types.json` (but read on for some exceptions).

2. The `::GetType()` method of the agent host type returns the protocol target type represented by this 
   Chromium target type. Sometimes the code can return different values. The list of all possible return values should
   added to `supportedCdpTargets`.
   If this `GetType` method just delegates to something else, ignore this whole agent host type (it's probably just a wrapper).

3. You can check the [list of constants](https://source.chromium.org/chromium/chromium/src/+/main:content/browser/devtools/devtools_agent_host_impl.cc?q=%22const%20char%20DevToolsAgentHost::kType%22)
   defining the protocol target types, and verify you got all of them covered (except maybe "other"). 

4. Search for [domain handler declarations in Chromium's source](https://source.chromium.org/search?q=%22session-%3ECreateAndAddHandler%22%20f:devtools&ss=chromium).
   Each `session->CreateAndAddHandler<protocol::DomainHandler>();` match in a `*_devtools_agent_host.cc` file represents
   a domain supported by this target type (watch out for macros that only include domains conditionally).
   The domain name is the prefix before `Handler` in the handler type.
   Add all supported domains to the `supportedDomainsInChromium` list of the target type in `target_types.json`.

5. The integration test for the schema should also detect additional domains supported by the Page session
   (`RenderFrame` host agent type). Make sure the missing supported domains are added to this target type in
   `target_types.json`, as the `additionalSupportedDomains` property.
   Note: as of now, it's still unclear *how* these additional domains are supported. Their implementation seems to be
   in a different part of the code, but I'm not sure where they are linked to the agent host types.

6. Each target type also gets a custom name for use in Kotlin code. This name usually matches the agent host type name
   or the main protocol target type supported by the Chromium target type. Pragmatically, `Page` was used instead of
   `RenderFrame` for clarity.
