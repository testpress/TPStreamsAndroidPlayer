# Video Player Error Codes & Troubleshooting Guide

This guide helps you resolve common playback, network, decoder, API, and DRM issues when integrating and using the **TPStreams Android Player SDK**.

---

## 1000 Series — Core Playback Errors
Errors originating from the core playback engine and player state transitions.

| Error Code | Name & Description | Root Cause | Troubleshooting & Action |
| :--- | :--- | :--- | :--- |
| **1000** | `ERROR_CODE_UNSPECIFIED`<br>General Playback Error | Unexpected runtime interruption or unhandled state in the player core. | Reopen the video or re-initialize player instance. If persistent, check logcat stack traces for player lifecycle issues. |

---

## 2000 Series — Network & I/O Errors
Errors related to network connectivity, media chunk downloading, HTTP status codes, and storage access.

| Error Code | Name & Description | Root Cause | Troubleshooting & Action |
| :--- | :--- | :--- | :--- |
| **2001** | `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`<br>Network Connection Failed | Socket connection failed while fetching video segments or playlist. | • Verify device Wi-Fi/mobile internet connectivity.<br>• Check if active firewall, VPN, or ad-blocker is dropping socket connections. |
| **2002** | `ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT`<br>Connection Timeout | HTTP request timed out due to a slow, unstable, or congested network. | • Test network bandwidth and latency.<br>• Switch to a lower video resolution/bitrate to reduce bandwidth consumption. |
| **2003** | `ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE`<br>Invalid Content Type | Server returned unexpected MIME type (e.g., HTML instead of video). | • Check for public Wi-Fi captive portals requiring browser login.<br>• Ensure proxies/gateways are not returning HTML error/block pages with HTTP 200. |
| **2004** | `ERROR_CODE_IO_BAD_HTTP_STATUS`<br>Bad HTTP Status | Media segment or manifest request returned an HTTP 4xx or 5xx status code. | • Check if signed stream/segment URL expired.<br>• Check CDN status and verify stream accessibility. |
| **2005** | `ERROR_CODE_IO_FILE_NOT_FOUND`<br>Downloaded File Not Found | Local media file for an offline download is missing on disk. | Re-download the video asset using the SDK's download manager. |
| **2006** | `ERROR_CODE_IO_NO_PERMISSION`<br>Storage Permission Denied | App lacks read permission for the local storage path where the video is stored. | Ensure storage permissions are granted in app settings or check scoped storage configuration. |
| **2007** | `ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED`<br>Cleartext Traffic Blocked | Attempting to stream over unencrypted `http://` on Android 9+ (API 28+). | Ensure all video URLs use `https://`, or configure `android:usesCleartextTraffic="true"` in AndroidManifest.xml if HTTP is required. |
| **2008** | `ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE`<br>Read Position Out of Range | Requested byte offset exceeds content length. | Re-download the offline file, or inspect stream file integrity. |

---

## 4000 Series — Audio & Video Decoder Errors
Errors related to device hardware codecs, software decoders, and format limits.

| Error Code | Name & Description | Root Cause | Troubleshooting & Action |
| :--- | :--- | :--- | :--- |
| **4001** | `ERROR_CODE_DECODER_INIT_FAILED`<br>Decoder Initialization Failed | Hardware `MediaCodec` initialization failed (instance limit reached or driver lockup). | • **Restart device** to release leaked hardware decoder instances.<br>• Close other apps using camera or background video players.<br>• Check device codec limits (SDK falls back to L3 if secure L1 decoder fails and `allowFallbackToL3` is enabled). |
| **4002** | `ERROR_CODE_DECODER_QUERY_FAILED`<br>Decoder Query Failed | Querying device `MediaCodecList` for codec capabilities failed in OS. | **Restart device** to reset Android media services, or verify system OS updates. |
| **4003** | `ERROR_CODE_DECODING_FAILED`<br>Decoding Failed | Video stream frame corruption or MediaCodec runtime error during active playback. | • Switch to a lower video resolution/bitrate track in player settings.<br>• Check encoded video file integrity (keyframe interval, NAL units). |
| **4004** | `ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES`<br>Format Exceeds Capabilities | Selected track resolution/framerate exceeds the device hardware decoder capability. | Select a lower resolution (e.g., choose 1080p or 720p instead of 4K) supported by the chipset. |
| **4005** | `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED`<br>Unsupported Video Format | Device lacks a hardware or software decoder for the requested codec (e.g., AV1 or HEVC). | Ensure H.264 fallback tracks are available in the stream, or test on a supported device. |

