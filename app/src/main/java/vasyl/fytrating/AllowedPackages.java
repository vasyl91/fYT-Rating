package vasyl.fytrating;

import android.content.Context;
import android.content.SharedPreferences;

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
 */
public final class AllowedPackages {

    private static final String PREFS_NAME = "allowed_packages";
    private static final String KEY_ALLOWED = "allowed";
    private static final String KEY_SEEN = "seen";

    private AllowedPackages() {
    }

    public static boolean isAllowed(Context context, String packageName) {
        return packageName != null && read(context, KEY_ALLOWED).contains(packageName);
    }

    public static Set<String> getAllowed(Context context) {
        return new TreeSet<>(read(context, KEY_ALLOWED));
    }

    public static void allow(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>(read(context, KEY_ALLOWED));
        allowed.add(packageName);
        write(context, KEY_ALLOWED, allowed);
    }

    public static void revoke(Context context, String packageName) {
        Set<String> allowed = new LinkedHashSet<>(read(context, KEY_ALLOWED));
        if (allowed.remove(packageName)) {
            write(context, KEY_ALLOWED, allowed);
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
        Set<String> seen = new LinkedHashSet<>(read(context, KEY_SEEN));
        if (seen.add(packageName)) {
            write(context, KEY_SEEN, seen);
        }
    }

    /** Applications that asked but have not been allowed. */
    public static Set<String> getPending(Context context) {
        Set<String> pending = new TreeSet<>(read(context, KEY_SEEN));
        pending.removeAll(read(context, KEY_ALLOWED));
        return pending;
    }

    public static void clearPending(Context context) {
        write(context, KEY_SEEN, Collections.emptySet());
    }

    private static Set<String> read(Context context, String key) {
        return prefs(context).getStringSet(key, Collections.emptySet());
    }

    private static void write(Context context, String key, Set<String> value) {
        // A copy is stored: getStringSet returns an instance the framework may
        // reuse, and mutating it corrupts the preference.
        prefs(context).edit().putStringSet(key, new LinkedHashSet<>(value)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
