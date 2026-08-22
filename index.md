---
layout: default
title: fYT Rating
description: Read and change the like status of a YouTube video from your car head unit or another app on your device.
---

# fYT Rating

**fYT Rating** is a free, open-source Android application that shows whether you
have liked a YouTube video and lets you like or unlike it, using your own Google
account.

It also answers the same two questions for other applications on your device
that you have explicitly allowed. That is what it was built for: a car head unit
launcher, a home screen widget or a steering wheel remote can display and change
your rating without having to handle Google sign-in itself.

## What it does

- Sign in with your Google account, once
- Paste a video id or a YouTube link and see its thumbnail, title and your rating
- Like or unlike the video with one tap
- Allow chosen applications on your device to do the same on your behalf

## Why it exists

YouTube does not tell other applications whether the video playing is liked. It
publishes no rating to the Android media session, so anything outside the
YouTube app is blind to it. The only reliable source is the YouTube Data API,
which requires the account owner's authorisation.

Rather than have every launcher and widget ask for that authorisation
separately, fYT Rating asks once and answers on their behalf — but only for
applications you have added by hand.

## What it does with your Google account

fYT Rating requests a single permission,
`https://www.googleapis.com/auth/youtube.force-ssl`, and uses it for exactly two
YouTube Data API calls:

| Call | Purpose |
|---|---|
| `videos.getRating` | reads your rating of a video, so it can be shown |
| `videos.rate` | sets your rating when you ask it to |

The narrower read-only permission cannot be used, because `videos.getRating`
returns the signed-in user's own rating and rejects it.

Sign-in is handled by Google Play services through Android's standard account
picker. The application never sees or asks for your password.

## What it does with your data

Nothing leaves your device except those two API calls to Google, made on your
behalf. There is no server behind this application, no analytics, no
advertising, and no data is shared with anyone.

The only things stored on the device are the address of the connected account,
the channel name shown next to it, and the list of applications you have
allowed. Signing out removes the first two and revokes the authorisation at
Google, so the application disappears from your account's connected apps.

## Privacy and terms

- [Privacy Policy](PRIVACY.html)
- [Terms of Service](TERMS.html)

## Source code and downloads

The application is open source. The code, releases and technical documentation
are on [GitHub](https://github.com/vasyl91/fYT-Rating).

fYT Rating is an independent project. It is not affiliated with, endorsed by or
sponsored by Google LLC or YouTube.

## Contact

Questions and problems can be raised as an
[issue](https://github.com/vasyl91/fYT-Rating/issues) in the repository.
