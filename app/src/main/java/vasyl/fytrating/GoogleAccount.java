package vasyl.fytrating;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Google authorisation and the YouTube Data API calls behind it.
 *
 * Authentication is delegated to Google Play services through Android's
 * standard AccountManager: the user picks an account and approves the consent
 * screen drawn by the system, and Google identifies this application by its
 * package name and signing certificate. No client id, client secret or
 * password is stored anywhere in the application.
 *
 * Every method that talks to the network blocks and must be called from a
 * background thread.
 */
public final class GoogleAccount {

    private static final String TAG = "GoogleAccount";

    /**
     * videos.getRating returns the signed in user's own rating, which the
     * read-only scope does not cover - it answers 403 with
     * ACCESS_TOKEN_SCOPE_INSUFFICIENT. The same scope also permits videos.rate.
     */
    private static final String AUTH_TOKEN_TYPE =
            "oauth2:https://www.googleapis.com/auth/youtube.force-ssl";

    private static final String ACCOUNT_TYPE = "com.google";


    private static final String RATING_URL =
            "https://www.googleapis.com/youtube/v3/videos/getRating?id=";
    private static final String RATE_URL =
            "https://www.googleapis.com/youtube/v3/videos/rate?rating=";
    private static final String VIDEO_URL =
            "https://www.googleapis.com/youtube/v3/videos?part=snippet,status&id=";
    private static final String REVOKE_URL =
            "https://oauth2.googleapis.com/revoke?token=";
    private static final String CHANNEL_URL =
            "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true";

    private static final String PREFS_NAME = "google_account";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_DISPLAY_NAME = "display_name";

    private static volatile String cachedToken;

    /** Revocation outlives the screen that triggered it. */
    private static final ExecutorService REVOKE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private GoogleAccount() {
    }

    // ----------------------------------------------------------------- state

