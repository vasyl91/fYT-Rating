# Privacy Policy

**fYT Rating**

Last updated: 21 August 2026

fYT Rating is a free, open-source Android application that reads and changes the
like status of YouTube videos on the signed-in user's own account. This policy
describes what it does with personal data.

## Summary

The application has no server and no backend of its own. It collects no
analytics, contains no advertising, and shares no data with anyone. The only
external service it contacts is the YouTube Data API, on the user's behalf and
only after the user has authorised it.

## The data the application handles

### Google account data

If the user signs in, the application can read and set the rating of a YouTube
video on that account. It requests a single scope,
`https://www.googleapis.com/auth/youtube.force-ssl`, and uses it for exactly two
YouTube Data API calls:

- **`videos.getRating`** — reads the signed-in user's rating of a video, so it
  can be displayed
- **`videos.rate`** — sets that rating when the user, or an application the user
  has allowed, asks for it

The narrower read-only scope cannot be used: `videos.getRating` returns the
user's own rating and rejects it.

Authorisation is delegated to Google Play services through Android's standard
`AccountManager`. The user chooses the account and approves the consent screen
drawn by the system. The application never receives, requests or stores a
password, and no client secret is embedded in it. Access tokens are managed by
Google Play services and are not written to storage by this application.

### What is stored on the device

In the application's private storage, and nowhere else:

- the address of the connected account
- the channel name associated with it, used only to show which account is connected
- the list of applications the user has allowed to use the bridge
- the list of applications that have requested access and been refused, so the
  user can decide about them

All of it is removed when the user signs out, except the allow list, which the
user manages directly.

### What is not collected

The application does not collect names, contacts, messages, browsing or watch
history, files, photographs, location, device identifiers or any identifier used
for tracking or advertising. It does not build profiles and does not sell data,
because it transmits none.

Video titles and thumbnails fetched for display are held in memory only and are
not stored.

## Other applications on the device

fYT Rating can answer rating requests from other applications so they do not have
to request Google authorisation themselves.

No application is allowed by default. A request from an application the user has
not explicitly allowed is refused. The identity of a caller is established from
the creator of an Android `PendingIntent`, which is recorded by the operating
system and cannot be forged by the caller.

An allowed application can ask for the rating of a video and can change it. It
receives no account credentials, no access token, and no information about the
account beyond the ratings it asks about. Access can be withdrawn at any time in
*Allowed applications*.

## Third-party services

The YouTube Data API is the only external service contacted, and only while the
user is signed in. Those requests are governed by
[Google's Privacy Policy](https://policies.google.com/privacy).

Video thumbnails are loaded from Google's public thumbnail host. No account
information is sent with those requests.

## Withdrawing consent

Access can be withdrawn in two ways, and either is sufficient:

- in the application, by signing out — this also revokes the authorisation at
  Google, so the application disappears from the account's connected apps
- at [myaccount.google.com/permissions](https://myaccount.google.com/permissions)

After withdrawal the application can no longer read or change any rating.

## Data retention

Nothing is retained anywhere but the device. Signing out removes the stored
account address and channel name immediately. Uninstalling the application
removes everything it stored.

## Children

The application is not directed at children and collects no data from them.

## Changes to this policy

Changes are published in this file in the project repository, with the date at
the top updated. Material changes will also be noted in the release notes.

## Contact

Questions and requests can be raised as an issue in the project repository, or
sent to the support address shown on the Google consent screen.
