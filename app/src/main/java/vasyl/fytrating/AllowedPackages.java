package vasyl.fytrating;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The applications the user has allowed to read and change ratings.
 *
 * Nothing is allowed by default and nothing is added automatically. An
 * application that asks while it is not on the list is refused, and the request
 * is recorded so the user can see who asked and decide.
 *
 * This is the whole of the bridge's access control, so it is deliberately
 * explicit: an entry exists only because the user created it.
 *
 * <h2>Why the list is stored on its own</h2>
 *
 * The allow list and the record of who has asked are kept in separate
 * preference files, and the allow list is written synchronously.
 *
 * They used to share one file, written with apply(). That is asynchronous: the
 * value is in memory at once but reaches disk later, and a process killed in
 * between loses the write - and SharedPreferences, finding a half written file
 * on the next start, falls back to its backup copy. Since the record of who has
 * asked is written on every refused request, a busy client could put a write in
 * flight at any moment, and a kill at that moment took the allow list back to
 * an older version with it. The user then found a granted application refused
 * for no visible reason, with granting it again the only cure.
 *
 * An entry the user created is worth the cost of a synchronous write. The
 * record of who has asked is not, and can no longer take the list down with it.
 */
public final class AllowedPackages {

    private static final String TAG = "RatingBridge";

    /** The list the user curates. Its own file, written synchronously. */
    private static final String ALLOWED_PREFS = "allowed_packages_v2";

    /** Who has asked. Noisy, disposable, and kept well away from the above. */
    private static final String SEEN_PREFS = "seen_packages";

    /** The single file both used to live in. Read once, to migrate. */
    private static final String LEGACY_PREFS = "allowed_packages";

    private static final String KEY_ALLOWED = "allowed";
    private static final String KEY_SEEN = "seen";
    private static final String KEY_MIGRATED = "migrated";

    private AllowedPackages() {
    }

    public static boolean isAllowed(Context context, String packageName) {
        return packageName != null && getAllowed(context).contains(packageName);
    }

    public static Set<String> getAllowed(Context context) {
        migrateIfNeeded(context);
        return new TreeSet<>(allowedPrefs(context).getStringSet(KEY_ALLOWED, Collections.emptySet()));
    }

    public static void allow(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        Set<String> allowed = new LinkedHashSet<>(getAllowed(context));
        if (!allowed.add(packageName)) {
            return;
        }
        writeAllowed(context, allowed);
    }

    public static void revoke(Context context, String packageName) {
        Set<String> allowed = new LinkedHashSet<>(getAllowed(context));
        if (allowed.remove(packageName)) {
            writeAllowed(context, allowed);
        }
    }

    /**
     * Records an application that asked while not allowed, so the interface can
     * offer it instead of making the user type a package name by hand.
     */
    public static void noteRequest(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        Set<String> seen = new LinkedHashSet<>(
                seenPrefs(context).getStringSet(KEY_SEEN, Collections.emptySet()));
        if (seen.add(packageName)) {
            seenPrefs(context).edit()
                    .putStringSet(KEY_SEEN, new LinkedHashSet<>(seen))
                    .apply();
        }
    }

    /** Applications that asked but have not been allowed. */
    public static Set<String> getPending(Context context) {
        migrateIfNeeded(context);
        Set<String> pending = new TreeSet<>(
                seenPrefs(context).getStringSet(KEY_SEEN, Collections.emptySet()));
        pending.removeAll(getAllowed(context));
        return pending;
    }

    public static void clearPending(Context context) {
        seenPrefs(context).edit().putStringSet(KEY_SEEN, Collections.emptySet()).apply();
    }

    /**
     * Written with commit() rather than apply(): the call returns once the list
     * is on disk. It happens when the user taps a button, so the wait costs
     * nothing anyone can perceive, and it means a grant cannot be undone by the
     * process being killed a moment later.
     */
    private static void writeAllowed(Context context, Set<String> allowed) {
        // A copy is stored: getStringSet returns an instance the framework may
        // reuse, and mutating it corrupts the preference.
        boolean written = allowedPrefs(context).edit()
                .putStringSet(KEY_ALLOWED, new LinkedHashSet<>(allowed))
                .commit();

        if (written) {
            Log.d(TAG, "Allow list is now " + new TreeSet<>(allowed));
        } else {
            Log.w(TAG, "Could not write the allow list");
        }
    }

    /**
     * Moves an existing list out of the file the two used to share.
     *
     * Runs once. Anything the user had granted before the split stays granted;
     * without this an update would silently revoke everything.
     */
    private static void migrateIfNeeded(Context context) {
        SharedPreferences allowed = allowedPrefs(context);
        if (allowed.getBoolean(KEY_MIGRATED, false)) {
            return;
        }

        SharedPreferences legacy = context.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);

        Set<String> legacyAllowed = legacy.getStringSet(KEY_ALLOWED, Collections.emptySet());
        Set<String> legacySeen = legacy.getStringSet(KEY_SEEN, Collections.emptySet());

        allowed.edit()
                .putStringSet(KEY_ALLOWED, new LinkedHashSet<>(legacyAllowed))
                .putBoolean(KEY_MIGRATED, true)
                .commit();

        if (!legacySeen.isEmpty()) {
            seenPrefs(context).edit()
                    .putStringSet(KEY_SEEN, new LinkedHashSet<>(legacySeen))
                    .apply();
        }

        Log.d(TAG, "Migrated " + legacyAllowed.size() + " allowed package(s) to their own file");
    }

    private static SharedPreferences allowedPrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(ALLOWED_PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences seenPrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE);
    }
}
