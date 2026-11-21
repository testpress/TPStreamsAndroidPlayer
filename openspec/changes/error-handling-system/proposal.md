## Why

The SDK currently has an ad-hoc error handling system: a flat enum of 8 values, incomplete mapping of ExoPlayer's 30+ error codes, no retry strategy, no multi-stage failure detection, and a single-TextView error overlay. Students see confusing messages, developers can't identify failure points from support tickets, and Sentry lacks breadcrumbs for silent recovery paths. This makes every playback issue require manual Sentry investigation.

## What Changes

- Replace flat PlaybackError enum with hierarchical SDK-owned error codes (TP-XXXX format)
- Add multi-stage failure detection: distinct error handling for auth, asset fetch, DRM, playback phases
- Implement retry strategy with exponential backoff (max 3 attempts), per-category rules
- Add internet validation probe that distinguishes "no internet" from "CDN/API blocked"
- Replace NetworkRecoveryHandler with validated network change detection (NET_CAPABILITY_VALIDATED)
- Redesign error overlay with student-friendly messages, copyable error code, retry button, diagnostics section
- Instrument all error paths for Sentry including silent recoveries and breadcrumbs
- Add network diagnostics (WiFi/cellular type, link speed) to error context (no signal strength — requires location permission)
- Remove concatenated "error code: X. Player ID: Y" from user-facing messages (moved to diagnostics section)

## Capabilities

### New Capabilities

- `error-codes`: SDK-owned error code system with TP-XXXX format, categorized ranges, mapping from ExoPlayer codes and API responses
- `multi-stage-detection`: Sequential stage tracking (auth → asset fetch → DRM license → prepare → playback) with stage-specific error handling and Sentry context
- `retry-strategy`: Per-category retry rules (max 3 attempts, exponential backoff, no retry on 404/401/403, 1 retry on 429 with Retry-After)
- `internet-validation`: Active internet probe to external endpoint, distinguishes no-internet from blocked-CDN scenarios
- `network-change-handling`: Validated network transition detection using NET_CAPABILITY_VALIDATED, not just onAvailable
- `error-screen-ui`: Redesigned error overlay with student message, retry button, expandable diagnostics, copyable error code
- `sentry-instrumentation`: Comprehensive Sentry logging for all error paths including silent recoveries and breadcrumbs

### Modified Capabilities

*(none — no existing specs have requirement changes)*

## Impact

- **TPStreamsPlayer.kt**: Add stage tracking, replace error mapping, integrate retry logic, add probe calls
- **TPStreamsPlayerView.kt**: Replace error overlay with new UI, wire diagnostics display
- **PlaybackError.kt**: Replace enum with full error code system, remove old mapping functions
- **AssetRepository.kt**: Add retry logic, stage tagging, updated error reporting
- **NetworkRecoveryHandler.kt**: Replace with validated network detection using NET_CAPABILITY_VALIDATED
- **SentryLogger.kt**: Add breadcrumb support, network state snapshots, retry context
- **error_overlay.xml**: Complete redesign with buttons, diagnostics section, error code display
- **New files**: Error code definitions, internet probe utility, retry coordinator, network diagnostics helper