    /**
     * True when something able to serve Google accounts is installed.
     *
     * The authenticator list is asked rather than the package list: it answers
     * the question directly, and it needs neither GET_ACCOUNTS nor
     * QUERY_ALL_PACKAGES. Any implementation registering the com.google account
     * type qualifies, whatever package it lives in.
     */
    public static boolean isAccountProviderInstalled(Context context) {
        for (AuthenticatorDescription authenticator :
                AccountManager.get(context).getAuthenticatorTypes()) {
            if (ACCOUNT_TYPE.equals(authenticator.type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSignedIn(Context context) {
        return getAccountName(context) != null;
    }

    /** Channel title when it is known, otherwise the account address. */
    public static String getDisplayName(Context context) {
        SharedPreferences prefs = prefs(context);
        String display = prefs.getString(KEY_DISPLAY_NAME, null);
        return display != null ? display : prefs.getString(KEY_ACCOUNT_NAME, null);
    }

    private static String getAccountName(Context context) {
        String name = prefs(context).getString(KEY_ACCOUNT_NAME, null);
        return name == null || name.isEmpty() ? null : name;
    }

    /**
     * Signs out and withdraws the authorisation.
     *
     * Forgetting the account locally is not enough: the grant lives on the
     * Google account, so the next sign in would be approved silently and the
     * application would keep its access in the user's account settings. The
     * token is therefore revoked at Google as well, which is what a user
     * pressing "sign out" reasonably expects.
     *
     * The network call runs in the background; the local state is cleared
     * immediately so the interface never waits for it.
     */
    public static void signOut(Context context) {
        Context appContext = context.getApplicationContext();
        String token = cachedToken;

        cachedToken = null;
        prefs(appContext).edit().remove(KEY_ACCOUNT_NAME).remove(KEY_DISPLAY_NAME).apply();

        AccountManager accountManager = AccountManager.get(appContext);
        if (token != null) {
            accountManager.invalidateAuthToken(ACCOUNT_TYPE, token);
        }

        REVOKE_EXECUTOR.execute(() -> revokeAtGoogle(appContext, token));
    }

    /**
     * Withdraws the grant at Google.
     *
     * A cached token may already have been invalidated, so a fresh one is
     * requested first: revoking any token issued to this application removes
     * the whole grant, not just that token.
     */
    private static void revokeAtGoogle(Context context, String previousToken) {
        String token = previousToken;
        try {
            // The account has just been forgotten, so there is nothing to
            // request a fresh token with: only a held one can be revoked.
            if (token == null) {
                Log.d(TAG, "No token to revoke");
                return;
            }

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(REVOKE_URL + encode(token)).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Content-Length", "0");
                connection.setFixedLengthStreamingMode(0);

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    Log.d(TAG, "Authorisation revoked at Google");
                } else {
                    // Most often the token had already expired, in which case
                    // the grant may still stand. Nothing more can be done from
                    // here; the account page always works.
                    Log.w(TAG, "Revocation answered HTTP " + code + ": "
                            + readBody(connection));
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not revoke the authorisation", e);
        }
    }

    // ---------------------------------------------------------------- signin

    public interface SignInCallback {
        void onResult(boolean success, String displayNameOrError);
    }

    /**
     * Shows the account picker and the consent screen, both drawn by Google.
     *
     * getAuthTokenByFeatures is used rather than listing accounts, which avoids
     * the GET_ACCOUNTS permission entirely: this application never enumerates
     * the accounts on the device, it only receives the one the user chose.
     */
    public static void signIn(Activity activity, SignInCallback callback) {
        AccountManager.get(activity.getApplicationContext()).getAuthTokenByFeatures(
                ACCOUNT_TYPE,
                AUTH_TOKEN_TYPE,
                null,
                activity,
                null,
                null,
                future -> onSignInResult(activity.getApplicationContext(), future, callback),
                null
        );
    }

    private static void onSignInResult(
            Context context, AccountManagerFuture<Bundle> future, SignInCallback callback) {
        try {
            Bundle result = future.getResult();
            String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
            String token = result.getString(AccountManager.KEY_AUTHTOKEN);

            if (accountName == null || token == null) {
                callback.onResult(false, null);
                return;
            }

            cachedToken = token;
            prefs(context).edit().putString(KEY_ACCOUNT_NAME, accountName).apply();

            // Cosmetic only: a failure here must not undo a successful sign in.
            String display = fetchChannelTitle(token);
            if (display != null) {
                prefs(context).edit().putString(KEY_DISPLAY_NAME, display).apply();
            }

            callback.onResult(true, display != null ? display : accountName);
        } catch (Exception e) {
            Log.w(TAG, "Sign in failed", e);
            callback.onResult(false, describeFailure(e));
        }
    }

    private static String describeFailure(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        if (message.contains("UnregisteredOnApiConsole")) {
            return "This build's signing key is not registered with Google.";
        }
        if (message.contains("NetworkError")) {
            return "Could not reach Google, check the network connection.";
        }
        return message;
    }

    // ------------------------------------------------------------------ api

    /** @return "like", "dislike", "none", or null when it cannot be told */
    public static String getRating(Context context, String videoId) throws IOException {
        JSONObject response = getWithRetry(context, RATING_URL + encode(videoId));
        if (response == null) {
            return null;
        }
        try {
            JSONArray items = response.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return null;
            }
            String rating = items.getJSONObject(0).optString("rating", null);
            // "unspecified" is YouTube's way of saying it will not tell.
            return "unspecified".equals(rating) ? null : rating;
        } catch (JSONException e) {
            throw new IOException("Malformed rating response", e);
        }
    }

    public static void setRating(Context context, String videoId, String rating)
            throws IOException {
        String token = getToken(context, false);
        if (token == null) {
            throw new IOException("No access token");
        }

        int code = post(RATE_URL + encode(rating) + "&id=" + encode(videoId), token);
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED
                || code == HttpURLConnection.HTTP_FORBIDDEN) {
            token = getToken(context, true);
            if (token == null) {
                throw new IOException("No access token");
            }
            code = post(RATE_URL + encode(rating) + "&id=" + encode(videoId), token);
        }

        if (code < 200 || code >= 300) {
            throw new IOException("Rating rejected, HTTP " + code);
        }
        Log.d(TAG, "Rating for " + videoId + " set to " + rating);
    }

    /**
     * YouTube reports no rating for videos made for kids, whatever they were
     * rated, so telling the two cases apart matters when the rating is absent.
     */
    public static boolean isMadeForKids(Context context, String videoId) {
        try {
            JSONObject video = getVideo(context, videoId);
            if (video == null) {
                return false;
            }
            JSONObject status = video.optJSONObject("status");
            return status != null && status.optBoolean("madeForKids", false);
        } catch (Exception e) {
            Log.w(TAG, "Could not read the status of " + videoId, e);
            return false;
        }
    }

    /** Title of a video, for display. Null when it cannot be read. */
    public static String getTitle(Context context, String videoId) {
        try {
            JSONObject video = getVideo(context, videoId);
            if (video == null) {
                return null;
            }
            JSONObject snippet = video.optJSONObject("snippet");
            return snippet == null ? null : snippet.optString("title", null);
        } catch (Exception e) {
            Log.w(TAG, "Could not read the title of " + videoId, e);
            return null;
        }
    }

    private static JSONObject getVideo(Context context, String videoId) throws IOException {
        JSONObject response = getWithRetry(context, VIDEO_URL + encode(videoId));
        if (response == null) {
            return null;
        }
        JSONArray items = response.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return null;
        }
        try {
            return items.getJSONObject(0);
        } catch (JSONException e) {
            throw new IOException("Malformed video response", e);
        }
    }

    private static String fetchChannelTitle(String token) {
        try {
            JSONObject response = get(CHANNEL_URL, token);
            JSONArray items = response == null ? null : response.optJSONArray("items");
            if (items != null && items.length() > 0) {
                return items.getJSONObject(0)
                        .getJSONObject("snippet")
                        .optString("title", null);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not read the channel title", e);
        }
        return null;
    }

    // --------------------------------------------------------------- tokens

    /**
     * A usable access token. Google Play services caches these itself, so
     * asking every time is cheap; a token the API rejects is invalidated and
     * fetched again once.
     */
    private static synchronized String getToken(Context context, boolean forceRefresh) {
        String accountName = getAccountName(context);
        if (accountName == null) {
            return null;
        }

        AccountManager accountManager = AccountManager.get(context);

        if (forceRefresh && cachedToken != null) {
            accountManager.invalidateAuthToken(ACCOUNT_TYPE, cachedToken);
            cachedToken = null;
        }
        if (cachedToken != null) {
            return cachedToken;
        }

        try {
            // Built from the stored address, so no account lookup and therefore
            // no GET_ACCOUNTS permission is needed.
            Account account = new Account(accountName, ACCOUNT_TYPE);
            Bundle result = accountManager
                    .getAuthToken(account, AUTH_TOKEN_TYPE, null, false, null, null)
                    .getResult();

            cachedToken = result.getString(AccountManager.KEY_AUTHTOKEN);
            if (cachedToken == null && result.containsKey(AccountManager.KEY_INTENT)) {
                Log.w(TAG, "Consent is required again; sign out and back in.");
            }
            return cachedToken;
        } catch (Exception e) {
            Log.w(TAG, "Could not obtain a token", e);
            return null;
        }
    }

    // -------------------------------------------------------------- plumbing

    /** Runs a GET, refreshing the token once if the first attempt is refused. */
    private static JSONObject getWithRetry(Context context, String url) throws IOException {
        String token = getToken(context, false);
        if (token == null) {
            return null;
        }

        JSONObject response = get(url, token);
        if (response == null) {
            token = getToken(context, true);
            if (token == null) {
                return null;
            }
            response = get(url, token);
        }
        return response;
    }

    /** @return null when the token was refused, so the caller can retry */
    private static JSONObject get(String url, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            // The rating changes while the application runs, so nothing here
            // may be answered from a cache.
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");

            int code = connection.getResponseCode();
            String body = readBody(connection);

            if (code == HttpURLConnection.HTTP_UNAUTHORIZED
                    || code == HttpURLConnection.HTTP_FORBIDDEN) {
                Log.w(TAG, "HTTP " + code + " from " + url + ": " + body);
                return null;
            }
            if (code >= 400) {
                throw new IOException("HTTP " + code + ": " + body);
            }
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new IOException("Malformed response", e);
        } finally {
            connection.disconnect();
        }
    }

    private static int post(String url, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Length", "0");
            connection.setFixedLengthStreamingMode(0);

            int code = connection.getResponseCode();
            if (code >= 400) {
                Log.w(TAG, "HTTP " + code + " from " + url + ": " + readBody(connection));
            }
            return code;
        } finally {
            connection.disconnect();
        }
    }

    /** Reads either stream: Google puts its error details in the body. */
    private static String readBody(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (stream == null) {
            return "{}";
        }

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
