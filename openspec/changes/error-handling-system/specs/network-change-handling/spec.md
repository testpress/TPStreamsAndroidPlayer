## ADDED Requirements

### Requirement: NetworkStateMonitor SHALL replace NetworkRecoveryHandler
A new `NetworkStateMonitor` class SHALL replace `NetworkRecoveryHandler`. It SHALL use `ConnectivityManager.NetworkCallback` with `NET_CAPABILITY_VALIDATED` to detect actual internet availability.

#### Scenario: Network recovery with VALIDATED check
- **WHEN** a new network becomes available (onAvailable fires)
- **THEN** NetworkStateMonitor SHALL do nothing in onAvailable — getCapabilities may return null and VALIDATED is not yet reliable
- **WHEN** `onCapabilitiesChanged` fires for the new network
- **THEN** NetworkStateMonitor SHALL check `network.getCapabilities()?.hasCapability(NET_CAPABILITY_VALIDATED) == true`
- **WHEN** VALIDATED is true
- **AND** the internet probe confirms reachability
- **THEN** NetworkStateMonitor SHALL trigger the recovery callback
- **WHEN** `getCapabilities()` returns null
- **THEN** NetworkStateMonitor SHALL NOT trigger recovery on that callback

### Requirement: Recovery only fires when NET_CAPABILITY_VALIDATED is true
The NetworkStateMonitor SHALL NOT trigger recovery on `onAvailable` alone. It MUST wait until `NET_CAPABILITY_VALIDATED` is confirmed via `onCapabilitiesChanged`. VALIDATED is the system's signal that the network has passed connectivity validation (captive portal check, DNS resolution, etc.).

#### Scenario: Delayed validation
- **WHEN** onAvailable fires (no action taken)
- **WHEN** `onCapabilitiesChanged` fires with capabilities
- **AND** `hasCapability(NET_CAPABILITY_VALIDATED)` is false
- **THEN** NetworkStateMonitor SHALL wait for a subsequent `onCapabilitiesChanged` with VALIDATED=true
- **AND** NOT trigger recovery before that

#### Scenario: Validation never arrives
- **WHEN** a network becomes available
- **AND** `onCapabilitiesChanged` with VALIDATED=true never fires within 30 seconds
- **THEN** NetworkStateMonitor SHALL consider the network unusable
- **AND** SHALL NOT trigger recovery for this network

### Requirement: NetworkStateMonitor tracks transport type for diagnostics
The monitor SHALL track the current network transport type (WiFi, cellular, ethernet, VPN) and expose it for diagnostic display on the error screen.

#### Scenario: Transport type diagnostics
- **WHEN** playback fails with a network error
- **AND** the device is on WiFi
- **THEN** the diagnostics section SHALL show "Connection: WiFi" with link speed (if available; SSID requires location permission and is NOT included)
- **WHEN** the device is on cellular
- **THEN** the diagnostics section SHALL show "Connection: Cellular" with network type (LTE, 5G, etc.)

### Requirement: Network change mid-playback re-validates
When a network transition occurs during active playback (WiFi→cellular switch, WiFi→WiFi switch), the SDK SHALL re-validate using the same procedure before assuming connectivity.

#### Scenario: WiFi to cellular handoff
- **WHEN** the device switches from WiFi to cellular during playback
- **AND** the old network (WiFi) becomes unavailable
- **THEN** NetworkStateMonitor SHALL wait for VALIDATED=true on the new network (cellular)
- **AND** the internet probe SHALL confirm reachability
- **AND** only then SHALL any pending retry be triggered
