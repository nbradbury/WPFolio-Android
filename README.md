# <img src="art/ic_launcher-playstore.png" alt="" width="48" align="absmiddle"> WPFolio

A gallery-first reader for Android. It follows WordPress.com subscriptions and any RSS or Atom feed, and lets each post's featured image supply the colour.

## Setup

1. Open the project in Android Studio, or build with `./gradlew :app:assembleDebug`.
2. To enable WordPress.com sign-in, register an app at https://developer.wordpress.com/apps/ with the redirect URL `wpfolio://oauth`, then set its client ID in `WpcomAuth.CLIENT_ID`. Sign-in uses the OAuth implicit flow, so there's no client secret. Until the ID is set the app works in RSS-only mode.

   Implicit-flow tokens can't be refreshed. When one expires the app signs out and asks you to sign in again.

## How it works

- **Room is the source of truth.** Screens observe DAO flows. `ReaderRepository` writes into Room from WordPress.com (when signed in) or on-device RSS fetching (local subscriptions).
- **Signed in:** every follow, WordPress or plain RSS, goes through the WordPress.com Reader API, so subscriptions sync across devices. Feeds added while signed out stay on-device until you move them from the account sheet.
- **Accents:** `AccentSampler` samples each featured image with Palette and caches the colour and aspect ratio on the post.
- **Theme:** light themes (Light, Paper) use the magazine feed; Dark uses the rail-and-list feed.

## Tests

`./gradlew :app:testDebugUnitTest` covers the feed parser, feed discovery, WordPress.com JSON mapping, article HTML blocks, and repository behaviour (Robolectric with in-memory Room).

## Fonts

Instrument Sans and Atkinson Hyperlegible Next are bundled under the SIL Open Font License; see `app/src/main/assets/licenses/`.
