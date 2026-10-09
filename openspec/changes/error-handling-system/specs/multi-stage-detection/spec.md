## ADDED Requirements

### Requirement: SDK SHALL track playback pipeline stage
The SDK SHALL maintain an internal `PlaybackStage` enum that reflects the current phase of the playback pipeline. The stages SHALL be:

```
IDLE → VALIDATING → FETCHING_ASSET → FETCHING_DRM_LICENSE → PREPARING → PLAYING
```

The stage SHALL be updated before each pipeline transition and SHALL be included in every error context.

#### Scenario: Stage transitions on successful path
- **WHEN** a player is created
- **THEN** stage SHALL be `VALIDATING`
- **WHEN** the asset API call starts
- **THEN** stage SHALL be `FETCHING_ASSET`
- **WHEN** the asset API succeeds and DRM license fetch starts
- **THEN** stage SHALL be `FETCHING_DRM_LICENSE`
- **WHEN** DRM license fetch completes and media item is being prepared
- **THEN** stage SHALL be `PREPARING`
- **WHEN** ExoPlayer enters `STATE_READY` and starts playing
- **THEN** stage SHALL be `PLAYING`

#### Scenario: Stage is captured on error
- **WHEN** an error occurs at any stage
- **THEN** the error code and Sentry event SHALL include the stage at which the error occurred

### Requirement: Each stage has distinct error handling behavior
Errors from different stages SHALL produce different student-facing messages and different Sentry context.

| Stage | Error Message Theme | Sentry Tags |
|-------|-------------------|-------------|
| VALIDATING | Cannot reach server, check internet | stage=validating, probe_result |
| FETCHING_ASSET | Could not load video details | stage=fetching_asset, http_code |
| FETCHING_DRM_LICENSE | Could not verify video rights | stage=fetching_drm_license, drm_error |
| PREPARING | Video could not start, format issue | stage=preparing, decoder_error |
| PLAYING | Playback interrupted, network or decoder | stage=playing, exo_error_code |

#### Scenario: Stage-specific error message
- **WHEN** an error occurs at `FETCHING_ASSET` stage with HTTP 404
- **THEN** the student-facing message SHALL say "This video could not be found. It may have been removed."
- **AND** the error code SHALL be `TP-3001`

#### Scenario: Same underlying error, different stage, different message
- **WHEN** an IOException occurs at `FETCHING_ASSET` stage
- **THEN** the student message SHALL indicate the video details could not be loaded
- **WHEN** an IOException occurs at `PLAYING` stage
- **THEN** the student message SHALL indicate playback was interrupted due to network issues
- **AND** both errors SHALL have distinct error codes (`TP-3003` vs `TP-1003`)

### Requirement: Auth-passed context is detectable
If the asset API call succeeds (HTTP 200), the SDK SHALL record that "auth and API are reachable." If a subsequent playback error occurs (CDN chunk fetch fails), the error message SHALL reflect that the server is reachable but the specific streaming endpoint failed.

#### Scenario: CDN failure after successful asset fetch
- **WHEN** the asset API call succeeds (HTTP 200)
- **AND** ExoPlayer later fires a network-related playback error
- **THEN** the student message SHALL NOT say "check your internet" since it was working
- **AND** the message SHALL indicate a streaming delivery issue
- **AND** the error code SHALL be `TP-1005` (CDN_OR_FIREWALL_BLOCKED)
