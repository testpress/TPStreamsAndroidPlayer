## Context

The SDK's current error handling grew organically. TPStreamsPlayer has a single error callback, a flat 8-value enum, and an error overlay with one TextView. Network recovery uses raw ConnectivityManager.onAvailable with no internet validation. There is no stage tracking, no retry logic, and Sentry logging is limited to exceptions with no breadcrumbs for silent paths.

The architecture wraps ExoPlayer by delegation. The error pipeline has four implicit stages: token validation, asset API fetch, DRM license acquisition, and runtime playback. Each stage requires distinct error handling behavior but currently flows through the same path.

## Goals / Non-Goals

**Goals:**
- Replace the flat PlaybackError enum with an SDK-owned error code system (TP-XXXX) that maps every known failure path
- Add sequential stage tracking so error context includes which pipeline step failed
- Implement per-category retry with exponential backoff and attempt counting
- Add internet validation probe that gates recovery and enables diagnostic display
- Replace ConnectivityManager.onAvailable with NET_CAPABILITY_VALIDATED detection
- Redesign error overlay with student message, retry button, expandable diagnostics, copyable code
- Log all errors to Sentry including silent recoveries as breadcrumbs
- Surface network diagnostics (transport type, link speed) on network errors

**Non-Goals:**
- Not changing the ExoPlayer CDN-level retry, chunk source, or adaptive bitrate logic
- Not adding a full network speed test (latency probe only, not bandwidth measurement)
- Not changing how developers pass tokens or configure the SDK
- Not redesigning the non-error parts of the player UI (controls, settings, etc.)

## Decisions

### 1. Error Code Format: TP-XXXX with Categorized Ranges

```
TP-1xxx → Network / connectivity
TP-2xxx → Authentication / token
TP-3xxx → Asset loading / API
TP-4xxx → DRM
TP-5xxx → Playback / decoder / format
TP-9xxx → Unknown / fallback
```

Each error code maps to a stable tech label (developer-facing) and a student-facing message template. The code embeds both category and instance, so `TP-1001` immediately tells support "network connection failed."

**Alternatives considered:**
- **Flat sequential**: Simpler assignment, but no category signal in the code. Rejected because support needs instant category recognition from tickets.
- **UUID per error**: Too opaque for support. Rejected.
- **ExoPlayer errorCode directly**: Not SDK-owned, changes across ExoPlayer versions. Rejected.

### 2. Stage Tracking as Internal Enum

Introduce `PlaybackStage` enum with states:

```
IDLE → VALIDATING → FETCHING_ASSET → FETCHING_DRM_LICENSE → PREPARING → PLAYING
```

DRM license fetch is its own stage because it's a separate API call with a different failure domain, different error codes, and different retry behavior than media preparation. Without this distinction, a DRM failure and a codec failure both report `stage: PREPARING` in Sentry, defeating the purpose of stage tracking.

This lives inside `TPStreamsPlayer`, updated at each pipeline transition. Errors capture the current stage and include it in Sentry context and the error code. This enables the multi-stage detection without a complex state machine.

**Rationale:** Minimal state, no external dependency, easy to thread through existing callbacks.

### 3. Retry Coordinator as a New Class

A `RetryCoordinator` class owns retry state per attempt. Each error path calls `RetryCoordinator.shouldRetry(error)` which checks:
- Is this error category retriable?
- Have we exceeded max attempts (3)?
- Apply exponential backoff (1s, 2s, 4s)

The coordinator emits callbacks: `onRetry(attempt, delay)` and `onFinalError(error)`.

**Alternatives considered:**
- **Inline retry in TPStreamsPlayer**: Couples retry logic with player logic. Rejected.
- **Coroutine-based with delay**: Lifecycle management via `CoroutineScope` is actually easy — one `scope.cancel()` on release stops everything. The objection is not lifecycle complexity but **testability and separation of concerns**: a standalone coordinator can be unit-tested without a player instance, and retry policy logic is cleanly decoupled from playback orchestration.

### 4. Internet Probe Utility

A lightweight `InternetProbe` class that performs an HTTP HEAD request on `Dispatchers.IO` to a reliable endpoint. Returns the probe result as an enum:

```kotlin
enum class ProbeResult { REACHABLE, NOT_REACHABLE }
```

