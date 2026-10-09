## ADDED Requirements

### Requirement: Error screen SHALL display student-facing message
The error overlay SHALL show a plain-language message that tells the student what happened, whether it's their side or the platform side, and what they can do. The message MUST NOT contain technical codes, player IDs, or URLs.

#### Scenario: Error message per category
- **WHEN** the error code is TP-1004 (no internet)
- **THEN** the message SHALL be: "No internet connection. Check your WiFi or mobile data and try again."
- **WHEN** the error code is TP-1005 (CDN blocked)
- **THEN** the message SHALL be: "Video playback failed. Your internet is working but the video server may be blocked. Check if a VPN or firewall is active."
- **WHEN** the error code is TP-2001 (auth invalid)
- **THEN** the message SHALL be: "Your session has expired. Please try logging in again."
- **WHEN** the error code is TP-3001 (asset not found)
- **THEN** the message SHALL be: "This video could not be found. It may have been removed or the link may be incorrect."
- **WHEN** the error code is TP-4001 (DRM license failed)
- **THEN** the message SHALL be: "Could not verify video rights. This may be a temporary issue — please try again."
- **WHEN** the error code is TP-5001 (decoder init failed)
- **THEN** the message SHALL be: "Your device cannot play this video format. Try restarting your device or contact support."
- **WHEN** the error code is TP-9001 (unspecified)
- **THEN** the message SHALL be: "Something went wrong. If this continues, please contact support."

### Requirement: Error screen SHALL include a retry button
The error overlay SHALL include a "Try Again" button. When tapped, the SDK SHALL attempt to restart playback from the beginning of the pipeline.

#### Scenario: Retry button tap
- **WHEN** the user taps "Try Again"
- **THEN** the SDK SHALL reset the stage to `VALIDATING`
- **AND** re-run the full pipeline (asset fetch → prepare → play)
- **AND** reset the RetryCoordinator attempt counter

### Requirement: Error screen SHALL show retry button only for retriable errors
If the error category is non-retriable (auth, asset not found, decoder failure), the "Try Again" button SHALL be hidden.

#### Scenario: No retry button on non-retriable error
- **WHEN** the error code is TP-2001 (auth)
- **THEN** the error overlay SHALL NOT show a "Try Again" button

### Requirement: Error screen SHALL show expandable technical details
A collapsible "Show technical details" section SHALL be available. When expanded, it SHALL display:
- SDK Error Code (TP-XXXX) — copyable
- Player ID — copyable (uses existing `playbackSessionId` from TPStreamsPlayer)
- Connection type (WiFi/cellular)
- Network details (link speed for WiFi; network type for cellular)
- Playback stage when error occurred

Signal strength and SSID SHALL NOT be included in the diagnostics. Both require `ACCESS_FINE_LOCATION` on Android 8+/8.1+, and the SDK must never request location permissions. WiFi link speed (from `WifiInfo.getLinkSpeed()`) is available without extra permissions and provides sufficient diagnostic value.

#### Scenario: Expand technical details
- **WHEN** the user taps "Show technical details"
- **THEN** a section SHALL expand below the message showing the diagnostic data
- **AND** the label SHALL change to "Hide technical details"
- **WHEN** the user taps "Hide technical details"
- **THEN** the section SHALL collapse

#### Scenario: Copy error details
- **WHEN** the technical details section is expanded
- **THEN** a "Copy details" button SHALL be visible
- **WHEN** the user taps "Copy details"
- **THEN** the error code, player ID, stage, and network info SHALL be copied to the clipboard as formatted text

### Requirement: Error screen SHALL include contact support link
A "Contact support" link or button SHALL be shown below the technical details section. The behavior SHALL be configurable via `TPStreamsSDK.setContactSupportCallback(callback)`. If no callback is configured, the SDK SHALL fall back to opening the default email client. The SDK MUST NOT hardcode a support email address or URL.

#### Scenario: Contact support with callback
- **WHEN** the user taps "Contact support"
- **AND** a contact support callback is configured via TPStreamsSDK
- **THEN** the SDK SHALL invoke the callback with the error code, player ID, and stage as parameters
- **AND** NOT open the email client

#### Scenario: Contact support fallback
- **WHEN** the user taps "Contact support"
- **AND** no callback is configured
- **THEN** the SDK SHALL open the default email client
- **AND** the subject SHALL be: "TPStreams Player Error: TP-XXXX"
- **AND** the body SHALL include the error code, player ID, stage, and network info from the diagnostics section

### Requirement: Retry UX transition is defined
When the RetryCoordinator signals a retry, the error overlay SHALL NOT be shown. The buffering indicator SHALL be shown during the backoff delay. Only when all retry attempts are exhausted SHALL the error overlay appear.

#### Scenario: Retry UX during backoff
- **WHEN** a retriable error occurs
- **AND** the RetryCoordinator signals retry with 1s backoff
- **THEN** the buffering indicator SHALL be shown
- **AND** the error overlay SHALL NOT be shown
- **WHEN** the retry succeeds after backoff
- **THEN** the buffering indicator SHALL be hidden
- **AND** playback continues normally

#### Scenario: Retry UX after final failure
- **WHEN** all 3 retry attempts have failed
- **THEN** the buffering indicator SHALL be hidden
- **AND** the error overlay SHALL be shown with the final error
- **AND** the "Try Again" button SHALL remain enabled (tapping it resets the retry counter and restarts the pipeline)

### Requirement: Error overlay SHALL be dismissible
The user SHALL be able to dismiss the error overlay. Dismissing SHALL return the player to the IDLE state or close the player view depending on the integration context.

#### Scenario: Dismiss error
- **WHEN** the user taps outside the error card or a close button
- **THEN** the error overlay SHALL be hidden
- **AND** the player SHALL return to its initial state
