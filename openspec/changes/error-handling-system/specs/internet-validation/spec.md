## ADDED Requirements

### Requirement: InternetProbe SHALL validate actual internet access
An `InternetProbe` class SHALL attempt to reach a reliable external endpoint via HTTP HEAD request. The probe SHALL return a `ProbeResult` enum with two states: `REACHABLE` and `NOT_REACHABLE`. Two states are sufficient — we only need to distinguish "can reach the internet" from "cannot reach it."

#### Scenario: Probe succeeds
- **WHEN** the probe reaches the endpoint and receives HTTP 2xx or 3xx
- **THEN** the result SHALL be `REACHABLE`

#### Scenario: Probe fails with any exception
- **WHEN** the probe throws any exception (IOException, SocketException, UnknownHostException, timeout)
- **THEN** the result SHALL be `NOT_REACHABLE`
- **AND** the SDK SHALL NOT distinguish between timeout, DNS failure, or connection refused

### Requirement: Probe endpoint is configurable
The probe endpoint URL SHALL be configurable via `TPStreamsSDK`. The default SHALL be a TPStreams-owned connectivity check endpoint. If that fails, a fallback to a well-known endpoint (google.com) SHALL be attempted.

#### Scenario: Fallback probe
- **WHEN** the primary probe endpoint is unreachable
- **THEN** the probe SHALL attempt the fallback endpoint
- **WHEN** the fallback also fails
- **THEN** the result SHALL be `UNREACHABLE`

### Requirement: Probe fires before network-type error screen
When a network-related error is detected (TP-1001, TP-1002, TP-3003), the SDK SHALL fire the internet probe BEFORE showing the error screen. The probe result SHALL determine which error code and message to display.

#### Scenario: Probe result determines error message
- **WHEN** a network error occurs
- **AND** the internet probe returns `NOT_REACHABLE`
- **THEN** the SDK SHALL show a "No internet connection" message (TP-1004)
- **WHEN** a network error occurs
- **AND** the internet probe returns `REACHABLE`
- **THEN** the SDK SHALL show a "Streaming server unreachable" message (TP-1005 or original code)
- **WHEN** a network error occurs
- **AND** the internet probe returns `NOT_REACHABLE`
- **AND** the original error has a timeout source (ExoPlayer ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
- **THEN** the SDK SHALL show a "Connection timed out" message (TP-1002)

### Requirement: Probe is cached with TTL
Probe results SHALL be cached for 30 seconds to avoid repeated network calls during rapid error-recovery cycles. The cache SHALL be invalidated on ANY network change event (onAvailable, onLost) to prevent stale results from a previous network from being used after a network transition.

#### Scenario: Cached probe result
- **WHEN** a network error triggers a probe
- **AND** the probe returns `REACHABLE`
- **AND** within 30 seconds another network error occurs
- **AND** no network change event occurred in between
- **THEN** the SDK SHALL use the cached `REACHABLE` result
- **AND** NOT make a new probe request

#### Scenario: Cache invalidated on network change
- **WHEN** a probe returns `REACHABLE`
- **AND** a network change event fires (onAvailable or onLost)
- **THEN** the cached probe result SHALL be invalidated
- **AND** the next probe request SHALL make a fresh attempt

### Requirement: Probe gates network recovery
The `NetworkStateMonitor` SHALL fire the probe before triggering retry after a network recovery event (e.g., WiFi reconnects). Retry SHALL only be triggered if the probe returns `REACHABLE`.

#### Scenario: Probe prevents false recovery
- **WHEN** the device reconnects to WiFi (onCapabilitiesChanged fires with VALIDATED=true)
- **AND** the internet probe returns `NOT_REACHABLE` (captive portal)
- **THEN** the SDK SHALL NOT retry playback
- **AND** SHALL show a "Connected to WiFi but no internet access" message
