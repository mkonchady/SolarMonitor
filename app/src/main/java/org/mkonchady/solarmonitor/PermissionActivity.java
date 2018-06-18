package org.mkonchady.solarmonitor;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

// Used to ask individual permissions in Android 6.0+
public class PermissionActivity extends Activity {
    private final String TAG = "PermissionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        final int PERM_CODE = 100;
        super.onResume();
        String permission1 = getIntent().getStringExtra("permission1");
        String permission2 = getIntent().getStringExtra("permission2");
        Log.d(TAG, "Requesting " + permission1 + " " + permission2);
        ActivityCompat.requestPermissions(this, new String[]{permission1, permission2}, PERM_CODE);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        boolean granted = true;
        Log.d(TAG, "Permission callback called-------");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            for (int grant: grantResults) {
                if (grant != PackageManager.PERMISSION_DENIED) {
                    granted = false;
                    Log.d(TAG, "Permission Denied");
                    break;
                }
            }
        }
        Intent data = getIntent();
        data.putExtra("granted", granted + "");
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }


}
