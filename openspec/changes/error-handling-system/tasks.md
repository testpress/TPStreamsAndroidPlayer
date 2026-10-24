## 1. Foundation: Error Code System

- [ ] 1.1 Create `TpErrorCode.kt` — sealed class/object with all TP-XXXX codes, tech labels, and category groups
- [ ] 1.2 Add error code mapping from ExoPlayer `PlaybackException.errorCode` to TP-XXXX (replace `toError()` extension)
- [ ] 1.3 Add error code mapping from HTTP response codes to TP-XXXX (replace switch in `AssetRepository.handleApiError`)
- [ ] 1.4 Add error code mapping from custom exceptions (LiveStreamNotStartedException, LiveStreamEndedException)
- [ ] 1.5 Add TP-2004 (CDN_AUTH_FAILED), TP-2005 (TOKEN_VALIDATION_FAILED), and TP-5005 (DECODER_BUSY) to the error code set
- [ ] 1.6 Add student-facing message templates for each TP-XXXX code (plain language, no tech codes)
- [ ] 1.7 Remove old `PlaybackError` enum and its extension functions (`toError()`, `getErrorMessage()`)


## 2. Foundation: Playback Stage Tracking

- [ ] 2.1 Create `PlaybackStage` enum: IDLE → VALIDATING → FETCHING_ASSET → FETCHING_DRM_LICENSE → PREPARING → PLAYING
- [ ] 2.2 Add stage field to `TPStreamsPlayer` and update at each pipeline transition
- [ ] 2.3 Wire stage into error context so it flows through to Sentry and the error screen
- [ ] 2.4 Thread stage info through `AssetRepository` callbacks so API errors carry FETCHING_ASSET stage
- [ ] 2.5 Set stage to FETCHING_DRM_LICENSE before DRM license fetch in preparePlayer
- [ ] 2.6 Set stage to PREPARING after DRM license fetch completes

## 3. Core: Internet Probe

- [ ] 3.1 Create `InternetProbe` class with ProbeResult enum (REACHABLE, NOT_REACHABLE)
- [ ] 3.2 Implement HTTP HEAD probe on Dispatchers.IO with 3s timeout
- [ ] 3.3 Return NOT_REACHABLE on any exception (no need to distinguish timeout vs DNS failure)
- [ ] 3.4 Add fallback probe logic (primary → google.com fallback)
- [ ] 3.5 Add 30-second TTL cache for probe results
- [ ] 3.6 Invalidate probe cache on any network change event (onAvailable, onLost)
- [ ] 3.7 Add `TPStreamsSDK.setProbeEndpoint(url)` for developer configuration

## 4. Core: Network Change Handling

- [ ] 4.1 Create `NetworkStateMonitor` replacing `NetworkRecoveryHandler`
- [ ] 4.2 Implement onAvailable as no-op (rely on onCapabilitiesChanged)
- [ ] 4.3 Add onCapabilitiesChanged listener for VALIDATED check
- [ ] 4.3b Handle null getCapabilities() — skip callback, don't crash
- [ ] 4.4 Add transport type tracking (WiFi, cellular, ethernet) and link speed (from NetworkCapabilities.getLinkDownstreamBandwidthKbps()) for diagnostics
- [ ] 4.5 Wire internet probe as gate before triggering recovery retry
- [ ] 4.6 Add 30s timeout for VALIDATED confirmation
- [ ] 4.7 Remove old `NetworkRecoveryHandler` class
- [ ] 4.8 Update `isNetworkError()` helper to handle new error codes

## 5. Core: Retry Coordinator

- [ ] 5.1 Create `RetryCoordinator` class with shouldRetry(error) → RetryDecision enum
- [ ] 5.2 Implement per-category retry table (no retry on auth/404/DRM/decoder-format, 1 retry on decoder-busy, retry on network/server)
- [ ] 5.3 Implement exponential backoff: 1s, 2s, 4s
- [ ] 5.3b Add RetryDecision.retry(attempt, delay) and RetryDecision.final(error) return types
- [ ] 5.4 Implement 429 single retry with Retry-After header support
- [ ] 5.5 Wire RetryCoordinator into `TPStreamsPlayer.onPlayerError` and `AssetRepository` error paths
- [ ] 5.6 Show buffering state during retry, error screen only on final error
- [ ] 5.7 Reset attempt counter on successful stage transitions

