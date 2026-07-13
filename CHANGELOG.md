# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.2.1 - 2026-07-11
### Added
- Improved error reporting with additional device and network context for faster debugging

### Fixed
- Fix MediaTek secure decoder NO_MEMORY crash during fullscreen transitions by explicitly releasing video surface before player detach

## [1.2.0] - 2026-07-02
### Added
- Add `setMaxResolution` API to limit maximum playback quality and automatically hide unallowed resolutions in the settings panel (#105)
### Changed
- Update `setVideoResolution` API to explicitly set the user's preferred resolution rather than just setting a track selector upper bound (#106)

## [1.1.20] - 2026-06-25
### Fixed
- Fix SentryLogger methods not returning the Sentry event ID, resolving a build error in network diagnostics integration

## [1.1.19] - 2026-06-25
### Added
- Add network diagnostics and playback error classification with automatic retry handling for recoverable network issues
- Add request URLs in Sentry error reports to improve debugging of asset loading failures

### Fixed
- Asset loading failures now include request URLs in error reports for better troubleshooting of invalid parameters, expired tokens, and incorrect endpoint generation

## [1.1.18] - 2026-06-12
### Fixed
- Gracefully handle HLS live stream pauses by suppressing `PlaylistStuckException` to prevent player crashes and unnecessary Sentry logs.

## [1.1.17] - 2026-05-11
### Added
- Added public API to enable or disable auto-fullscreen on device rotation in Android.

## [1.1.16] - 2026-05-05
### Fixed
- Prevent fullscreen reattachment from showing the loading spinner when playback is already ready.

## [1.1.15] - 2026-05-05
### Fixed
- Prevent fullscreen transitions from crashing with `Detaching surface timed out` by detaching and reattaching the player around view reparenting.

## [1.1.14] - 2026-04-30
### Fixed
- Improve Testpress live stream notice messages and recorded states handling.

## [1.1.13] - 2026-04-28
### Added
- Automatic AES-128 authentication support for both TPStreams and Testpress backends.
- Stateless "Token Handoff" logic to prevent manifest signature invalidation.

### Fixed
- Resolved playback failures for AES-protected videos where encryption keys were being rejected.

## [1.1.13-beta.2] - 2026-04-24
### Added
- Implement clock drift diagnostics to track time differences between device and server in error reports.
- Add `ServerDateHeaderInterceptor` to capture server time from HTTP responses.
- Enrich Sentry error logs with human-readable time drift and timing context.

## [1.1.13-beta.1] - 2026-04-21
### Added
- Enable decoder fallback to automatically roll over to software decoders on hardware failure.
- Include global auth headers in token validation to support extended authentication flows.
- Add `TestpressSDK` and `TestpressPlayer` wrappers for simplified authentication and initialization for testpress usecases.

## [1.1.12] - 2026-04-13
### Added
- Multi-provider support for TPStreams and Testpress backends (#90)
- Integrated bottom navigation in the example app for managing multiple providers and downloads.

## [1.1.11-beta.1] - 2026-04-02
### Added
- Add `CodecManager` for real-time hardware decoder instance tracking and capacity diagnostics.
- Add `PlaybackHistoryManager` to maintain a global 500-line buffer of player events.
- Attach full playback history to Sentry crash reports for enhanced production debugging.
- Add support for `onAccessTokenExpired` event in `TPStreamsPlayer.Listener` for dynamic token refreshing during playback and renewal.

### Fixed
- Resolve offline DRM playback failure where metadata JSON in the download request incorrectly blocked initialization.
- Fix DRM background license renewal flow to correctly handle expired licenses for downloaded content.

## [1.1.10] - 2026-03-10
### Fixed
- Prevent audio capture for DRM-protected videos (#85)

## [1.1.9] - 2026-02-25
### Added
- Expose public `startDownload` API in `DownloadClient` (#82)
- Replace hardcoded download toasts with event-driven callbacks (#83)
- Update Example App UI for testing and downloads (#84)
### Fixed
- Update delete event signature and prevent duplicate notifications
### Changed
- Centralize media configuration and asset metadata handling (#81)

## [1.1.8] - 2026-02-10
### Fixed
- Fix player release crash and background playback issue (#80)

## [1.1.7] - 2026-01-28
### Added
- Add automatic retry mechanism for network failures (#79)

## [1.1.6] - 2026-01-13
### Fixed
- Fix bottom sheets scrollable to prevent content cut-off (#78)
### Changed
- Refactor to `BaseBottomSheet` class and modernize bottom sheet styling (#75)

## [1.1.5] - 2026-01-08
### Fixed
- Fix playlist navigation buttons for standalone playback (#74)
- Fix jarring loading UI flickering during stable playback (#73)

## [1.1.4] - 2025-12-31
### Added
- Add support for launching player in fullscreen mode (#72)

## [1.1.3] - 2025-12-16
### Fixed
- Fix download option restriction for live streams
- Fix live badge visibility issue

## [1.1.2] - 2025-12-16
### Added
- Add live stream playback support (#71)

## [1.1.1] - 2025-12-04
### Fixed
- Fix external listener preservation to restore token refresh

## [1.1.0] - 2025-11-19
### Fixed
- Fix error overlay display across all view contexts

## [1.0.19] - 2025-11-19
### Fixed
- Fix player error overlay initialization

## [1.0.18] - 2025-11-19
### Added
- Add error overlay and loading states to player view (#70)
- Integrate Sentry for error tracking and enhance error handling (#69)

## [1.0.17] - 2025-09-12
### Added
- Add download state change callback to DownloadClient (#68)

## [1.0.0] - 2025-06-20
### Added
- Initial release with core playback and DRM support

[1.2.0]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.20...1.2.0
[1.1.20]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.19...1.1.20
[1.1.19]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.18...1.1.19
[1.1.18]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.17...1.1.18
[1.1.17]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.16...1.1.17
[1.1.16]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.15...1.1.16
[1.1.15]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.14...1.1.15
[1.1.14]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.13...1.1.14
[1.1.13]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.13-beta.1...1.1.13
[1.1.13-beta.2]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.13-beta.1...1.1.13-beta.2
[1.1.13-beta.1]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.12...1.1.13-beta.1
[1.1.12]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.11-beta.1...1.1.12
[1.1.11-beta.1]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.10...1.1.11-beta.1
[1.1.10]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.9...1.1.10
[1.1.9]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.8...1.1.9
[1.1.8]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.7...1.1.8
[1.1.7]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.6...1.1.7
[1.1.6]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.5...1.1.6
[1.1.5]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.4...1.1.5
[1.1.4]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.3...1.1.4
[1.1.3]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.2...1.1.3
[1.1.2]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.1...1.1.2
[1.1.1]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.0.19...1.1.0
[1.0.19]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.0.18...1.0.19
[1.0.18]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.0.17...1.0.18
[1.0.17]: https://github.com/testpress/TPStreamsAndroidPlayer/compare/1.0.16...1.0.17
