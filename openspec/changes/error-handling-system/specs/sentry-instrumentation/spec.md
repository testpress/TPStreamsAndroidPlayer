## ADDED Requirements

### Requirement: Every error path SHALL log to Sentry
Every error path in the SDK SHALL produce either a Sentry exception or a Sentry breadcrumb. This includes errors that are handled silently (no exception thrown, no error screen shown).

#### Scenario: Silent error produces breadcrumb
- **WHEN** a DRM license expired error is detected and silently renewed
- **THEN** the SDK SHALL produce a Sentry breadcrumb with level WARNING
- **AND** the breadcrumb SHALL include the error code, player ID, and stage

#### Scenario: User-visible error produces exception
- **WHEN** an error reaches the error overlay (final error, shown to user)
- **THEN** the SDK SHALL call `Sentry.captureException()` with the underlying error
- **AND** include all required tags and context

### Requirement: Sentry tags SHALL follow a standard schema
Every Sentry event SHALL include the following tags:

| Tag | Description | Example |
|-----|-------------|---------|
| `sdk_error_code` | TP-XXXX code | `TP-1001` |
| `sdk_error_label` | Tech label | `NETWORK_CONNECTION_FAILED` |
| `playback_stage` | Stage at error | `PLAYING` |
| `player_id` | Playback session ID | `a1b2c3d4e5` |
| `asset_id` | Asset being played | `abc123` |
| `retry_attempt` | Retry count (0 if first) | `2` |
| `transport_type` | Network type | `WiFi`, `CELLULAR` |

#### Scenario: Sentry event tags
- **WHEN** a Sentry exception is captured for a TP-3001 error at FETCHING_ASSET stage
- **THEN** the event SHALL have `sdk_error_code = "TP-3001"`
- **AND** `playback_stage = "FETCHING_ASSET"`
- **AND** `retry_attempt = "0"` (first occurrence)

### Requirement: Sentry SHALL include network state snapshot
Every error-related Sentry event SHALL include a `network_state` context block with the current network state at the time of the error.

#### Scenario: Network state context
- **WHEN** a Sentry exception is captured for a network error
- **THEN** the `network_state` context SHALL include:
  - `transport_type`: WiFi, Cellular, or Unknown
  - `is_validated`: whether NET_CAPABILITY_VALIDATED was true
  - `link_speed_kbps`: downstream bandwidth from `NetworkCapabilities.getLinkDownstreamBandwidthKbps()` (or "N/A")
  - `wifi_ssid`: SSID if on WiFi (or "N/A" — location permission not requested by SDK)
  - `cellular_network_type`: LTE, 5G, etc. if on cellular
  - `probe_result`: latest internet probe result — REACHABLE or NOT_REACHABLE (or "N/A")

### Requirement: Sentry SHALL handle offline scenarios via built-in persistence
The Sentry Android SDK already handles offline scenarios natively — it persists events to disk when the network is unavailable and flushes them on reconnection. The SDK SHALL NOT implement a custom queue.

#### Scenario: Sentry initialized with cacheDirPath
- **WHEN** `Sentry.init` is called
- **THEN** the configuration SHALL include `cacheDirPath` set to the app's cache directory
- **AND** NOT implement any custom in-memory breadcrumb buffer

#### Scenario: Flush on network recovery
- **WHEN** NetworkStateMonitor triggers recovery (validated network + probe success)
- **THEN** the SDK SHALL call `Sentry.flush()` with a 2-second timeout
- **AND** attempt to send any pending events

### Requirement: Retry attempts SHALL be logged as breadcrumbs
Each retry attempt SHALL produce a Sentry breadcrumb with level INFO, including the attempt number, delay, and error code being retried.

#### Scenario: Retry breadcrumb
- **WHEN** the RetryCoordinator signals a retry (attempt 1 of 3, 1s delay)
- **THEN** a Sentry breadcrumb SHALL be created with:
  - `message`: "Retry attempt 1/3 for TP-1001"
  - `level`: INFO
  - `data.retry_attempt`: 1
  - `data.retry_max`: 3
  - `data.retry_delay_ms`: 1000

### Requirement: Network recovery events SHALL be logged
When NetworkStateMonitor detects a validated network and triggers recovery, a Sentry breadcrumb SHALL be created.

#### Scenario: Recovery breadcrumb
- **WHEN** NetworkStateMonitor triggers a recovery callback
- **THEN** a Sentry breadcrumb SHALL be created with:
  - `message`: "Network recovered — validated"
  - `level`: INFO
  - `data.transport_type`: transport type of the new network

### Requirement: Internet probe results SHALL be logged
Every internet probe execution SHALL create a Sentry breadcrumb with the probe result and latency.

#### Scenario: Probe breadcrumb
- **WHEN** the internet probe completes with `REACHABLE` in 800ms
- **THEN** a Sentry breadcrumb SHALL be created with:
  - `message`: "Internet probe: REACHABLE"
  - `level`: INFO
  - `data.latency_ms`: 800
  - `data.probe_result`: REACHABLE or NOT_REACHABLE

### Requirement: Error screen display SHALL be logged
When the error overlay is shown to the user (final error), a Sentry breadcrumb SHALL be created.

#### Scenario: Error screen breadcrumb
- **WHEN** the error overlay is shown to the user
- **THEN** a Sentry breadcrumb SHALL be created with:
  - `message`: "Error screen shown"
  - `level`: ERROR
  - `data.sdk_error_code`: the TP-XXXX code
  - `data.is_retriable`: whether retry button is shown
