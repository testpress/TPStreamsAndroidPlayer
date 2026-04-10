## Why

The current SDK is hard-wired to TPStreams endpoints and payload assumptions, which prevents using the same player stack for Testpress-backed content. We need dual backend support now so both products can use one SDK without duplicating player logic.

## What Changes

- Add provider-aware SDK initialization while preserving existing `TPStreamsSDK.init(orgId)` behavior for backward compatibility.
- Add backend API service classes (`BaseApiService`, `TPStreamsApiService`, `TestPressApiService`) that each own endpoint URL construction and response parsing.
- Route data retrieval and media preparation flows through provider-aware endpoint/parsing layers without major refactoring of player UI or controls.
- Keep `AssetInfo` as the unified output model for playback and download flows (moved under network model package).

## Capabilities

### New Capabilities
- `multi-provider-backend-support`: Allow the SDK to fetch playback metadata and DRM/token endpoints from either TPStreams or Testpress using provider-aware routing and parsing.

### Modified Capabilities
- None.

## Impact

- Affected code:
  - SDK initialization/config (`TPStreamsSDK`)
  - Asset metadata fetching/parsing (`data/AssetRepository` and backend API service classes)
  - Media item and DRM URL construction (`util/MediaItemUtils`)
  - Token validation and URL usage in player/download paths (`TPStreamsPlayer`, `download/DownloadClient`)
- API impact:
  - Adds an overload/init path with provider selection.
  - Existing TPStreams-only initialization remains supported.
- Dependencies:
  - No new external runtime dependency is required.
- Systems:
  - Playback and download flows become provider-aware while retaining current TPStreams behavior as default.
