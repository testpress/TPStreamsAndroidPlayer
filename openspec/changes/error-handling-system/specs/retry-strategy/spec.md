## ADDED Requirements

### Requirement: RetryCoordinator SHALL manage retry state
A `RetryCoordinator` class SHALL own retry state per playback session. It SHALL track attempt count, apply per-category rules, and signal when to retry vs show final error.

#### Scenario: RetryCoordinator initial state
- **WHEN** a new playback session starts
- **THEN** the RetryCoordinator SHALL have attempt count = 0
- **AND** no backoff timer SHALL be active

### Requirement: Per-category retry rules
The RetryCoordinator SHALL apply the following retry rules by error category:

| Error Codes | Retriable | Max Attempts | Backoff |
|------------|-----------|-------------|---------|
| TP-2001, TP-2002, TP-2004, TP-2005 (auth) | ✗ No | 0 | N/A |
| TP-3001 (asset not found) | ✗ No | 0 | N/A |
| TP-4001, TP-4002, TP-4003, TP-4004 (DRM) | ✗ No | 0 | N/A |
| TP-5001, TP-5002 (decoder init/format unsupported) | ✗ No | 0 | N/A |
| TP-5005 (decoder busy / resource contention) | ✓ Yes | 2 | Exponential |
| TP-5003, TP-5004 (live stream state) | ✗ No | 0 | N/A |
| TP-3002, TP-3003 (asset server/API error) | ✓ Yes | 3 | Exponential |
| TP-1001, TP-1002, TP-1003 (network) | ✓ Yes | 3 | Exponential |
| TP-1004, TP-1005 (internet/CDN) | ✓ Yes | 3 | Exponential |
| TP-429 (rate limited, mapped to TP-9xxx) | ✓ Yes | 1 | Retry-After header or 5s |
| TP-9001, TP-9002 (unspecified) | ✗ No | 0 | N/A |

#### Scenario: Retry on network error
- **WHEN** a `TP-1001` error occurs
- **AND** attempt count is 0
- **THEN** the RetryCoordinator SHALL signal retry with 1s backoff
- **AND** increment attempt count to 1
- **AND** the SDK SHALL call `retryPlayback()`

#### Scenario: No retry on auth error
- **WHEN** a `TP-2001` error occurs
- **THEN** the RetryCoordinator SHALL signal final error (no retry)

#### Scenario: No retry on DRM error
- **WHEN** a `TP-4001` error occurs
- **THEN** the RetryCoordinator SHALL signal final error (no retry — license failure needs user action or app restart)

#### Scenario: Max attempts reached
- **WHEN** a retriable error occurs
- **AND** attempt count is already 3
- **THEN** the RetryCoordinator SHALL signal final error
- **AND** NOT attempt another retry

#### Scenario: Single retry on 429 with Retry-After
- **WHEN** AssetRepository receives HTTP 429
- **AND** the response includes a `Retry-After` header
- **THEN** the RetryCoordinator SHALL wait for `Retry-After` seconds OR 5s minimum before retrying
- **WHEN** the retry also returns 429
- **THEN** the RetryCoordinator SHALL signal final error (only 1 attempt allowed for 429)

### Requirement: Retry uses exponential backoff
The delay between retry attempts SHALL follow exponential backoff: 1s, 2s, then 4s.

#### Scenario: Exponential backoff timing
- **WHEN** a retriable error occurs
- **AND** it is retry attempt 1
- **THEN** the delay SHALL be 1 second
- **WHEN** the retry fails (attempt 2)
- **THEN** the delay SHALL be 2 seconds
- **WHEN** the retry fails again (attempt 3)
- **THEN** the delay SHALL be 4 seconds

### Requirement: UI shows buffering state during retry
When the RetryCoordinator signals a retry, the SDK SHALL keep the player in a buffering/loading state rather than showing the error screen. The error screen SHALL only appear on final error.

#### Scenario: Buffering during retry
- **WHEN** a retriable error occurs
- **AND** the RetryCoordinator signals retry
- **THEN** the player SHALL show the buffering indicator
- **AND** the error overlay SHALL NOT be shown
- **WHEN** the final attempt fails
- **THEN** the buffering indicator SHALL be hidden
- **AND** the error overlay SHALL be shown with the final error

### Requirement: Retry counter resets on successful stage transition
When any pipeline stage completes successfully (e.g., asset API succeeds, ExoPlayer reaches STATE_READY), the RetryCoordinator SHALL reset the attempt count to 0 for the next stage.

#### Scenario: Retry counter reset between stages
- **WHEN** the asset API fails and retries
- **AND** eventually succeeds on attempt 3
- **THEN** the RetryCoordinator SHALL reset the counter to 0
- **WHEN** a playback error occurs later at the PLAYING stage
- **THEN** the RetryCoordinator SHALL begin with attempt 1, not attempt 4
