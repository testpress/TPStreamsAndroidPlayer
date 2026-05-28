## ADDED Requirements

### Requirement: Error codes use TP-XXXX format
The SDK SHALL define all error codes in the format `TP-XXXX` where XXXX is a zero-padded four-digit number. The first digit SHALL encode the error category: 1xxx = network, 2xxx = auth, 3xxx = asset loading, 4xxx = DRM, 5xxx = playback, 9xxx = unknown/fallback.

#### Scenario: Error code format validation
- **WHEN** an error occurs during playback
- **THEN** the SDK SHALL produce an error code matching the pattern `/^TP-\d{4}$/`
- **AND** the first digit SHALL match the error category

### Requirement: Every failure path maps to a unique error code
The SDK SHALL define and map every known failure path to a unique TP-XXXX code. The full table SHALL include:

| Code | Tech Label | Source |
|------|-----------|--------|
| TP-1001 | NETWORK_CONNECTION_FAILED | ExoPlayer ERROR_CODE_IO_NETWORK_CONNECTION_FAILED |
| TP-1002 | NETWORK_CONNECTION_TIMEOUT | ExoPlayer ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT |
| TP-1003 | CDN_CHUNK_FETCH_FAILED | ExoPlayer generic IO error during playback |
| TP-1004 | INTERNET_UNREACHABLE | Internet probe failed (no connectivity to external endpoint) |
| TP-1005 | CDN_OR_FIREWALL_BLOCKED | Probe passed but video streaming fails |
| TP-2001 | ACCESS_TOKEN_INVALID | Asset API returned 401 |
| TP-2002 | ACCESS_TOKEN_FORBIDDEN | Asset API returned 403 |
| TP-2003 | ACCESS_TOKEN_EXPIRED | DRM license expired check returned expired |
| TP-2004 | CDN_AUTH_FAILED | CDN returns 403 (CloudFront token auth failure) |
| TP-2005 | TOKEN_VALIDATION_FAILED | Token validation endpoint call fails |
| TP-3001 | ASSET_NOT_FOUND | Asset API returned 404 |
| TP-3002 | ASSET_SERVER_ERROR | Asset API returned 5xx |
| TP-3003 | ASSET_API_FAILED | Asset API IOException/timeout |
| TP-4001 | DRM_LICENSE_ACQUISITION_FAILED | ExoPlayer ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED |
| TP-4002 | DRM_LICENSE_EXPIRED | ExoPlayer ERROR_CODE_DRM_LICENSE_EXPIRED |
| TP-4003 | DRM_SYSTEM_ERROR | ExoPlayer ERROR_CODE_DRM_SYSTEM_ERROR or CryptoException |
| TP-4004 | DRM_DISALLOWED_OPERATION | ExoPlayer ERROR_CODE_DRM_DISALLOWED_OPERATION |
| TP-5001 | DECODER_INIT_FAILED | ExoPlayer ERROR_CODE_DECODER_INIT_FAILED |
| TP-5002 | DECODING_FAILED | ExoPlayer ERROR_CODE_DECODING_FAILED |
| TP-5003 | LIVE_STREAM_NOT_STARTED | Custom LiveStreamNotStartedException |
| TP-5004 | LIVE_STREAM_ENDED | Custom LiveStreamEndedException |
| TP-5005 | DECODER_BUSY | ExoPlayer ERROR_CODE_DECODING_FAILED when caused by codec resource contention |
| TP-9001 | UNSPECIFIED | Any unmapped ExoPlayer error code |
| TP-9002 | UNKNOWN_EXCEPTION | Any unmapped runtime exception |

#### Scenario: Error code for known ExoPlayer error
- **WHEN** ExoPlayer fires `onPlayerError` with `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED`
- **THEN** the SDK SHALL produce error code `TP-4001`

#### Scenario: Error code for unknown ExoPlayer error
- **WHEN** ExoPlayer fires `onPlayerError` with an error code not in the mapping table
- **THEN** the SDK SHALL produce error code `TP-9001`

#### Scenario: Error code for asset API 404
- **WHEN** `AssetRepository` receives HTTP 404 from the asset API
- **THEN** the SDK SHALL produce error code `TP-3001`

#### Scenario: Error code for internet probe failure
- **WHEN** the internet probe fails to reach any endpoint
- **THEN** the SDK SHALL produce error code `TP-1004`

#### Scenario: CDN 403 maps to TP-2004
- **WHEN** ExoPlayer fires an error during chunk loading
- **AND** the underlying HTTP response code is 403
- **THEN** the SDK SHALL produce error code `TP-2004` (CDN_AUTH_FAILED)

### Requirement: Each error code has a stable tech label
Every error code SHALL have an associated tech label (PascalCase) suitable for Sentry tags, log messages, and developer documentation. The label MUST NOT change between SDK versions.

#### Scenario: Tech label in Sentry
- **WHEN** a TP-1002 error is logged to Sentry
- **THEN** the Sentry event SHALL include a tag `sdk_error_code = "TP-1002"` and `sdk_error_label = "NETWORK_CONNECTION_TIMEOUT"`