Key parameters:
- **Timeout**: 3 seconds per attempt (connect + read)
- **Dispatcher**: `Dispatchers.IO` — never on main thread
- **Exception handling**: ANY exception (IOException, SocketException, UnknownHostException) returns `NOT_REACHABLE`. Two states are sufficient — we only need to know "can I reach the internet" or not. Distinguishing timeout vs DNS failure adds complexity without actionability.
- **Primary endpoint**: Configurable via `TPStreamsSDK.setProbeEndpoint()`. Default: TPStreams-owned connectivity check endpoint.
- **Fallback**: `https://www.google.com` — only used if primary endpoint fails.

Used in two places:
- On network error before showing screen (to distinguish no-internet from CDN-blocked)
- In the network recovery path before attempting retry (to prevent false recoveries)

Cache the probe result (30s TTL) to avoid hammering the endpoint. **The cache MUST be invalidated on any network change event** — otherwise a stale REACHABLE result from a previous WiFi network could be used after a network transition to a non-functional network.

**Rationale:** Separates the probe mechanism from the error handling logic. Easy to mock in tests.

### 5. Network Change Handling

Replace `NetworkRecoveryHandler` with `NetworkStateMonitor` that:
- Registers a `ConnectivityManager.NetworkCallback`
- Does nothing in `onAvailable` — VALIDATED may not be set yet, and `getCapabilities()` can return null
- Waits for `onCapabilitiesChanged` with `network.getCapabilities()?.hasCapability(NET_CAPABILITY_VALIDATED) == true`
- Only triggers recovery callback when VALIDATED is confirmed via `onCapabilitiesChanged`
- Tracks transport type (WiFi, cellular, ethernet) for diagnostics
- No API level fallback needed: `ConnectivityManager.NetworkCallback`, `onCapabilitiesChanged`, and `NET_CAPABILITY_VALIDATED` are all available from API 21 (minSdk)

**Rationale:** This is the Android-idiomatic way to avoid false network recovery. `NET_CAPABILITY_VALIDATED` is the system's signal that a network actually has internet access.

### 6. Retriable Categories

`RetryCoordinator.shouldRetry(error)` SHALL use the following category-level rules:

| Category | Retriable | Max | Reason |
|----------|-----------|-----|--------|
| TP-1xxx Network | Yes | 3 | Transient connectivity issues |
| TP-2xxx Auth | No | 0 | Bad token won't fix itself — needs re-auth |
| TP-3xxx Asset API | Yes, once | 1 | Could be a transient server error (5xx). 404 is not retried. |
| TP-4xxx DRM | No | 0 | License acquisition, expiry, and system errors need user action or app restart |
| TP-5xxx Playback | Case by case | — | Decoder busy / resource contention → **Yes, max 2**. Format unsupported / codec not found → **No**. |
| TP-9xxx Unknown | No | 0 | Can't retry what we don't understand |

The per-code mapping in `specs/error-codes/spec.md` (in this change) defines the precise retriability for each individual code.

### 7. HTTP Error Flow Paths

Errors reach the SDK through two entirely separate channels. Each must be caught and mapped independently:

**Channel A: ExoPlayer `onPlayerError`**
- Catches: transport errors (network drops, timeout, chunk fetch failure), DRM errors during playback, decoder errors
- Mapped by: `TpErrorCode.fromExoPlayerErrorCode(int)` (replacing current `toError()` extension)
- These are already flowing through `onPlayerError` — just need better mapping

**Channel B: AssetRepository / API calls**
- Catches: HTTP responses from token validation, asset detail, license proxy endpoints
- Mapped by: `TpErrorCode.fromHttpCode(statusCode, endpoint)` where `endpoint` is an enum identifying which API failed
  ```kotlin
  enum class ApiEndpoint { TOKEN_VALIDATION, ASSET_DETAIL, DRM_LICENSE, CDN_MANIFEST }
  ```
  Using a raw URL is avoided — URLs may contain query parameters with credentials.
- These are NOT caught by ExoPlayer at all — no mapping exists today
- `401`/`403` on token endpoint → `TP-2001`/`TP-2002`
- `403` on CDN/manifest endpoint → `TP-2004` (CDN_AUTH_FAILED)
- `404` on asset detail endpoint → `TP-3001`
- `5xx` on asset detail endpoint → `TP-3002`
- `IOException` on any API call → `TP-3003`

**Channel C: Internet probe failures**
- Not strictly an "error" — a diagnostic finding that changes the error code and message
- `NOT_REACHABLE` + network error → elevates to `TP-1004` (no internet)
- `REACHABLE` + chunk fetch failure → `TP-1005` (CDN or firewall blocking)

