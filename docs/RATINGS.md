# Show ratings

Version 0.7.0 adds a Ratings section above the synopsis on phone, tablet and TV show details. Crunchyroll uses its original 5-point scale; MyAnimeList and IMDb use 10-point scales. MyAnimeList also displays its overall score rank, rather than its popularity rank. The layout wraps cards on narrow windows and with larger text.

Version 0.7.1 replaces the original Jikan endpoint with Tenrai. Jikan’s public API was [discontinued on October 1, 2026](https://github.com/jikan-me/jikan-rest/issues/613), which caused the MyAnimeList failures in 0.7.0. Crunchyroll and IMDb integrations are unchanged by this fix.

## Sources

- **Crunchyroll:** authenticated GET `content-reviews/v2/user/{accountId}/rating/{series|movie_listing}/{contentId}` through the existing profile session. Only the community `average` is shown. The viewer’s own `rating` is never used as the community score. The request shape and response fields were checked against the reference [Crunchyroll media rating method](https://github.com/crunchy-labs/crunchyroll-rs/blob/master/src/media/anime/impl.rs) and [review response schema](https://github.com/ediaz23/crunchyroll-js-api/blob/master/src/types.js).
- **MyAnimeList:** [Tenrai anime search](https://api.tenrai.org/documentation) at `https://api.tenrai.org/v1/anime`, using the selected entry’s `score` and `rank`. Tenrai supplies cached public MyAnimeList catalogue data with the same response schema. The `rating` field is an age classification and is ignored. No API key or MyAnimeList sign-in is required. Individual MAL entries may correspond to a single season, while Crunchyroll groups seasons into a series.
- **IMDb:** Stremio’s public Cinemeta catalogue search followed by metadata for the matched IMDb ID. Only `imdbRating` is used, as defined in the [Stremio metadata schema](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/meta.md). A metadata response must retain the matched ID and identify Animation as a genre.

External metadata uses a separate HTTP client with no authorization or cookie headers. Crunchyroll credentials and session tokens are never sent to Tenrai or Cinemeta. Searches send the selected show’s title; IMDb metadata requests use its matched IMDb identifier. No extra external account is needed.

## Matching and availability

Search order is never treated as identity. The Crunchyroll title must exactly match a provider title or MAL alias after normalizing case, Unicode accents, punctuation and spacing. Season numbers and sequel names are retained. When Crunchyroll supplies a release year, the provider year must agree. Multiple qualifying IDs produce an unavailable state. Movies and series use separate matching paths. These conservative rules can leave legitimate titles unavailable when the services use different names or release years.

Null, nonfinite, zero and out-of-scale scores are unavailable rather than shown as zero. Missing or nonpositive MAL rank is unranked. Missing matches stay unavailable; network, HTTP and malformed-response failures expose Retry ratings. Retrying also restarts any pending sources, preserving successful scores. Optional rating failures do not enter the episode error state or block playback.

Requests run separately for each provider and are cancelled on show changes, playback entry, Back, logout and account/profile changes. Late results cannot update another show or account. Public requests have a ten-second timeout per request and a 22-second overall source deadline; Crunchyroll shares the overall deadline. MyAnimeList requests are serialized and spaced at least 2.1 seconds apart, below Tenrai’s public limits of 4 requests per second and 120 per minute. A bounded in-memory cache keeps successful scores for six hours and missing matches for 30 minutes. Errors and cancellations are not cached.

Demo mode uses bundled sample scores and ranks, explicitly labeled **Sample ratings · Demo data**, and does not call rating services.

## Validation

JVM tests exercise the authenticated Crunchyroll review path, score/rank parsing, exact alias and year matching, ambiguous results, missing scores, movie routing, live-action rejection, caching/expiry, provider errors, retries, cancellation, and locale-independent score formatting. Android tests exercise the actual details flow, show changes and activity recreation, loading/unavailable/error states, retry interaction, card wrapping, large text, and the existing season-scroll anchor.

The regression test verifies the default production MyAnimeList host and route as well as parsing an observed Tenrai response. The explicit `MyAnimeListLiveTest` calls the production repository on Android for Frieren, Solo Leveling and DAN DA DAN, requiring a real score and rank for every title. This network-dependent check is excluded from the default instrumentation suite and must be selected explicitly. It writes only public rating results to `live-myanimelist.json` in the app’s external files directory.

See `artifacts/v0.7.1/build-manifest.json` for current test results and the APK checksum; `artifacts/v0.7.0/build-manifest.json` preserves the original feature’s validation. Crunchyroll review requests were verified with an authenticated mock session during initial development; the user subsequently confirmed that Crunchyroll and IMDb ratings load in the installed app. Physical hardware was not retested for the 0.7.1 source change.
