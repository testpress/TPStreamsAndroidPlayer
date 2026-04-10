## Context

The SDK currently couples provider assumptions (TPStreams URLs and payload shape) directly inside playback and download paths. This creates repeated hardcoded endpoint logic and prevents Testpress from using the same codepath. We need to add multi-provider capability while preserving existing public behavior and avoiding large-scale refactoring.

Constraints:
- Existing TPStreams integrations using `TPStreamsSDK.init(orgId)` must keep working.
- Playback and download UI/control flows should not be redesigned in this change.
- Risk should be concentrated in metadata/endpoint seams, not player lifecycle code.

## Goals / Non-Goals

**Goals:**
- Add provider selection at SDK initialization with a backward-compatible default.
- Keep provider-specific endpoint URL construction and response parsing together in clear API service classes.
- Keep downstream playback/download consumers operating on the existing `AssetInfo` model.
- Minimize code churn outside network/parsing integration points.

**Non-Goals:**
- Replacing the current player architecture or introducing full clean-architecture layering everywhere.
- Redesigning `TPStreamsPlayer` UI/control APIs.
- Introducing new public domain models for playback metadata.
- Changing offline storage format or download state management behavior.

## Decisions

1. Add provider-aware SDK config with compatibility default
- Decision: Add a `Provider` enum to the `com.tpstreams.player` package and update `TPStreamsSDK.init(orgId, provider = Provider.TPStreams)`.
- Why: Enables Testpress support with a clean, typesafe API while preserving fallback behavior for existing TPStreams callers without requiring them to handle internal `ApiService` classes.
- Alternatives considered:
  - Replace existing init signature only: rejected because it is a breaking API change.
  - Infer provider from org format: rejected as fragile and implicit.

2. Use backend API service classes
- Decision: Introduce `BaseApiService` with two concrete implementations: `TPStreamsApiService` and `TestPressApiService`.
- Why: Keeps endpoint construction and parsing behavior for each backend in one place, while avoiding extra helper abstractions.
- Alternatives considered:
  - Endpoint builder + parser interface split: rejected as unnecessary complexity for this scope.
  - One giant conditional parser in repository: rejected for readability and maintainability.

4. Preserve `AssetInfo` as unified handoff model
- Decision: Keep existing `AssetInfo` contract for now and map both providers into it.
- Why: Avoids touching broad downstream surfaces in player/download modules.
- Alternatives considered:
  - Introduce a new canonical domain model now: rejected to avoid major refactor.

5. Migrate URL usage sites to active API service
- Decision: Update `AssetRepository`, `MediaItemUtils`, `TPStreamsPlayer` token checks/license templates, and `DownloadClient` dependencies to use `TPStreamsSDK.apiService`.
- Why: Ensures behavior consistency across playback and downloads.
- Alternatives considered:
  - Update repository only: rejected because DRM/token checks would remain TPStreams-only.

## Risks / Trade-offs

- [Risk] Provider edge-case mismatch in live stream status mapping (especially Testpress) → Mitigation: parser-level tests for livestream, DRM/non-DRM, and fallback branches.
- [Risk] Silent regressions for existing TPStreams flows due to endpoint migration → Mitigation: keep TPStreams as default provider and add API service URL/parser unit tests.
- [Risk] Incomplete migration leaving some hardcoded TPStreams URLs → Mitigation: explicit grep checks and a migration checklist for all known call sites.
- [Trade-off] We keep `AssetInfo` and JSONObject-based parsing for minimal churn → Mitigation: document this as intentional and defer typed network models to a later cleanup change.

## Migration Plan

1. Add provider config storage and backward-compatible init overload in SDK config.
2. Add `BaseApiService`, `TPStreamsApiService`, and `TestPressApiService`.
3. Wire repository to active API service endpoint + parsing behavior.
4. Update media item DRM URL and player token-validation/license URL paths to active API service.
5. Update download flow to consume provider-aware behavior consistently.
6. Add focused tests for URL generation and parser behavior.
7. Validate with both provider samples in the example app.

Rollback strategy:
- Revert to TPStreams-only behavior by forcing default provider branch and restoring old direct TPStreams URL templates.
- Since public default init remains unchanged, rollback risk is low and localized.

## Open Questions

- Should Testpress DRM license requests include any provider-specific headers in addition to query token? (Current implementation keeps query-token-only parity.)
- Do we need explicit provider tagging in Sentry logs for this SDK (parity with old SDK diagnostics)?
- For Testpress live-stream completion, should recorded playback fallback behavior exactly mirror old SDK wording/errors or keep current SDK error text?
