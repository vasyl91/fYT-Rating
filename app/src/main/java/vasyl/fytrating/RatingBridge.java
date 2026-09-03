package vasyl.fytrating;

/**
 * The contract other applications use to talk to this one.
 *
 * The bridge answers two questions about a YouTube video: what the signed in
 * user's rating of it is, and please change it. It exists so an application
 * that knows a video id - a car launcher, a widget, a remote - can show and
 * change the rating without dealing with Google authorisation itself.
 *
 * <h2>Identifying the caller</h2>
 *
 * A broadcast receiver cannot tell who sent it an intent, and a package name
 * carried in an extra can be set to anything. Callers therefore include a
 * {@link android.app.PendingIntent} in {@link #EXTRA_IDENTITY}: its creator is
 * recorded by the system when it is constructed and cannot be forged, so
 * {@code getCreatorPackage()} is a trustworthy identity.
 *
 * The bridge answers only callers the user has added to the allow list in its
 * own interface. Nothing is answered by default.
 *
 * <h2>Asking for the rating</h2>
 *
 * <pre>
 * Intent request = new Intent(RatingBridge.ACTION_GET_RATING);
 * request.setPackage(RatingBridge.BRIDGE_PACKAGE);
 * request.putExtra(RatingBridge.EXTRA_VIDEO_ID, "dQw4w9WgXcQ");
 *
 * // Proves who is asking. Never sent, only inspected.
 * request.putExtra(RatingBridge.EXTRA_IDENTITY, PendingIntent.getBroadcast(
 *         context, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE));
 *
 * // Where the answer should go.
 * request.putExtra(RatingBridge.EXTRA_CALLBACK, PendingIntent.getBroadcast(
 *         context, 0, new Intent(MY_RESULT_ACTION).setPackage(getPackageName()),
 *         PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
 *
 * context.sendBroadcast(request);
 * </pre>
 *
 * The reply arrives on the callback with {@link #EXTRA_VIDEO_ID},
 * {@link #EXTRA_RATING} and, when something went wrong, {@link #EXTRA_ERROR}.
 *
 * <h2>Changing the rating</h2>
 *
 * The same shape, with {@link #ACTION_SET_RATING} and {@link #EXTRA_RATING} set
 * to {@link #RATING_LIKE}, {@link #RATING_DISLIKE} or {@link #RATING_NONE}. The
 * reply carries the rating as it stands after the change.
 */
public final class RatingBridge {

    public static final String BRIDGE_PACKAGE = "vasyl.fytrating";

    public static final String ACTION_GET_RATING =
            "vasyl.fytrating.action.GET_RATING";
    public static final String ACTION_SET_RATING =
            "vasyl.fytrating.action.SET_RATING";

    /**
     * Asks whether the bridge is in a position to answer at all.
     *
     * The reply carries {@link #EXTRA_SIGNED_IN} and {@link #EXTRA_ALLOWED}:
     * two booleans, and nothing about the account itself. It exists so a caller
     * can tell the user what is missing - an account, or permission for this
     * particular application - instead of failing silently.
     *
     * This is the one request answered even when the caller is not allowed;
     * refusing to say "you are not allowed" would leave callers unable to
     * explain themselves.
     */
    public static final String ACTION_GET_STATUS =
            "vasyl.fytrating.action.GET_STATUS";

    /** Sent back to the caller's callback with the outcome. */
    public static final String ACTION_RATING_RESULT =
            "vasyl.fytrating.action.RATING_RESULT";

    /** String, the 11 character video id. A full watch URL is also accepted. */
    public static final String EXTRA_VIDEO_ID = "video_id";

    /** String, one of the RATING_ constants below. */
    public static final String EXTRA_RATING = "rating";

    /** PendingIntent whose creator package identifies the caller. */
    public static final String EXTRA_IDENTITY = "identity";

    /** PendingIntent the reply is sent through. */
    public static final String EXTRA_CALLBACK = "callback";

    /** String, present only on failure. One of the ERROR_ constants. */
    public static final String EXTRA_ERROR = "error";

    /** Boolean, in a status reply: an account is connected. */
    public static final String EXTRA_SIGNED_IN = "signed_in";

    /** Boolean, in a status reply: the asking application is allowed. */
    public static final String EXTRA_ALLOWED = "allowed";

    public static final String RATING_LIKE = "like";
    public static final String RATING_DISLIKE = "dislike";
    public static final String RATING_NONE = "none";

    /** The caller is not on the user's allow list. */
    public static final String ERROR_NOT_ALLOWED = "not_allowed";

    /** No Google account is connected in the bridge. */
    public static final String ERROR_NOT_SIGNED_IN = "not_signed_in";

    /** The video id was missing or malformed. */
    public static final String ERROR_BAD_REQUEST = "bad_request";

    /** The API could not be reached, or it refused the request. */
    public static final String ERROR_UNAVAILABLE = "unavailable";

    /**
     * YouTube reports no rating for videos marked as made for kids, whatever
     * they were actually rated. Callers should show "unknown" rather than
     * "not rated" when they see this.
     */
    public static final String ERROR_MADE_FOR_KIDS = "made_for_kids";

    private RatingBridge() {
    }
}