## 6. Core: Multi-Stage Detection Integration

- [ ] 6.1 In `TPStreamsPlayer.init`: set stage to VALIDATING before asset fetch
- [ ] 6.2 In `AssetRepository` callbacks: pass stage context through to error handling
- [ ] 6.3 In `TPStreamsPlayer.preparePlayer`: set stage to FETCHING_DRM_LICENSE before DRM license fetch, then to PREPARING after DRM succeeds
- [ ] 6.4 In `TPStreamsPlayer.onPlayerError`: check stage for auth-passed context (TP-200 → TP-1005 logic)
- [ ] 6.5 In `TPStreamsPlayer.onPlaybackStateChanged(READY)`: set stage to PLAYING
- [ ] 6.6 Wire internet probe into network error paths before error screen display

## 7. UI: Error Screen Overlay

- [ ] 7.1 Redesign `error_overlay.xml` with: student message, retry button, expandable diagnostics section, copy button, contact support link
- [ ] 7.2 Add error icon drawable resource (no location-dependent assets)
- [ ] 7.3 Implement expand/collapse logic for technical details section in `TPStreamsPlayerView`
- [ ] 7.4 Implement "Copy details" clipboard functionality
- [ ] 7.5 Implement "Contact support" — configurable callback via TPStreamsSDK, fallback to email intent
- [ ] 7.5b Wire retry button tap to reset RetryCoordinator and restart full pipeline
- [ ] 7.6 Show/hide retry button based on error retriability
- [ ] 7.7 Wire `NetworkStateMonitor` diagnostics (transport type, link speed) into error screen (no SSID or signal strength — both require location permission)
- [ ] 7.8 Remove old HTML-based message handling for decoder errors (no longer needed)
- [ ] 7.9 Ensure buffering spinner shows during retry backoff, error overlay only on final failure

## 8. Instrumentation: Sentry Logging

- [ ] 8.1 Add `logBreadcrumb(level, message, data)` method to `SentryLogger`
- [ ] 8.2 Ensure `Sentry.init` has `cacheDirPath` configured (Sentry SDK handles offline persistence natively)
- [ ] 8.2b Add standard tag schema constants to `SentryLogger`
- [ ] 8.2c Add `Sentry.flush(2000)` call on network recovery (no custom queue needed)
- [ ] 8.3 Update `logPlaybackException` to include all required tags (sdk_error_code, playback_stage, retry_attempt, transport_type)
- [ ] 8.4 Update `logAPIException` to include all required tags
- [ ] 8.5 Add network state snapshot context block to all exception events (use NetworkCapabilities.getLinkDownstreamBandwidthKbps for speed, not WifiInfo)
- [ ] 8.6 Log breadcrumbs for: silent DRM renew, network recovery start/stop, retry attempts, internet probe results, error screen shown
- [ ] 8.7 Add `retry_attempt` tag to all error events
- [ ] 8.8 Add `transport_type` tag to all error events

## 9. Cleanup & Verification

- [ ] 9.1 Remove old `PlaybackError.kt` file entirely
- [ ] 9.2 Remove concatenated "Player code: X. Player Id: Y" from student messages
- [ ] 9.3 Verify all existing callers of `listener.onError()` and `AssetCallback.onError()` work with new error types
- [ ] 9.4 Verify retry button is hidden for non-retriable errors (auth, 404, decoder)
- [ ] 9.5 Verify buffering state shows during retry attempts
- [ ] 9.6 Verify error screen shows on final failure
- [ ] 9.7 Verify probe fires before network error screen (not after)
- [ ] 9.8 Verify NET_CAPABILITY_VALIDATED gates recovery via onCapabilitiesChanged (available from API 21)
- [ ] 9.9 Verify Sentry breadcrumbs appear for all silent paths
- [ ] 9.10 Verify Sentry.init has cacheDirPath configured for offline persistence
- [ ] 9.10b Verify Sentry.flush(2000) is called on network recovery
- [ ] 9.10c Verify diagnostics use NetworkCapabilities.getLinkDownstreamBandwidthKbps (not WifiInfo)
- [ ] 9.11 Verify cached probe result is invalidated on network change
- [ ] 9.12 Verify retry UX: buffering during backoff, error screen only on final failure
