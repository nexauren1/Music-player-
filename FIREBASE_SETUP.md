# Music Player — Firebase setup

The app is prepared for Firebase, but Firebase registration is intentionally not required to build or use the local player.

## Android package

Use this exact package/application ID in Firebase:

com.musicplayer.app

## Firebase Authentication

Enable:
- Email/Password
- Google

After creating the Android app in Firebase, download `google-services.json` and place it at:

app/google-services.json

Then enable the Google Services Gradle plugin in the root/app build configuration.

For Google Sign-In, register the app signing SHA-1 and SHA-256 fingerprints in Firebase.

## Firestore entitlement

The app expects Premium entitlement at:

users/{uid}/entitlement/premium

Suggested fields:

- plan: "quarterly" or "lifetime"
- verified: true/false
- expiresAtMillis: Unix timestamp in milliseconds for quarterly plans
- updatedAt: server timestamp

Lifetime purchases should use `plan = lifetime` and remain active without an expiration timestamp.

Quarterly purchases should be renewed by the payment backend and receive a new `expiresAtMillis`.

## Premium plans

- Quarterly: USD 5 every 3 months
- Lifetime: USD 30 one-time

The Android client only opens the configured checkout URL. Payment verification and entitlement writing must happen server-side before setting `verified = true`.

## Account rules

Music Player remains usable without an account.

Account creation/sign-in is required only for paid premium access and cloud entitlement.

## Test mode

Before Firebase is connected, the app has a local test allowance of 3 premium equalizer preset previews.
