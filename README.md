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

`INTERNET`, and nothing else.

In particular there is no `GET_ACCOUNTS`, because the account is chosen in a
picker drawn by Google Play services and this application never enumerates the
accounts on your device; and no `QUERY_ALL_PACKAGES`, because the presence of an
account provider is established by asking `AccountManager` which authenticator
types exist.

## How other applications talk to it

A caller sends a broadcast with the video id, a `PendingIntent` proving its
identity, and a `PendingIntent` for the reply.

```java
Intent request = new Intent("vasyl.ytrating.action.GET_RATING");
request.setPackage("vasyl.ytrating");
request.putExtra("video_id", "dQw4w9WgXcQ");

// Identity. Never sent, only inspected: the system records the creator of a
// PendingIntent and it cannot be forged, unlike a package name in an extra.
request.putExtra("identity", PendingIntent.getBroadcast(
        context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE));

// Where the answer goes.
request.putExtra("callback", PendingIntent.getBroadcast(
        context, 0, new Intent(MY_RESULT_ACTION).setPackage(context.getPackageName()),
        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

context.sendBroadcast(request);
```

The reply carries `video_id`, `rating` (`like`, `dislike` or `none`) and, on
failure, `error`. Use `vasyl.ytrating.action.SET_RATING` with `rating` set to
change it.

Error values: `not_allowed`, `not_signed_in`, `bad_request`, `unavailable`,
`made_for_kids`.

See `RatingBridge.java` for the full contract.

### Two things integrators trip over

**An application that has never been launched receives no broadcasts.** Android
keeps a freshly installed application in a stopped state until the user opens
it, and returns it there after a force stop. This is rarely a problem in
practice, since the user has to open fYT Rating anyway to sign in and grant
access — but if you are testing on a fresh install, open it once first.

**Videos made for kids never report a rating.** YouTube answers `none` for them
whatever they were actually rated, and omits them from the liked videos list.
The bridge reports `made_for_kids` instead of a rating so callers can show that
the state is unknown rather than claim the video is not liked. This is a
limitation on Google's side and cannot be worked around.

## Building

Standard Android project; no dependencies beyond the framework.

```
./gradlew assembleRelease
```

To run your own build against Google, register an OAuth client of type
**Android** in the Google Cloud console for the package `vasyl.ytrating` with
your signing certificate's SHA-1 fingerprint, and enable the **YouTube Data API
v3**. No credential is copied into the source: Google identifies the application
by its package name and certificate.

## Documents

- [Privacy Policy](PRIVACY.md)
- [Terms of Service](TERMS.md)

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