### 8. Error Screen UI as Expandable Layout

The error overlay XML expands to:

```
┌────────────────────────────────────┐
│                                    │
│   [icon]                           │
│                                    │
│   [student message]                │
│   (plain language, actionable)     │
│                                    │
│   ┌────────────────────────┐       │
│   │     Try Again           │       │
│   └────────────────────────┘       │
│                                    │
│   ▼ Show technical details         │ ← toggle
│   ┌────────────────────────┐       │
│   │ Error: TP-1001         │       │
│   │ Connection: WiFi       │       │
│   │ Speed: 300 Mbps        │       │
│   │ Player ID: a1b2c3d4e5  │       │
│   │ Stage: PLAYING         │       │
│   │ ┌──────────────┐      │       │
│   │ │ Copy details   │      │       │
│   │ └──────────────┘      │       │
│   └────────────────────────┘       │
│                                    │
│   Contact support                  │
│                                    │
└────────────────────────────────────┘
```

The technical details section is hidden by default. A toggle button reveals it. The error code and player ID are displayed as plain text in this section, not in the student-facing message.

**Location-sensitive diagnostics note**: Signal strength and SSID both require `ACCESS_FINE_LOCATION` on Android 8+/8.1+. The diagnostics section MUST NOT include either field. Only transport type (WiFi/cellular/ethernet) and link speed (from `NetworkCapabilities.getLinkDownstreamBandwidthKbps()`, available from API 21, no location permission needed) are available without extra permissions. The SDK must NEVER request location permissions. Note: `WifiInfo.getLinkSpeed()` is deprecated from API 31 and should not be used.

**Contact support behavior**: The "Contact support" element SHALL fire a configurable callback provided by the host app via `TPStreamsSDK`. If no callback is configured, it SHALL fall back to opening the default email client with the subject line `TPStreams Player Error: TP-XXXX`. The SDK must not hardcode a support email address or URL.

**Player ID note**: `player_id` already exists in the SDK as `playbackSessionId` in `TPStreamsPlayer`. It is a 6-char alphanumeric string. No new ID generation is needed.

**Retry UX transition**: When a retriable error occurs and the RetryCoordinator signals a retry:
1. The error overlay is NOT shown — instead the player shows the buffering spinner
2. The user sees the buffering state during the backoff delay
3. If the retry succeeds: buffering dismisses, playback continues normally
4. If all 3 attempts fail: buffering hides, error overlay appears with final error
5. The "Try Again" button on the error screen stays enabled — it resets the retry counter and restarts the pipeline from scratch

### 9. Sentry Breadcrumbs for Silent Paths

Add a `logBreadcrumb` method to SentryLogger that captures:
- DRM license renew attempts (in-flight breadcrumb, not exception)
- Network recovery start/stop transitions
- Retry attempts (attempt number, delay, error)
- Internet probe results

Each breadcrumb includes the current stage, playback session ID, and network state snapshot.

### 10. Sentry Offline Handling

The Sentry Android SDK already handles offline scenarios natively — it persists events to disk and flushes them on reconnection. The SDK does not need a custom queue.

Requirements:
- Ensure `Sentry.init` is called with `cacheDirPath` configured so Sentry can write events to disk when offline
- On network recovery (NetworkStateMonitor + internet probe confirms reachability), call `Sentry.flush(2000)` to trigger an immediate send attempt
- Standard Sentry breadcrumbs (`Sentry.addBreadcrumb`) are used throughout — Sentry's SDK queues them internally and flushes them with the next event

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Retry coordinator introduces latency (1s-4s delays) | Delays only happen during retries. User sees buffering state during retry, not a blank screen. Error overlay only on final failure. |
| Internet probe endpoint could be blocked in some regions | Configurable probe URL. Fallback to google.com. Probe failure doesn't block error screen — just reduces diagnostic detail. |
| Error code table could grow stale as ExoPlayer adds codes | Define a catch-all TP-9xxx for unmapped codes. Add a periodic review process. |
| Expandable UI adds complexity to error overlay | All diagnostic data is hidden by default. Student experience is unchanged — they see a simple message + button. |
| Diagnostics limited without location permission | Only transport type and link speed available. SSID and signal strength require ACCESS_FINE_LOCATION (not requested). These fields are diagnostic helpers — their absence doesn't affect functionality. |
| Sentry offline during network failures | Sentry SDK's built-in disk persistence handles this. Flush-on-recovery via Sentry.flush(2000). No custom queue needed. |
