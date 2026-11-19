# Native account integration · 0.5.1

Updated 16 November 2026. Account sign-in was confirmed working in 0.2. Version 0.3 implements and exercises native entitled playback, title-logo artwork and available-resolution selection.

## Skip events and next episode in 0.6.1

Public `static.crunchyroll.com/skip-events/production/{contentId}.json` metadata supplies recap, intro and credits start/end times in seconds. Legacy `datalab-intro-v2/{contentId}.json` supplies intro startTime/endTime. Requests use no account authorization, have a five-second timeout per source and run separately from stream preparation. The selected audio edition is tried first, then the original episode if needed. Only finite, nonnegative, increasing ranges are accepted. Missing metadata leaves ordinary playback intact; timestamps are never guessed.

`content/v2/discover/up_next/{episodeId}` with locale and preferred audio resolves the following episode even when the current episode came only from a history panel. The result enters the profile-scoped episode index before navigation. The response must identify a different episode in the same series. A failed request is distinct from an empty final-episode response and exposes Retry. The endpoint and marker schema were checked against the reference app's [service calls](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/service.js) and [player handling](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/screen/video.js). The implementation is independent; no upstream application code was copied.

## Default playback in 0.5.1

Account episode playback has reported compatibility with the 0.5.0 Spatial compatibility option on OnePlus 15 / Android 17 / Unbound / XREAL One. Version 0.5.1 makes that same TextureView plus genuine Widevine L3 request the sole playback configuration. The mode selector and display-capability branch are removed. Existing stored mode values cannot override the new default. Device display flags remain read-only diagnostics; session identity, licensing transport, quality selection and account progress behavior are retained.

## Spatial displays introduced in 0.5.0

See [display compatibility](DISPLAY_COMPATIBILITY.md). New mode selection requests a genuine supported L3 CDM instance with TextureView when appropriate; ordinary phone/default display playback retains its prior surface and default CDM. CDM license/output enforcement remains active. Public clear/Widevine playback passed on phone and TV emulators, with actual L3 and keys loaded in Spatial mode. Account credentials were unavailable for an account-license test of the new mode. OnePlus 15 / Android 17 / Unbound / XREAL One was unverified at that release; subsequent reports confirmed it works in Spatial compatibility.

## Account library and subtitle corrections in 0.4.0

The live discover watchlist response supplies episode panels, unlike the earlier fixture that supplied series panels. Those panels are resolved by `episode_metadata.series_id` and hydrated through CMS objects. Account history uses watch-history entries and cloud playheads, including last-modified/played timestamps and fully-watched state. Unsynced or demonstrably newer local progress is preserved during cloud refresh. History pagination respects `meta.next_page` where supplied.

My Content exposes Watched shows, Watchlist, and History. Both played-content views can load every history page the service exposes; refreshing reconciles the already loaded page window, and Watched shows groups those episodes once per series. History represents the latest available checkpoint per episode, not a separate event for each rewatch. Neither cloud playheads nor local demo progress provide a complete playback-session audit log, and unavailable/omitted provider records cannot be reconstructed. Old saved My List and History destinations open the matching content tab.

Styled subtitle manifest URLs come directly from `hardSubs` metadata. Their selected language is rendered by the provider into the licensed video, so authored ASS styling is retained without relying on Media3’s limited ASS text renderer. Off chooses the original/no-subtitle variant; soft sidecars are used only when a styled variant is not available. All variants keep the same authenticated lease and Android DRM path.

## Fresh-login correction in 0.3.1

The 0.3 native playback checks restored an authenticated refresh session and did not exercise the Android password form. The exact fresh-login path was subsequently reproduced returning HTTP 401 / `auth.obtain_access_token.invalid_credentials`. A new-device email does not establish that the OAuth token request completed successfully.

The regression was using the native playback compatibility User-Agent on the password grant. Password grants now use the explicit Sumi sign-in User-Agent (previously CinemaOLED), while API/playback and refresh keep their own compatibility profile. Changing that header made the same credential input succeed through the actual login UI and native episode playback. Mock-server tests now enforce the separation.

Installation identity is retained across retries/logout instead of creating another UUID on every attempt. Authentication diagnostics retain only bounded request-stage/status/provider-code data, never credentials or tokens. Error messages distinguish a rejected sign-in request from a rejected saved/profile session.

Real-account verification now enters credentials through the app’s actual fields, then separately force-stops the app and validates restoration and native playback from the persisted encrypted session. These checks replace reliance on refresh-session seeding for sign-in coverage.

## Verified native path

The old catalogue-only client was rejected by native playback with error `40016` / `Outdated Token`. The native path now uses a matching pinned Android TV API client authorization, versioned native protocol user agent that explicitly identifies Sumi as unofficial, and `/v3/{contentId}/tv/android_tv/play`. The Android TV protocol is used across adaptive layouts because the phone client requires a different OAuth-code sign-in flow. Actual decoding remains Android Media3/MediaDrm on the current device.

