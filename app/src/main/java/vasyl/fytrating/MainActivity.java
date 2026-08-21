package vasyl.fytrating;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The whole interface: connect an account, look a video up, see and change its
 * rating.
 *
 * Other applications reach the same two operations through
 * {@link RatingBridgeReceiver}; this screen is what makes the application
 * usable on its own, and what lets the user see exactly what it does with the
 * account before allowing anything else to use it.
 */
public class MainActivity extends Activity {

    private static final Pattern VIDEO_ID_IN_URL =
            Pattern.compile("(?:v=|/vi/|youtu\\.be/|/shorts/)([A-Za-z0-9_-]{11})");
    private static final Pattern BARE_VIDEO_ID =
            Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private static final String THUMBNAIL_URL = "https://i.ytimg.com/vi/%s/mqdefault.jpg";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView accountLine;
    private Button accountButton;
    private EditText videoField;
    private ImageView thumbnail;
    private TextView title;
    private Button likeButton;
    private TextView ratingLine;

    private String currentVideoId;
    private String currentRating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountLine = findViewById(R.id.account_line);
        accountButton = findViewById(R.id.account_button);
        videoField = findViewById(R.id.video_field);
        thumbnail = findViewById(R.id.thumbnail);
        title = findViewById(R.id.title);
        likeButton = findViewById(R.id.like_button);
        ratingLine = findViewById(R.id.rating_line);

        accountButton.setOnClickListener(v -> onAccountClicked());
        findViewById(R.id.check_button).setOnClickListener(v -> onCheckClicked());
        likeButton.setOnClickListener(v -> onLikeClicked());
        findViewById(R.id.allowed_apps_button).setOnClickListener(
                v -> startActivity(new android.content.Intent(this, AllowedAppsActivity.class)));
findViewById(R.id.scroll_root).post(() -> {
    android.view.View r = findViewById(R.id.scroll_root);
    android.view.View c = ((android.view.ViewGroup) r).getChildAt(0);
    android.util.Log.d("LAYOUT", "scroll=" + r.getHeight()
            + " content=" + c.getHeight()
            + " klasa=" + c.getClass().getSimpleName()
            + " dzieci=" + ((android.view.ViewGroup) c).getChildCount());
});
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountLine();
    }

    // -------------------------------------------------------------- account

    private void updateAccountLine() {
        boolean signedIn = GoogleAccount.isSignedIn(this);
        accountLine.setText(signedIn
                ? getString(R.string.account_connected, GoogleAccount.getDisplayName(this))
                : getString(R.string.account_not_connected));
        accountButton.setText(signedIn ? R.string.sign_out : R.string.sign_in);
    }

    private void onAccountClicked() {
        if (GoogleAccount.isSignedIn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sign_out)
                    .setMessage(getString(R.string.sign_out_message,
                            GoogleAccount.getDisplayName(this)))
                    .setNegativeButton(R.string.no, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.yes, (d, w) -> {
                        GoogleAccount.signOut(this);
                        clearVideo();
                        updateAccountLine();
                    })
                    .show();
            return;
        }

        if (!GoogleAccount.isAccountProviderInstalled(this)) {
            toast(getString(R.string.no_account_provider));
            return;
        }

        GoogleAccount.signIn(this, (success, message) -> main.post(() -> {
            updateAccountLine();
            toast(success
                    ? getString(R.string.signed_in_as, message)
                    : getString(R.string.sign_in_failed,
                    message == null ? getString(R.string.cancelled) : message));
        }));
    }

    // ---------------------------------------------------------------- video

    private void onCheckClicked() {
        if (!GoogleAccount.isSignedIn(this)) {
            toast(getString(R.string.sign_in_first));
            return;
        }

        String videoId = extractVideoId(videoField.getText().toString());
        if (videoId == null) {
            toast(getString(R.string.bad_video_id));
            return;
        }

        currentVideoId = videoId;
        currentRating = null;
        title.setText(R.string.loading);
        ratingLine.setText("");
        likeButton.setEnabled(false);
        loadThumbnail(videoId);

        executor.execute(() -> {
            String rating = null;
            String videoTitle = null;
            boolean madeForKids = false;
            try {
                videoTitle = GoogleAccount.getTitle(this, videoId);
                rating = GoogleAccount.getRating(this, videoId);
                if (rating == null) {
                    madeForKids = GoogleAccount.isMadeForKids(this, videoId);
                }
            } catch (Exception e) {
                // Reported below through the empty rating.
            }
            showResult(videoId, videoTitle, rating, madeForKids);
        });
    }

    private void showResult(
            String videoId, String videoTitle, String rating, boolean madeForKids) {
        main.post(() -> {
            if (!videoId.equals(currentVideoId)) {
                return;
            }

            title.setText(videoTitle == null ? getString(R.string.unknown_title) : videoTitle);
            currentRating = rating;

            if (rating == null) {
                // A video made for kids never reports a rating, so the button
                // shows no state rather than claiming the video is unrated.
                ratingLine.setText(madeForKids
                        ? R.string.rating_made_for_kids
                        : R.string.rating_unavailable);
                likeButton.setEnabled(!madeForKids);
                likeButton.setSelected(false);
                return;
            }

            boolean liked = RatingBridge.RATING_LIKE.equals(rating);
            ratingLine.setText(liked ? R.string.rating_liked : R.string.rating_not_liked);
            likeButton.setSelected(liked);
            likeButton.setEnabled(true);
        });
    }

    private void onLikeClicked() {
        if (currentVideoId == null) {
            return;
        }

        boolean liked = RatingBridge.RATING_LIKE.equals(currentRating);
        String target = liked ? RatingBridge.RATING_NONE : RatingBridge.RATING_LIKE;
        String videoId = currentVideoId;

        likeButton.setEnabled(false);
        executor.execute(() -> {
            try {
                GoogleAccount.setRating(this, videoId, target);
                String confirmed = GoogleAccount.getRating(this, videoId);
                showResult(videoId, title.getText().toString(), confirmed, false);
            } catch (Exception e) {
                main.post(() -> {
                    likeButton.setEnabled(true);
                    toast(getString(R.string.rating_failed));
                });
            }
        });
    }

    private void clearVideo() {
        currentVideoId = null;
        currentRating = null;
        title.setText("");
        ratingLine.setText("");
        thumbnail.setImageDrawable(null);
        thumbnail.setVisibility(View.GONE);
        likeButton.setEnabled(false);
        likeButton.setSelected(false);
    }

    /** Loaded straight from the thumbnail host; no API call and no key needed. */
    private void loadThumbnail(String videoId) {
        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                URL url = new URL(String.format(THUMBNAIL_URL, videoId));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try (InputStream stream = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(stream);
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                // A missing thumbnail is cosmetic.
            }

            Bitmap loaded = bitmap;
            main.post(() -> {
                if (videoId.equals(currentVideoId)) {
                    thumbnail.setImageBitmap(loaded);
                    thumbnail.setVisibility(loaded == null ? View.GONE : View.VISIBLE);
                }
            });
        });
    }

    /** Accepts a bare id or any of the usual YouTube URL shapes. */
    private static String extractVideoId(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (BARE_VIDEO_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        Matcher matcher = VIDEO_ID_IN_URL.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
