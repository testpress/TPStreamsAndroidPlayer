# multi-provider-backend-support Specification

## Purpose
TBD - created by archiving change add-testpress-provider-support. Update Purpose after archive.
## Requirements
### Requirement: SDK initialization SHALL support explicit provider selection
The SDK SHALL allow callers to initialize configuration with both provider and organization code, while preserving the existing initialization method that accepts only organization code.

#### Scenario: Backward-compatible TPStreams initialization
- **WHEN** the caller initializes the SDK with organization code only
- **THEN** the SDK MUST configure provider as TPStreams by default and remain usable without API changes

#### Scenario: Explicit Testpress initialization
- **WHEN** the caller initializes the SDK with provider `TestPress` and a valid organization code
- **THEN** the SDK MUST store and use that provider for metadata, DRM, and token-validation endpoints

### Requirement: Endpoint resolution SHALL be provider-aware and centralized
The SDK SHALL construct asset metadata, DRM license, and token-validation URLs through a centralized provider-aware endpoint resolver.

#### Scenario: TPStreams endpoint resolution
- **WHEN** provider is TPStreams and the SDK needs an asset metadata endpoint
- **THEN** the resolver MUST produce the TPStreams API URL format with organization code, asset id, and access token

#### Scenario: Testpress endpoint resolution
- **WHEN** provider is TestPress and the SDK needs an asset metadata endpoint
- **THEN** the resolver MUST produce the Testpress API URL format with organization code, asset id, and access token

### Requirement: Metadata parsing SHALL support TPStreams and Testpress payloads
The SDK SHALL parse provider-specific metadata responses through provider-specific parsers and return a unified playback metadata object for downstream consumers.

#### Scenario: TPStreams payload parsing
- **WHEN** a TPStreams asset metadata response is received
- **THEN** the TPStreams parser MUST map playback URL, DRM flag, thumbnail, track metadata, live-stream state, and duration into the unified model

#### Scenario: Testpress payload parsing
- **WHEN** a Testpress asset metadata response is received
- **THEN** the Testpress parser MUST map playback URL, DRM flag, thumbnail, live-stream state, and duration into the unified model

### Requirement: Playback and download flows SHALL consume provider-aware endpoints without behavioral regression
Playback and download internals SHALL use provider-aware endpoint and parser outputs without requiring UI or control-layer API changes.

#### Scenario: DRM media item preparation
- **WHEN** DRM is enabled for an asset
- **THEN** the media item builder MUST use provider-aware DRM license endpoint generation and preserve existing DRM playback behavior

#### Scenario: Download/token validation path
- **WHEN** download start or access-token validation is triggered
- **THEN** the SDK MUST use provider-aware endpoint construction and preserve existing TPStreams behavior when provider is not explicitly set

