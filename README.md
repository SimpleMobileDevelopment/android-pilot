# Pilot

**AI-powered natural language integration testing for Android.**

[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)]()
[![Min SDK](https://img.shields.io/badge/minSdk-29-green.svg)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-orange.svg)]()

Pilot lets you write Android UI tests in plain English. Define test routes in YAML, and Pilot uses Claude AI to interpret your instructions, read the screen, and execute actions against your Jetpack Compose UI — no brittle selectors, no flaky XPath queries.

```yaml
route: Login flow

steps:
  - step: Verify login screen loads
    instructions:
      - verify: A login screen is visible with username and password fields and a Log In button

  - step: Enter credentials and log in
    instructions:
      - action: Tap the username input field
      - action: Type 'testuser' into the username field
      - action: Tap the password input field
      - action: Type 'password123' into the password field
      - action: Tap the 'Log In' button

  - step: Verify navigation to list
    timeout: 10
    instructions:
      - verify: A list of items is visible on screen
```

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Writing Routes (YAML)](#writing-routes-yaml)
- [Writing Tests](#writing-tests)
- [Route Builder Tool](#route-builder-tool)
- [Architecture](#architecture)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- **Natural language instructions** — describe what you want to test in plain English
- **YAML-based routes** — define test flows in human-readable YAML files
- **AI-powered execution** — Claude interprets instructions and maps them to UI actions
- **Vision support** — sends screenshots to Claude for visual verification
- **Auto-retry verification** — failed assertions are retried with configurable timeouts (default 15s)
- **Value remembering** — extract and interpolate values across steps with `{key}` syntax
- **Rich Compose semantics** — reads test tags, text, positions, and capabilities from the Compose semantics tree
- **Pluggable reporting** — Logcat, file-based, or composite reporters
- **Kotlin DSL** — programmatically build routes with a type-safe DSL as an alternative to YAML

---

## Requirements

| Requirement | Version |
|---|---|
| Android Min SDK | **29** (Android 10+) |
| JDK | **17** |
| Gradle | **8.x+** |
| Jetpack Compose | Required |
| Anthropic API Key | Required ([get one here](https://console.anthropic.com/)) |

---

## Installation

### Gradle (Maven Central)

Add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    androidTestImplementation("co.pilot:pilot-android:0.1.0")
}
```

### JitPack

Add the JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    androidTestImplementation("com.github.YOUR_ORG:pilot-android:0.1.0")
}
```

### Local Development

Clone the repo and publish to your local Maven:

```bash
git clone https://github.com/YOUR_ORG/pilot-android.git
cd pilot-android
./gradlew publishToMavenLocal
```

Then in your app, add `mavenLocal()` to your repositories and use:

```kotlin
androidTestImplementation("co.pilot:pilot-android:0.1.0")
```

> **Note:** ProGuard/R8 consumer rules are bundled with the library — no extra configuration needed.

---

## Quick Start

### 1. Add the dependency

See [Installation](#installation) above.

### 2. Create a YAML route

Create the file `app/src/androidTest/assets/routes/landing-screen.yaml`:

```yaml
route: Verify landing screen

steps:
  - step: Check landing screen content
    instructions:
      - verify: The landing screen is visible
      - verify: A 'Get Started' button is displayed
```

### 3. Write the test

```kotlin
@RunWith(AndroidJUnit4::class)
class LandingScreenRouteTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val config = Pilot.configure {
        apiKey = BuildConfig.PILOT_API_KEY  // or set ROUTES_AI_API_KEY env var
    }

    @get:Rule
    val routeRule = RouteTestRule(
        composeTestRule = composeTestRule,
        aiBackend = config.buildBackend(),
        outputDir = config.outputDir,  // optional: for file-based reports
    )

    @Test
    fun landingScreen() = runBlocking {
        val route = YamlRouteParser.parse(
            composeTestRule.activity.assets.open("routes/landing-screen.yaml"),
            sourceName = "landing-screen.yaml"
        )
        routeRule.runRoute(route)
    }
}
```

### 4. Run the test

```bash
./gradlew app:connectedAndroidTest
```

---

## Configuration

Configure Pilot via the `Pilot.configure {}` block:

```kotlin
val config = Pilot.configure {
    apiKey = "sk-ant-..."           // Required: your Anthropic API key
    model = "claude-sonnet-4-20250514"  // Optional: Claude model (default shown)
    maxTokens = 1024                // Optional: max tokens per AI request
    outputDir = File("/sdcard/pilot-reports")  // Optional: file report output directory
}
```

### Configuration Options

| Property | Type | Default | Description |
|---|---|---|---|
| `apiKey` | `String` | `""` (required) | Your Anthropic Claude API key |
| `model` | `String` | `claude-sonnet-4-20250514` | Claude model to use |
| `maxTokens` | `Int` | `1024` | Maximum tokens per AI request |
| `outputDir` | `File?` | `null` | Directory for file-based reports (enables `FileReporter`) |

### API Key

You can provide the API key in several ways:

1. **Directly in code** (for local development):
   ```kotlin
   Pilot.configure { apiKey = "sk-ant-..." }
   ```

2. **Via BuildConfig** (recommended):
   Add to `local.properties`:
   ```properties
   pilot.apiKey=sk-ant-...
   ```
   Then in your `build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           buildConfigField("String", "PILOT_API_KEY", "\"${project.findProperty("pilot.apiKey")}\"")
       }
   }
   ```

3. **Via environment variable**:
   ```bash
   export ROUTES_AI_API_KEY=sk-ant-...
   ```

> **Important:** Never commit your API key to source control. Use `local.properties` or environment variables.

---

## Writing Routes (YAML)

### Structure

A route file has two top-level keys:

```yaml
route: <route name>        # Required: human-readable name
steps:                         # Required: list of steps
  - step: <step name>          # Required: human-readable step name
    timeout: <seconds>         # Optional: verification retry timeout (default: 15s)
    instructions:              # Required: list of instructions
      - action: <description>  # Perform a UI action
      - verify: <description>  # Assert something about the screen
      - remember:              # Extract and store a value
          key: <key_name>
          description: <what to extract>
```

### Instruction Types

#### `action`

Tells Pilot to perform a UI interaction. The AI determines the correct element and gesture.

```yaml
- action: Tap the 'Login' button
- action: Type 'user@example.com' into the email field
- action: Scroll down to find the 'Settings' option
- action: Swipe left on the notification card
```

**Supported actions:** tap, type text, scroll (up/down/left/right), swipe (up/down/left/right), wait for element.

#### `verify`

Tells Pilot to assert something about the current screen state.

```yaml
- verify: The login form is visible with email and password fields
- verify: An error message says 'Invalid credentials'
- verify: The user's profile picture is displayed
```

If a verification fails, Pilot automatically retries by polling for screen changes until the step's `timeout` (default 15 seconds) is reached.

#### `remember`

Extracts a value from the screen and stores it for later use with `{key}` interpolation.

```yaml
- remember:
    key: email
    description: The email address shown in the profile header

# Use it later in the same route:
- verify: The confirmation screen displays {email}
```

### Step Timeout

Override the default 15-second verification timeout per step:

```yaml
- step: Wait for slow network response
  timeout: 30
  instructions:
    - verify: The search results are displayed
```

### Complete Example

```yaml
route: Complete test route

steps:
  - step: First step
    instructions:
      - action: Tap the login button
      - verify: Login screen is visible

  - step: Second step
    instructions:
      - action: Type 'user@example.com' into the email field
      - remember:
          key: email
          description: The email address entered in the field
      - verify: The email field shows {email}
```

---

## Writing Tests

### Using RouteTestRule

`RouteTestRule` is a JUnit 4 `TestRule` that sets up the Pilot runtime (screen reader, action executor, reporter) and provides the `runRoute` method.

```kotlin
@get:Rule
val routeRule = RouteTestRule(
    composeTestRule = composeTestRule,  // Your ComposeTestRule
    aiBackend = config.buildBackend(), // AI backend from Pilot.configure
    outputDir = config.outputDir,      // Optional: enables file reporting
)
```

### Loading Routes from YAML

**From assets:**

```kotlin
val routes = YamlRouteLoader.loadFromAssets(
    assets = composeTestRule.activity.assets,
    path = "routes"  // default asset subdirectory
)

// Run all routes
for (route in routes) {
    routeRule.runRoute(route)
}
```

**From a directory:**

```kotlin
val routes = YamlRouteLoader.loadFromDirectory(File("/path/to/routes"))
```

**From a single file:**

```kotlin
val route = YamlRouteParser.parse(
    inputStream,
    sourceName = "login.yaml"
)
routeRule.runRoute(route)
```

### Using the Kotlin DSL

Build routes programmatically instead of YAML:

```kotlin
@Test
fun loginRoute() = runBlocking {
    routeRule.runRoute("Login flow") {
        step("Open login") {
            verify("The landing screen is visible")
            action("Tap the 'Login' button")
        }

        step("Enter credentials") {
            action("Type 'user@example.com' into the email field")
            action("Type 'password123' into the password field")
            action("Tap the 'Submit' button")
        }

        step("Verify success") {
            timeout = 30
            verify("The home screen is displayed with a welcome message")
        }
    }
}
```

### DSL Reference

| Function | Description |
|---|---|
| `route(name) { }` | Create a route |
| `step(name) { }` | Add a step to the route |
| `action(description)` | Add an action instruction |
| `verify(description)` | Add a verification instruction |
| `remember(key, description)` | Add a remember instruction |
| `timeout = seconds` | Set step verification timeout |

### Reporting

Pilot includes built-in reporters:

- **LogcatReporter** (default) — logs route progress to Logcat via Timber
- **FileReporter** — writes structured reports to `outputDir`
- **CompositeReporter** — combines multiple reporters (used automatically when `outputDir` is set)

When `outputDir` is provided, both Logcat and file reporting are enabled.

---

## Route Builder Tool

The Route Builder is a web-based UI for creating and editing YAML route files interactively.

### Prerequisites

- Python 3
- A connected Android device or running emulator
- A `pilot.config.json` file in your project root

### Configuration

Create `pilot.config.json` in your project root (see `tools/route-builder/pilot.config.example.json`):

```json
{
  "appPackageId": "com.example.app.debug",
  "testRunner": "com.example.app.TestInstrumentationRunner",
  "testClass": "com.example.app.e2e.YamlRouteTest",
  "yamlDir": "app/src/androidTest/assets/routes",
  "testFile": "app/src/androidTest/kotlin/com/example/app/e2e/YamlRouteTest.kt",
  "testMarker": "// --- Individual routes ---",
  "insertBefore": "\n    companion object {",
  "debugApk": "app/build/outputs/apk/debug/app-debug.apk",
  "testApk": "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
  "buildCommand": ["./gradlew", "app:assembleDebug", "app:assembleDebugAndroidTest"],
  "github": {
    "owner": "",
    "repo": "",
    "workflowFile": "route-tests.yml"
  }
}
```

#### Config Fields

| Field | Description |
|---|---|
| `appPackageId` | Your app's debug package ID |
| `testRunner` | Your test instrumentation runner class |
| `testClass` | The test class that runs YAML routes |
| `yamlDir` | Directory where route YAML files are stored |
| `testFile` | Path to the test file for auto-generating test methods |
| `testMarker` | Marker comment in the test file for inserting new tests |
| `insertBefore` | Code pattern to insert new test methods before |
| `debugApk` | Path to the debug APK output |
| `testApk` | Path to the test APK output |
| `buildCommand` | Gradle command to build both APKs |
| `github` | Optional GitHub integration for CI (owner, repo, workflowFile) |

### Running the Route Builder

```bash
./scripts/route-builder.sh
```

Or with a custom config path:

```bash
./scripts/route-builder.sh path/to/pilot.config.json
```

This starts a local server at `http://localhost:8080` and opens your browser. From the web UI you can:

- Create new route YAML files
- Edit existing routes
- Build and run tests on a connected device
- Trigger GitHub Actions workflows (if configured)

---

## Architecture

```
YAML File / DSL
       |
       v
  YamlRouteParser / RouteBuilder
       |
       v
    Route (data model)
       |
       v
  RouteTestRule (JUnit TestRule)
       |
       v
  RouteRunner (orchestrator)
       |
       +---> ScreenReader -----> Compose Semantics Tree + Screenshot
       |
       +---> ClaudeBackend ----> Anthropic Claude API (tool_use)
       |
       +---> ActionExecutor ---> Compose UI (tap, type, scroll, swipe, wait)
       |
       +---> RouteReporter --> Logcat / File output
```

### Key Classes

| Class | Package | Role |
|---|---|---|
| `Pilot` | `co.pilot.android` | Entry point and configuration |
| `PilotConfig` | `co.pilot.android` | Configuration builder |
| `RouteTestRule` | `co.pilot.android.runner` | JUnit 4 TestRule for running routes |
| `RouteRunner` | `co.pilot.android.runner` | Orchestrates step/instruction execution with retry logic |
| `ClaudeBackend` | `co.pilot.android.ai` | Sends requests to the Anthropic Claude API using tool_use |
| `ComposeScreenReader` | `co.pilot.android.screen` | Captures Compose semantics tree and screenshots |
| `ComposeActionExecutor` | `co.pilot.android.executor` | Executes UI actions via Compose test APIs |
| `YamlRouteParser` | `co.pilot.android.yaml` | Parses YAML files into `Route` objects |
| `YamlRouteLoader` | `co.pilot.android.yaml` | Batch-loads routes from assets or directories |
| `Route` / `Step` / `Instruction` | `co.pilot.android.dsl` | Data model for test routes |

---

## Troubleshooting

### API Key Errors

**Error:** `Pilot API key is required. Set it via Pilot.configure { apiKey = "..." }`

Your API key is empty. Ensure you're passing it through `Pilot.configure` or set the `ROUTES_AI_API_KEY` environment variable.

### Claude API Errors

**Error:** `Claude API error 401: ...`

Your API key is invalid or expired. Generate a new one at [console.anthropic.com](https://console.anthropic.com/).

**Error:** `Claude API error 429: ...`

You've hit the rate limit. Wait a moment and retry, or check your Anthropic plan's rate limits.

### Verification Timeouts

**Error:** `Verification failed after 15s: <instruction>`

The AI couldn't verify the assertion within the timeout. Try:
- Increasing the step `timeout` value
- Making the verify instruction more specific
- Ensuring the UI has loaded before the verification step

### Compose Test Rule Issues

**Error:** `RouteTestRule has not been initialized`

Make sure `RouteTestRule` is annotated with `@get:Rule` and is applied *after* your `ComposeTestRule`:

```kotlin
@get:Rule(order = 0)
val composeTestRule = createAndroidComposeRule<MainActivity>()

@get:Rule(order = 1)
val routeRule = RouteTestRule(composeTestRule, ...)
```

### Min SDK Mismatch

Pilot requires Min SDK 29. If your app targets a lower SDK, you can set the `androidTest` source set to a higher min SDK or use a `tools:overrideLibrary` manifest merge strategy.

---

## API Reference

### Pilot

```kotlin
object Pilot {
    const val VERSION: String = "0.1.0"
    fun configure(block: PilotConfig.() -> Unit): PilotConfig
}
```

### PilotConfig

```kotlin
class PilotConfig {
    var apiKey: String
    var model: String          // Default: "claude-sonnet-4-20250514"
    var maxTokens: Int         // Default: 1024
    var outputDir: File?       // Default: null
    fun buildBackend(): AiBackend
}
```

### RouteTestRule

```kotlin
class RouteTestRule(
    composeTestRule: ComposeTestRule,
    aiBackend: AiBackend,
    outputDir: File? = null,
) : TestRule {
    suspend fun runRoute(route: Route)
    suspend fun runRoute(name: String, block: RouteBuilder.() -> Unit)
}
```

### YamlRouteParser

```kotlin
object YamlRouteParser {
    fun parse(input: InputStream, sourceName: String? = null): Route
    fun parse(yamlString: String, sourceName: String? = null): Route
}
```

### YamlRouteLoader

```kotlin
object YamlRouteLoader {
    fun loadFromDirectory(directory: File): List<Route>
    fun loadFromAssets(assets: AssetManager, path: String = "routes"): List<Route>
    fun loadFromStreams(streams: List<Pair<String, InputStream>>): List<Route>
}
```

### Route DSL

```kotlin
fun route(name: String, block: RouteBuilder.() -> Unit): Route

class RouteBuilder {
    fun step(name: String, block: StepBuilder.() -> Unit)
}

class StepBuilder {
    var timeout: Int?
    fun action(description: String)
    fun verify(description: String)
    fun remember(key: String, description: String)
}
```

---

## Contributing

```bash
# Clone the repository
git clone https://github.com/YOUR_ORG/pilot-android.git
cd pilot-android

# Build the library
./gradlew build

# Run unit tests
./gradlew test

# Run Detekt linting
./gradlew detekt
```

### Project Structure

```
pilot-android/
├── pilot/                    # Main library module
│   ├── src/main/kotlin/      # Source code
│   ├── src/main/assets/      # Example YAML routes
│   └── src/test/             # Unit tests
├── tools/route-builder/    # Web-based route builder tool
├── scripts/                  # Utility scripts
├── config/detekt.yml         # Detekt linting configuration
└── gradle/libs.versions.toml # Version catalog
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
