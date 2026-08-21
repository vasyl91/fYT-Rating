package vasyl.fytrating;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        boolean set = RatingBridge.ACTION_SET_RATING.equals(intent.getAction());
        if (!set && !RatingBridge.ACTION_GET_RATING.equals(intent.getAction())) {
            return;
        }

        PendingIntent callback = intent.getParcelableExtra(RatingBridge.EXTRA_CALLBACK);

        // Identity comes from the creator of a PendingIntent, which the system
        // records and the sender cannot fake. A package name in an extra could
        // be set to anything, so it is never trusted.
        PendingIntent identity = intent.getParcelableExtra(RatingBridge.EXTRA_IDENTITY);
        String caller = identity == null ? null : identity.getCreatorPackage();

        if (caller == null) {
            Log.w(TAG, "Request without a verifiable identity, refused");
            reply(context, callback, null, null, RatingBridge.ERROR_BAD_REQUEST);
            return;
        }

        if (!AllowedPackages.isAllowed(context, caller)) {
            Log.w(TAG, "Request from " + caller + " refused, not on the allow list");
            AllowedPackages.noteRequest(context, caller);
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