The current identifiers are pinned from the public `crunchy-labs/artifacts` protocol reference, Android TV 3.81.0 / build 22375. No real user credential is embedded. Existing older-client refresh sessions may need one fresh sign-in after upgrading.

An authenticated stream returned a DASH manifest and a Widevine license URL. The manifest receives the account ID and playback GUID required by the service. The player uses the returned license endpoint with bearer authorization, content ID and video-token headers. Android’s built-in CDM performs provisioning, license interpretation and protected decoding. The implementation supports both a binary platform-license response and the service’s JSON/base64 transport envelope. It does not extract content keys, impersonate DRM hardware, weaken license constraints or export decrypted media.

Media3 reports actual supported tracks. The Quality menu contains Auto and distinct supported heights; choosing a height installs a video-track override, while Auto clears it. Selection does not reprepare the media or reset position. Saved choices are profile-specific; unavailable choices fall back to Auto on the current stream.

Service subtitle/caption sidecars are passed to Media3 with their actual format/language. Preferred audio selects an available service version when needed, closing the previous lease before opening another. The player never fabricates language or resolution choices.

## Session and progress lifecycle

Native streams have bounded lease renewal based on service metadata. Keepalive is a PATCH with the current playhead query and multipart `position` field, verified against the service protocol. Each stream's own content/token pair is deleted on exit/background; the app never purges other devices’ sessions. New playback waits for previous cleanup to avoid overlapping stream-limit leases.

Local progress is saved on pause, seek, exit and periodic intervals. The account playhead endpoint is updated as well. Sync failures are shown without claiming success. Backgrounding pauses playback and releases the stream; foregrounding reacquires at the saved position.

Requests are bound to the account/profile identity that started them. A 401 from an old profile cannot retry with the newly selected profile. Cleanup uses its captured original authorization, avoiding mutations against another account after a switch.

## Title artwork

`images_localized.content_logo_polychromatic` is preferred, with monochromatic artwork as a fallback. The service’s `{:param}` template is replaced with a bounded image request. Home uses the localized hero background, then the wide artwork or portrait poster. Phone detail headers and portrait cards prefer the tall poster. Image-load failure advances through the available candidates. A missing or unavailable title logo falls back to readable text.

After a short pause on a Home spotlight, the app prepares the adjacent banner and logo in the direction of travel. An unmetered connection also permits the opposite neighbour. A complete browse card supplies both URLs directly. A partial card shares its series metadata lookup with other requests for that show until the next Home catalogue refresh. The carousel and visible artwork use the same Coil memory and disk caches and share in-flight downloads by URL.

While Home or My Content is open on an unmetered connection, the app alternates through watched and saved shows with known poster or logo URLs. It checks the disk cache first and warms missing files one at a time, at least 750 ms apart, with at most 24 attempted downloads per signed-in profile session. On later sessions, existing files leave room in the request budget for more titles. Playback and backgrounding stop this work. The disk cache evicts older artwork as space is needed.

## Credential protection and testing

Passwords go directly to Crunchyroll over HTTPS and are never persisted. Only refresh material and device/account/profile identity are encrypted using Android Keystore in the no-backup directory. Access tokens and signed stream data remain in memory, with redacted `toString` representations. HTTP logging is absent; credential-bearing redirects are disabled. Service errors map to fixed messages rather than exposing raw responses.

The account was supplied through secure entry. The test broker keeps credential/session input in memory and transfers it through an ADB-local socket, not command-line credentials or source files. The fresh-login regression uses the app’s actual password form, and the restart check starts a new app process from the encrypted saved session. Live tests return only rotated session material to that local broker and clear the test installation’s encrypted session. Review artifacts exclude passwords, tokens, signed URLs, license payloads and protected movie capture.

Native verification requires all of: a real entitled descriptor, Android CDM keys-loaded callback, rendered episode frame, advancing playback position, actual quality change and successful stream cleanup. The clear-video demo is not used to establish DRM compatibility. Device results and remaining limitations are in `IMPLEMENTATION.md`.

## References and attribution

Protocol behavior was inspected from [crunchyroll-linux](https://github.com/aarron-lee/crunchyroll-linux), [crunchyroll-rs](https://github.com/crunchy-labs/crunchyroll-rs), [Android client identifiers](https://github.com/crunchy-labs/artifacts), and the [session keepalive reference](https://github.com/ediaz23/crunchyroll-js-api/blob/master/src/services/drm.js). Android integration follows [Media3 DRM](https://developer.android.com/media/media3/exoplayer/drm) and [track selection](https://developer.android.com/media/media3/exoplayer/track-selection). No upstream application implementation, Widevine service certificate or device keys were copied.

The Linux reference’s LICENSE/package discrepancy remains relevant if its code is ever reused. The native code is independently implemented. Fonts, promotional artwork and demo-media attribution are recorded in `THIRD_PARTY.md`.
