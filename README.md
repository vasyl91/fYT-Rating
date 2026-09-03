# fYT Rating

Read and change the like status of a YouTube video from another application.

fYT Rating signs in to your Google account, shows whether you have liked a given
video, and lets you like or unlike it. It also answers the same two questions
for other applications on the device that you have explicitly allowed — so a car
head unit launcher, a widget or a remote can show and control your rating
without handling Google authorisation itself.

There is no server behind it. Nothing is collected, nothing is uploaded, and the
only service contacted is the YouTube Data API, on your behalf.

## Why it exists

YouTube does not tell other applications whether the playing video is liked. It
publishes no rating and no like or dislike actions to the media session, so
anything outside the YouTube app is blind to it. The only reliable source is the
YouTube Data API, which needs the account owner's authorisation.

Rather than have every launcher, widget and companion app request that
authorisation for itself, fYT Rating requests it once and answers on their behalf
— but only for applications you have added by hand.

## Using it

**On its own.** Sign in, paste a video id or a YouTube link, and the video's
thumbnail, title and your rating appear. The button likes or unlikes it.

**From another application.** Open *Allowed applications*. An application that
has asked and been refused is listed under *Asked for access*; tap it and
confirm to allow it. Access can be withdrawn at any time from the same screen.

Nothing is allowed by default and nothing is added automatically.

## What it asks of your account

One scope: `https://www.googleapis.com/auth/youtube.force-ssl`, used for exactly
two calls:

| Call | Purpose |
|---|---|
| `videos.getRating` | reads your rating of a video |
| `videos.rate` | sets your rating when you ask it to |

The narrower `youtube.readonly` scope cannot be used. `videos.getRating` returns
the signed-in user's own rating and rejects that scope with
`ACCESS_TOKEN_SCOPE_INSUFFICIENT`.

Authorisation is handled by Google Play services through Android's standard
`AccountManager`. You pick the account and approve the consent screen drawn by
the system. The application never sees or asks for your password, and no client
secret is embedded in it.

Signing out revokes the authorisation at Google, not just locally: the
application disappears from your account's connected apps.

## Permissions

- `INTERNET`: Required to communicate directly with the YouTube Data API.
- `QUERY_ALL_PACKAGES`: Required on Android 11+ (API level 30+) to query installed applications on the device and resolve their human-readable labels and icons in *Allowed applications* (`AllowedPackages`), overcoming Android 11+ package visibility restrictions.

Note that `GET_ACCOUNTS` is not required because the account is chosen via a picker drawn by Google Play services (`AccountManager`), which does not require enumerating all accounts on the device.

## How other applications talk to it

Applications communicate with fYT Rating using broadcast intents and `PendingIntent` objects for identity verification and callback delivery.

### Actions

- `vasyl.fytrating.action.GET_STATUS`: Queries whether fYT Rating is signed in and whether the calling application is allowed. This request is answered even if the caller is not on the allow list yet.
- `vasyl.fytrating.action.GET_RATING`: Requests the current rating (`like`, `dislike`, or `none`) for a given video ID.
- `vasyl.fytrating.action.SET_RATING`: Sets the rating (`like`, `dislike`, or `none`) for a given video ID.

### Request Format

A caller sends a broadcast containing:
1. `video_id` (Extra String): The 11-character YouTube video ID or full URL.
2. `rating` (Extra String): Target rating (`like`, `dislike`, or `none`) when using `SET_RATING`.
3. `identity` (Extra `PendingIntent`): An immutable `PendingIntent` created by the caller. fYT Rating inspects `getCreatorPackage()` on this intent to securely identify the requesting app without trusting unverified extras.
4. `callback` (Extra `PendingIntent`): A mutable `PendingIntent` with a unique action through which fYT Rating sends the broadcast reply back.

#### Example Request

```java
Intent request = new Intent("vasyl.fytrating.action.GET_RATING");
request.setPackage("vasyl.fytrating");
request.putExtra("video_id", "dQw4w9WgXcQ");

// Set explicit receiver component for reliable delivery on custom head units/ROMs
request.setComponent(new ComponentName("vasyl.fytrating", "vasyl.fytrating.RatingBridgeReceiver"));
request.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

// Identity: Proves caller package via PendingIntent creator
PendingIntent identity = PendingIntent.getBroadcast(
        context, reqId + 1000000, new Intent(), PendingIntent.FLAG_IMMUTABLE);
request.putExtra("identity", identity);

// Callback: Receiver where the result broadcast will be delivered
IntentFilter filter = new IntentFilter(UNIQUE_RESULT_ACTION);
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
} else {
    context.registerReceiver(receiver, filter);
}

PendingIntent callback = PendingIntent.getBroadcast(
        context, reqId,
        new Intent(UNIQUE_RESULT_ACTION).setPackage(context.getPackageName()),
        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
request.putExtra("callback", callback);

context.sendBroadcast(request);

```

### Response Format

The reply arrives on the callback broadcast with the following extras:

* **Status Reply (`GET_STATUS`)**:
  * `signed_in` (boolean): `true` if a Google account is connected.
  * `allowed` (boolean): `true` if the calling package has been allowed by the user.


* **Rating Reply (`GET_RATING` / `SET_RATING`)**:
  * `video_id` (String): The target video ID.
  * `rating` (String): `like`, `dislike`, or `none`.
  * `error` (String): Present only on failure. Possible values:
  * `not_allowed`: The calling app is not on the user's allow list.
  * `not_signed_in`: No Google account is connected in fYT Rating.
  * `bad_request`: Missing or malformed video ID / rating parameter.
  * `unavailable`: API error, network failure, or unconfirmed lookup.
  * `made_for_kids`: Video is marked as made for kids (ratings unavailable on YouTube).





### Waking the Bridge (`WakeActivity`)

On Android head units or devices with aggressive power management, the bridge process may be frozen, killed, or in a stopped state where broadcast delivery is suppressed.

Applications can silently wake fYT Rating by launching `vasyl.fytrating.WakeActivity`. This activity uses `Theme.NoDisplay` and finishes immediately without showing any UI, clearing the stopped state and putting the process back into an active standby state:

```java
Intent wake = new Intent()
        .setComponent(new ComponentName("vasyl.fytrating", "vasyl.fytrating.WakeActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
context.startActivity(wake);

```

### Important Notes for Integrators

* **An application that has never been launched receives no broadcasts.** Opening `fYT Rating` once after installation or force stop clears the stopped state.
* **Waking idle/stopped processes.** Starting `WakeActivity` prior to requests ensures the background process is reachable on head unit ROMs.
* **Videos made for kids never report a rating.** YouTube returns `none` for them regardless of actual rating. The bridge reports `made_for_kids` error code so callers can display an "unknown" state rather than claiming the video is unrated.

## Building

Standard Android project; no dependencies beyond the framework.

```
./gradlew assembleRelease
```

To run your own build against Google, register an OAuth client of type
**Android** in the Google Cloud console for the package `vasyl.fytrating` with
your signing certificate's SHA-1 fingerprint, and enable the **YouTube Data API
v3**. No credential is copied into the source: Google identifies the application
by its package name and certificate.

## Documents

- [Webpage](https://fytrating.pl/)
- [Privacy Policy](https://fytrating.pl/privacy)
- [Terms of Service](https://fytrating.pl/terms)

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
