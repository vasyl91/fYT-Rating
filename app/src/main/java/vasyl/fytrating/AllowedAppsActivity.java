package vasyl.fytrating;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Who may read and change ratings through the bridge.
 *
 * Nothing is allowed by default. Applications that asked and were refused are
 * offered here, so granting access is a decision the user makes about a name
 * they have actually seen, rather than a package name typed from memory.
 */
public class AllowedAppsActivity extends Activity {

    private ListView allowedList;
    private ListView pendingList;
    private TextView pendingHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allowed_apps);

        allowedList = findViewById(R.id.allowed_list);
        pendingList = findViewById(R.id.pending_list);
        pendingHeader = findViewById(R.id.pending_header);

        allowedList.setOnItemClickListener((parent, view, position, id) ->
                confirmRevoke((String) parent.getItemAtPosition(position)));
        pendingList.setOnItemClickListener((parent, view, position, id) ->
                confirmAllow((String) parent.getItemAtPosition(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Set<String> allowed = AllowedPackages.getAllowed(this);
        Set<String> pending = AllowedPackages.getPending(this);

        allowedList.setAdapter(adapterFor(allowed));
        findViewById(R.id.allowed_empty)
                .setVisibility(allowed.isEmpty() ? View.VISIBLE : View.GONE);

        pendingList.setAdapter(adapterFor(pending));
        int pendingVisibility = pending.isEmpty() ? View.GONE : View.VISIBLE;
        pendingHeader.setVisibility(pendingVisibility);
        pendingList.setVisibility(pendingVisibility);
    }

    private ArrayAdapter<String> adapterFor(Set<String> packages) {
        List<String> entries = new ArrayList<>(packages);
        return new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, entries) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String packageName = getItem(position);
                ((TextView) view.findViewById(android.R.id.text1))
                        .setText(label(packageName) + "\n" + packageName);
                return view;
            }
        };
    }

    /** The application's own name when it can be resolved, else the package. */
    private String label(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            return packageManager
                    .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                    .toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void confirmAllow(String packageName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.allow_title)
                .setMessage(getString(R.string.allow_message, label(packageName), packageName))
                .setNegativeButton(R.string.no, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.yes, (d, w) -> {
                    AllowedPackages.allow(this, packageName);
                    refresh();
                })
                .show();
    }

    private void confirmRevoke(String packageName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.revoke_title)
                .setMessage(getString(R.string.revoke_message, label(packageName)))
                .setNegativeButton(R.string.no, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.yes, (d, w) -> {
                    AllowedPackages.revoke(this, packageName);
                    refresh();
                })
                .show();
    }
}