---

## 5000 Series — SDK API & Asset Access Errors
Errors returned when communicating with TPStreams backend APIs for asset metadata and permissions.

| Error Code | Name & Description | Root Cause | Troubleshooting & Action |
| :--- | :--- | :--- | :--- |
| **5001** | `INVALID_ASSETS_ID`<br>Asset Not Found | Video asset not found on server (HTTP 404). | • Verify `assetId` is correct and properly formatted.<br>• Confirm initialized `orgId` matches the organization owning the asset.<br>• Check TPStreams dashboard to ensure video is published and active. |
| **5002** | `INVALID_ACCESS_TOKEN_FOR_ASSETS`<br>Access Denied | Authentication/authorization failure (HTTP 401/403). | • Check `accessToken` expiration timestamp.<br>• Verify token signature and permissions for the specified `assetId`.<br>• Generate a fresh `accessToken` via backend and retry. |
| **5004** | `NETWORK_CONNECTION_FAILED`<br>API Network Connection Failed | Initial `fetchAssetInfo()` network call failed before reaching server. | • Test internet connectivity.<br>• Check DNS resolution for TPStreams API endpoint.<br>• Check if firewall, proxy, or VPN is blocking the API domain. |
| **5005** | `SERVER_ERROR`<br>Server Error | TPStreams backend or CDN returned HTTP 500–599. | • Check TPStreams service status.<br>• Implement retry logic with exponential backoff. |
| **5100** | `UNSPECIFIED`<br>Unspecified API Error | Unhandled HTTP status code, malformed response, or generic exception. | • Inspect logcat / application logs for stack traces.<br>• Contact TPStreams support with the **Player ID** displayed on screen. |

---

## 6000 Series — Security & DRM License Errors
Errors related to Widevine DRM protected content, device certificates, and license acquisition.

| Error Code | Name & Description | Root Cause | Troubleshooting & Action |
| :--- | :--- | :--- | :--- |
| **6001** | `ERROR_CODE_DRM_SCHEME_UNSUPPORTED`<br>DRM Scheme Unsupported | Widevine DRM modular UUID is not supported by the device. | Test on a certified Android device with official Google Play services (emulators require Google APIs system image). |
| **6002** | `ERROR_CODE_DRM_PROVISIONING_FAILED`<br>DRM Provisioning Failed | Device certificate provisioning failed with Google provisioning server. | • Ensure device **Date & Time** is set to **Automatic / Network-provided**.<br>• Test on stable internet connection (*SDK auto-persists L3 fallback if device is unprovisionable and `allowFallbackToL3` is enabled*). |
| **6003** | `ERROR_CODE_DRM_CONTENT_ERROR`<br>DRM Content Error | Encrypted media samples could not be decrypted. | Verify DRM packaging/encryption parameters and re-test with a stable connection. |
| **6004** | `ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED`<br>DRM License Acquisition Failed | Fetching license key from TPStreams DRM license server failed. | • **Check Device Clock**: Ensure device **Date & Time** is set to **Automatic** (clock drift causes token signature rejection).<br>• Verify DRM license URL and authentication token validity.<br>• Ensure device has internet connectivity to reach the DRM server. |
| **6005** | `ERROR_CODE_DRM_DISALLOWED_OPERATION`<br>DRM Operation Disallowed | Playback blocked by DRM security policy (e.g., HDCP or screen capture protection). | Disconnect external HDMI displays, and close active screen recording or screen sharing apps. |
| **6006** | `ERROR_CODE_DRM_SYSTEM_ERROR`<br>DRM System Error | Low-level Android `MediaDrm` or OEM crypto HAL system crash. | **Restart device** to reinitialize Android's DRM system service. |
| **6007** | `ERROR_CODE_DRM_DEVICE_REVOKED`<br>DRM Device Revoked | Device Widevine credentials blacklisted by Google. | Update device OS firmware to official release or test on another device. |
| **6008** | `ERROR_CODE_DRM_LICENSE_EXPIRED`<br>Offline DRM License Expired | Validity duration for downloaded offline DRM license expired. | Connect device to internet and open the video — the SDK will automatically trigger background license renewal. |

---

## Still Need Help?
If you've tried the suggested steps and are still experiencing issues:
1. Note down the **Error Code** and **Player ID** shown on the error screen.
2. Reach out to TPStreams support with these details for rapid investigation.
