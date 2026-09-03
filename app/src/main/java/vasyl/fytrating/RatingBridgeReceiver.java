package vasyl.fytrating;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers rating requests from other applications.
 *
 * Every request is checked twice before anything happens: the caller has to
 * prove who it is, and the user has to have allowed it. A request that fails
 * either check is refused and recorded, never silently honoured.
 */
public class RatingBridgeReceiver extends BroadcastReceiver {

    private static final String TAG = "RatingBridge";

    private static final Pattern VIDEO_ID_IN_URL =
            Pattern.compile("(?:v=|/vi/|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_VIDEO_ID =
            Pattern.compile("^[A-Za-z0-9_-]{11}$");

    // A single thread meant one slow lookup blocked every request queued
    // behind it, and each of those then timed out on the caller's side.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        boolean set = RatingBridge.ACTION_SET_RATING.equals(action);
        boolean status = RatingBridge.ACTION_GET_STATUS.equals(action);
        if (!set && !status && !RatingBridge.ACTION_GET_RATING.equals(action)) {
            return;
        }

        PendingIntent callback;
        PendingIntent identity = null;

        // The whole extras bundle is pulled out at once.
        Bundle extras = intent.getExtras();

        if (extras != null) {
            // Forcing the system ClassLoader; custom ROMs otherwise fail to
            // unmarshal the PendingIntents.
            extras.setClassLoader(PendingIntent.class.getClassLoader());

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                callback = extras.getParcelable(RatingBridge.EXTRA_CALLBACK, PendingIntent.class);
                identity = extras.getParcelable(RatingBridge.EXTRA_IDENTITY, PendingIntent.class);
            } else {
                callback = extras.getParcelable(RatingBridge.EXTRA_CALLBACK);
                identity = extras.getParcelable(RatingBridge.EXTRA_IDENTITY);
            }
        } else {
            callback = null;
        }

        String callbackCreator = creatorOf(callback);
        String identityCreator = creatorOf(identity);

        String caller = callbackCreator != null ? callbackCreator : identityCreator;

        if (caller == null) {
            Log.w(TAG, "Request without a verifiable identity, refused"
                    + " (extras=" + (extras != null)
                    + " callback=" + (callback != null)
                    + " identity=" + (identity != null) + ")");
            reply(context, callback, null, null, RatingBridge.ERROR_BAD_REQUEST);
            return;
        }

        boolean callerAllowed = AllowedPackages.isAllowed(context, caller);

        if (!callerAllowed) {
            // Logged in full because the two ways this happens look identical
            // from the outside: a caller the user never granted, and a caller
            // whose grant went missing. The list itself tells them apart.
            Log.w(TAG, "Not allowed: caller=" + caller
                    + " action=" + action
                    + " allowList=" + AllowedPackages.getAllowed(context));

            // Recorded whatever the request was, so the user can see who asked
            // and decide about them in the interface.
            AllowedPackages.noteRequest(context, caller);
        }

        if (status) {
            // Answered even for a caller that is not allowed: it is how an
            // application learns that it needs to be, and it reveals nothing
            // about the account.
            replyStatus(context, callback, GoogleAccount.isSignedIn(context), callerAllowed);
            return;
        }

        if (!callerAllowed) {
            Log.w(TAG, "Request from " + caller + " refused, not on the allow list");
            reply(context, callback, null, null, RatingBridge.ERROR_NOT_ALLOWED);
            return;
        }

        String videoId = extractVideoId(intent.getStringExtra(RatingBridge.EXTRA_VIDEO_ID));
        if (videoId == null) {
            reply(context, callback, null, null, RatingBridge.ERROR_BAD_REQUEST);
            return;
        }

        if (!GoogleAccount.isSignedIn(context)) {
            reply(context, callback, videoId, null, RatingBridge.ERROR_NOT_SIGNED_IN);
            return;
        }

        String requested = intent.getStringExtra(RatingBridge.EXTRA_RATING);
        if (set && !isKnownRating(requested)) {
            reply(context, callback, videoId, null, RatingBridge.ERROR_BAD_REQUEST);
            return;
        }

        Context appContext = context.getApplicationContext();

        // Without this the process could be killed the moment onReceive
        // returns, cutting the network call short. goAsync keeps the receiver
        // alive until finish() is called.
        final PendingResult pendingResult = goAsync();
        EXECUTOR.execute(() -> {
            try {
                handle(appContext, callback, videoId, set ? requested : null);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void handle(Context context, PendingIntent callback, String videoId, String toSet) {
        try {
            if (toSet != null) {
                GoogleAccount.setRating(context, videoId, toSet);
            }

            String rating = GoogleAccount.getRating(context, videoId);
            if (rating == null) {
                // No rating comes back for videos made for kids, whatever they
                // were rated. Saying so is more useful than reporting "none".
                String error = GoogleAccount.isMadeForKids(context, videoId)
                        ? RatingBridge.ERROR_MADE_FOR_KIDS
                        : RatingBridge.ERROR_UNAVAILABLE;
                reply(context, callback, videoId, null, error);
                return;
            }

            reply(context, callback, videoId, rating, null);
        } catch (Exception e) {
            Log.w(TAG, "Request for " + videoId + " failed", e);
            reply(context, callback, videoId, null, RatingBridge.ERROR_UNAVAILABLE);
        }
    }

    private static void reply(
            Context context, PendingIntent callback,
            String videoId, String rating, String error) {
        if (callback == null) {
            return;
        }

        Intent result = new Intent(RatingBridge.ACTION_RATING_RESULT);
        if (videoId != null) {
            result.putExtra(RatingBridge.EXTRA_VIDEO_ID, videoId);
        }
        if (rating != null) {
            result.putExtra(RatingBridge.EXTRA_RATING, rating);
        }
        if (error != null) {
            result.putExtra(RatingBridge.EXTRA_ERROR, error);
        }

        try {
            callback.send(context, 0, result);
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "The caller went away before the reply could be sent", e);
        }
    }

    private static String creatorOf(PendingIntent pendingIntent) {
        try {
            return pendingIntent == null ? null : pendingIntent.getCreatorPackage();
        } catch (Exception e) {
            return null;
        }
    }

    private static void replyStatus(
            Context context, PendingIntent callback, boolean signedIn, boolean allowed) {
        if (callback == null) {
            return;
        }

        Intent result = new Intent(RatingBridge.ACTION_RATING_RESULT);
        result.putExtra(RatingBridge.EXTRA_SIGNED_IN, signedIn);
        result.putExtra(RatingBridge.EXTRA_ALLOWED, allowed);

        try {
            callback.send(context, 0, result);
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "The caller went away before the status could be sent", e);
        }
    }

    /** Accepts a bare id or any of the usual YouTube URL shapes. */
    private static String extractVideoId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (BARE_VIDEO_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        Matcher matcher = VIDEO_ID_IN_URL.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean isKnownRating(String rating) {
        return RatingBridge.RATING_LIKE.equals(rating)
                || RatingBridge.RATING_DISLIKE.equals(rating)
                || RatingBridge.RATING_NONE.equals(rating);
    }
}