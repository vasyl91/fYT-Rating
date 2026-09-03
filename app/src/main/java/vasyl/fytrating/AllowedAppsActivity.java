package vasyl.fytrating;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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
public class AllowedAppsActivity extends AppCompatActivity {

    private ListView allowedList;
    private ListView pendingList;
    private TextView pendingHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_allowed_apps);

        Toolbar toolbar = findViewById(R.id.apps_toolbar);
        setSupportActionBar(toolbar);

        allowedList = findViewById(R.id.allowed_list);
        pendingList = findViewById(R.id.pending_list);
        pendingHeader = findViewById(R.id.pending_header);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        Set<String> allowed = AllowedPackages.getAllowed(this);
        Set<String> pending = AllowedPackages.getPending(this);

        allowedList.setAdapter(adapterFor(allowed, true)); 
        findViewById(R.id.allowed_empty)
                .setVisibility(allowed.isEmpty() ? View.VISIBLE : View.GONE);

        pendingList.setAdapter(adapterFor(pending, false)); 
        int pendingVisibility = pending.isEmpty() ? View.GONE : View.VISIBLE;
        pendingHeader.setVisibility(pendingVisibility);
        pendingList.setVisibility(pendingVisibility);
    }

    private ArrayAdapter<String> adapterFor(Set<String> packages, boolean isAllowedList) {
        List<String> entries = new ArrayList<>(packages);
        
        return new ArrayAdapter<>(
                this, R.layout.list_item_button, R.id.button_item, entries) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                String packageName = getItem(position);
                
                Button button = view.findViewById(R.id.button_item);
                button.setText(label(packageName) + "\n" + packageName);
                
                button.setOnClickListener(v -> {
                    if (isAllowedList) {
                        confirmRevoke(packageName);
                    } else {
                        confirmAllow(packageName);
                    }
                });
                
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
