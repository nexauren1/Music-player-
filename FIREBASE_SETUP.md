# Music Player — Firebase setup

The app is prepared for Firebase, but Firebase registration is intentionally not required to build or use the local player.

## Android package

Use this exact package/application ID in Firebase:

com.nexauren.musicplayer2

## Firebase Authentication

Enable:
- Email/Password
- Google

After creating the Android app in Firebase, download `google-services.json` and place it at:

app/google-services.json

Then enable the Google Services Gradle plugin in the root/app build configuration.

For Google Sign-In, register the app signing SHA-1 and SHA-256 fingerprints in Firebase.

Production signing fingerprints:
- SHA-1: 33:A0:BB:D9:64:C3:38:E3:BF:82:45:41:0F:11:0C:8F:31:67:D3:A8
- SHA-256: 15:C1:41:6C:53:47:F3:2C:FD:0F:68:03:94:E8:D2:F9:22:CE:0C:0C:14:96:7B:7C:D9:E2:25:E6:DB:DD:31:9F

After registering the new Android app, download the new `google-services.json` and replace the repository copy.

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

The Android client only opens the configured checkout URL and reads its own entitlement. Payment verification and entitlement writing happen in the Cloudflare Worker after PayPal confirms the purchase.

The Worker needs these Cloudflare secrets:
- `FIREBASE_PROJECT_ID`
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`

Use a Firebase service-account key with permission to write Firestore. Never commit the JSON key or paste the private key into GitHub/source code.

## Account rules

Music Player remains usable without an account.

Account creation/sign-in is required only for paid premium access and cloud entitlement.

## Test mode

Before Firebase is connected, the app has a local test allowance of 3 premium equalizer preset previews.
