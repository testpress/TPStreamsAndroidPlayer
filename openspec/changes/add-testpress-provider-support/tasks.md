## 1. SDK Provider Configuration

- [x] 1.1 Extend `TPStreamsSDK` with stored API service and org configuration.
- [x] 1.2 Keep `TPStreamsSDK.init(orgId)` backward compatible by defaulting to `TPStreamsApiService`.
- [x] 1.3 Add explicit initialization overload that accepts `BaseApiService` and org code.

## 2. API Service and Parsing Core

- [x] 2.1 Add `BaseApiService` with endpoint methods and `parseAsset`.
- [x] 2.2 Implement `TPStreamsApiService` with TPStreams endpoint and parsing logic.
- [x] 2.3 Implement `TestPressApiService` with Testpress endpoint and parsing logic based on old SDK response fields.
- [x] 2.4 Move `AssetInfo` to network model package and update imports.

## 3. Integrate Provider-Aware Flow

- [x] 3.1 Update `AssetRepository` to call `TPStreamsSDK.apiService` for endpoint and parsing.
- [x] 3.2 Update `MediaItemUtils` DRM URL construction to use `TPStreamsSDK.apiService`.
- [x] 3.3 Update token validation and license URL paths in `TPStreamsPlayer` to use `TPStreamsSDK.apiService`.
- [x] 3.4 Update `DownloadClient` integration points to use provider-aware repository behavior.

## 4. Validation and Compatibility Checks

- [x] 4.1 Add API service unit tests for URL outputs for both TPStreams and Testpress.
- [x] 4.2 Run regression checks to confirm TPStreams default behavior is unchanged when provider is not explicitly set.
- [x] 4.4 Verify no remaining hardcoded TPStreams endpoint strings exist in provider-sensitive paths.
