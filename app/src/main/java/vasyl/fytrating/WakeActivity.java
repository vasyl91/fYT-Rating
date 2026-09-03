package vasyl.fytrating;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * An activity that exists only to be started.
 *
 * A client cannot reach into this application to bring its process back; the
 * one thing it can do is start something. Starting an activity clears the
 * package's stopped flag and moves it to an active standby bucket, which is
 * exactly what opening the application by hand achieved - and this one draws
 * nothing, so it can be done without disturbing whatever is on screen.
 *
 * There is nothing to protect here: it carries no data, performs no action and
 * grants nothing. Anything worth guarding still goes through
 * {@link RatingBridgeReceiver}, which checks the caller and the allow list.
 */
public class WakeActivity extends Activity {

    private static final String TAG = "RatingBridge";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Woken by " + getCallingPackage());

        // Theme.NoDisplay requires this before the activity would be resumed.
        finish();
        overridePendingTransition(0, 0);
    }
}
