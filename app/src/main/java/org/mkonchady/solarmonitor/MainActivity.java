package org.mkonchady.solarmonitor;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.TextView;

import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.text.DecimalFormat;

public class MainActivity extends Activity {

    public String locationStatus = "";                      // status of location setup
    private Handler guiHandler = new Handler();             // to update GUI
    public int localLog = 0;                                // Log to a local file
    public SharedPreferences sharedPreferences = null;
    LocationStartup locationStartup = null;

    private Menu menu;
    public Context context;
    public Thread coordinateThread = null;
    public Thread envoyThread = null;
    public boolean envoyThreadRunning = false;
    public boolean coordinateThreadRunning = false;
    private SolarLog solarLog = null;
    private String monitorStatus = Constants.MONITOR_NOT_STARTED;
    private String envoyStatus = Constants.ENVOY_DEAD;
    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isGooglePlayServicesAvailable()) finish();
        context = getApplicationContext();

        // load the preferences from the settings
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // initial layout
        setContentView(R.layout.activity_main);
        setActionBar();

        // set the icon grid and text
        GridView grid;
        final String[] icon_text = { "Monitor", "Calendar", "Settings" };
        final int[] imageId = { R.drawable.logs, R.drawable.calendar, R.drawable.settings};
        final int[] icon_ids = { R.id.action_logs, R.id.action_calendar, R.id.action_settings};

        CustomGrid adapter = new CustomGrid(MainActivity.this, icon_text, imageId);
        grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                for (int i = 0; i < icon_text.length; i++) {
                    if (position == i)
                        onOptionsItemSelected(menu.findItem(icon_ids[i]));
                }
            }
        });

        // get permissions
        getPermissions();

        // create the Log file, clean up old Log files, and process Log files from earlier days
        solarLog = new SolarLog(context);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                solarLog.clean_up_old_files();
                solarLog.process_logfiles();
            }
        });

        // get the monitor status, if finished earlier, then set to not started
        monitorStatus = sharedPreferences.getString(Constants.MONITOR_STATUS, Constants.MONITOR_NOT_STARTED);
        if (monitorStatus.equals(Constants.MONITOR_FINISHED)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.MONITOR_STATUS, Constants.MONITOR_NOT_STARTED);
            editor.apply();
        }

        // get the envoy status saved in shared preferences
        String envoy_ip = sharedPreferences.getString(Constants.ENVOY_IP, Constants.DEFAULT_ENVOY_IP);
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        editor.putString(Constants.ENVOY_IP, "");
        editor.putString(Constants.LAN_SERVERS, "");
        editor.apply();
        UtilsNetwork.isEnvoyAlive(envoy_ip, sharedPreferences);

        // start the Log file
        if (!monitorStatus.equals(Constants.MONITOR_RUNNING)) new Log(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));

        setButtonLabel();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_bar, menu);
        this.menu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {

            case R.id.action_calendar:
                intent = new Intent(MainActivity.this, CalendarActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_settings:
                intent = new Intent(MainActivity.this, PreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_logs:
                intent = new Intent(MainActivity.this, MonitorActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed", localLog);
        statusCheck();

        // toggle the button to start / stop
        setButtonLabel();
        ImageButton mb = findViewById(R.id.mainButton);
        View.OnClickListener mbLis = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (monitorStatus.equals(Constants.MONITOR_RUNNING)) {
                    locationStatus = getString(R.string.monitor_cleanup);
                    guiHandler.post(updateLocationStatus);
                    stopTracking();
                } else {
                    startTracking();
                }
                setButtonLabel();
            }
        };
        mb.setOnClickListener(mbLis);

        guiHandler.post(updateLocationStatus);
        setButtonLabel();
        Log.d(TAG, "onResume Status " + monitorStatus, localLog);
    }


    private void statusCheck() {
        /* set the main button depending on whether monitoring is in progress or not

              MONITOR_NOT_STARTED --> MONITOR_STARTED ---> MONITOR_RUNNING ---> MONITOR_FINISHED

           1. MONITOR_NOT_STARTED: No GPS coordinates located so far
           2. MONITOR_STARTED: GPS Coordinates found, but monitoring not in progress
           3. MONITOR_RUNNING: GPS Coordinates found and monitoring is in progress
           4. MONITOR_FINISHED: Monitoring is complete and the app must be restarted for a new session

        */
        monitorStatus = sharedPreferences.getString(Constants.MONITOR_STATUS, Constants.MONITOR_NOT_STARTED);
        switch (monitorStatus) {
            case Constants.MONITOR_NOT_STARTED:  // get the location status in the background and update status
                if (isPermissionsGranted() && coordinateThread == null) {
                    getCoordinates();
                }
                break;
            case Constants.MONITOR_STARTED:
                locationStatus = "" + formatLocation(sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0f),
                                                     sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0f));
                guiHandler.post(updateLocationStatus);
                break;
            case Constants.MONITOR_RUNNING:
                locationStatus = sharedPreferences.getString(Constants.LOG_STATUS, getString(R.string.monitor_in_progress));
                guiHandler.post(updateLocationStatus);
                break;
            case Constants.MONITOR_FINISHED:
                locationStatus = getString(R.string.monitor_ended);
                guiHandler.post(updateLocationStatus);
                break;
        }

        // if envoy is dead, search for an IP address
        // envoy status is set in call to isEnvoyAlive from onCreate()
        envoyStatus = sharedPreferences.getString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        if (envoyStatus.equals(Constants.ENVOY_DEAD) && envoyThread == null)
            getEnvoyIP();
        guiHandler.post(updateEnvoyStatus);
    }

    //*-----------------------------------------------------------------
    // get the coordinates of the first location in the background
    //*------------------------------------------------------------------
    private void getCoordinates() {
        if (locationStartup == null)
            locationStartup = new LocationStartup(context, this);

        if (!coordinateThreadRunning) {
            coordinateThread = new Thread(null, getCoordinatesBackground, "GetCoordinates");
            coordinateThread.start();
            TextView coordinates = findViewById(R.id.monitorStatusView);
            coordinates.setText(getString(R.string.getting_coordinates));
        }
    }

    // run location start up to get the current coordinates
    private Runnable getCoordinatesBackground = new Runnable() {
        public void run() {
            coordinateThreadRunning = true;
            int i = 0;
            locationStartup.startConnection();
            while (i < Constants.TWO_MINUTES_SECONDS && isRunning()) {
                locationStatus = getString(R.string.getting_coordinates) + " " + i++ + " seconds";
                guiHandler.post(updateLocationStatus);
                sleep(1);
                if (locationStartup.isFreshLocation()) // a fresh location was found
                    i = Constants.TWO_MINUTES_SECONDS;
            }

            // update with latest location status
            Location initialLocation = locationStartup.getLocation();
            if (initialLocation == null) {
                locationStatus = getString(R.string.no_coordinates) +
                        " " + Constants.TWO_MINUTES_SECONDS + " seconds ..." ;
            } else {
                float f_lat = (float) initialLocation.getLatitude();
                float f_lon = (float) initialLocation.getLongitude();
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putFloat(Constants.LATITUDE_FLOAT, f_lat);
                editor.putFloat(Constants.LONGITUDE_FLOAT, f_lon);
                monitorStatus = Constants.MONITOR_STARTED;
                editor.putString(Constants.MONITOR_STATUS, monitorStatus);
                editor.apply();
                if (isRunning()) {
                    AsyncTask.execute(new Runnable() {      // update the sunrise and sunset times
                        @Override
                        public void run() {
                            UtilsNetwork.setSunriseSunset(sharedPreferences, context);
                        }
                    });
                }
                locationStatus = "" + formatLocation(initialLocation.getLatitude(), initialLocation.getLongitude());
            }
            locationStartup.stopConnection();
            guiHandler.post(updateLocationStatus);
            coordinateThreadRunning = false;
        }

        private boolean isRunning() {
            return coordinateThreadRunning;
        }
    };

    // update the monitor status on the GUI
    private Runnable updateLocationStatus = new Runnable() {
        @Override
        public void run() {
            TextView tv = findViewById(R.id.monitorStatusView);
            tv.setText(locationStatus);
            setButtonLabel();
        }
    };

    //*-----------------------------------------------------------------
    // get the IP address of the envoy box
    //*------------------------------------------------------------------
    private void getEnvoyIP() {
        if (!envoyThreadRunning) {
            envoyThread = new Thread(null, getEnvoyBackground, "GetEnvoy");
            envoyThread.start();
            TextView envoyView = findViewById(R.id.envoyStatusView);
            envoyView.setText(getString(R.string.getting_envoy));
        }
    }

    // run to check if the envoy is up
    private Runnable getEnvoyBackground = new Runnable() {
        public void run() {
            envoyThreadRunning = true;
            WifiManager wm = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            final WifiInfo connectionInfo = wm.getConnectionInfo();
            int i = 0;         // wait for a second, checking async. with the given IP address from
            sleep(1);  // UtilsNetwork.isEnvoyAlive that was called in onCreate()
            envoyStatus = getString(R.string.getting_envoy);
            guiHandler.post(updateEnvoyStatus);
            String sharedEnvoyStatus = sharedPreferences.getString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);

            // if dead, then try all IPs in the LAN and wait for 45 seconds
            if (sharedEnvoyStatus.equals(Constants.ENVOY_DEAD)) {
                String servers_string = ""; // begin the async. server search task
                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        UtilsNetwork.get_lan_servers(context, connectionInfo);
                    }
                });
                while (i < 45 && isRunning()) {
                    sleep(1);
                    envoyStatus = getString(R.string.getting_envoy) + " " + i++ + " seconds";
                    guiHandler.post(updateEnvoyStatus);
                    servers_string = sharedPreferences.getString(Constants.LAN_SERVERS, "");
                    if (servers_string.length() > 0) break; // found some servers on the lan
                }

                // begin the scan of the list of servers found and check if the envoy is alive
                String[] servers = servers_string.split(Constants.DELIMITER);
                if (isRunning())
                    for (String server : servers)
                        UtilsNetwork.isEnvoyAlive(server, sharedPreferences);  // async. check
                while (i < Constants.ONE_MINUTES_SECONDS && isRunning()) {
                    sleep(1);
                    if (sharedPreferences.getString(
                            Constants.ENVOY_STATUS, Constants.ENVOY_DEAD).equals(Constants.ENVOY_DEAD)) {
                        envoyStatus = getString(R.string.getting_envoy) + " " + i++ + " seconds";
                        guiHandler.post(updateEnvoyStatus);
                    } else {
                        break;  // found it ...
                    }
                }
            }

            envoyStatus = sharedPreferences.getString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
            guiHandler.post(updateEnvoyStatus);
            envoyThreadRunning = false;
        }

        private boolean isRunning() {
            return envoyThreadRunning;
        }

    };

    // update the envoy status on the GUI
    private Runnable updateEnvoyStatus = new Runnable() {
        @Override
        public void run() {
            TextView tv = findViewById(R.id.envoyStatusView);
            String postEnvoyStatus = envoyStatus;
            if (envoyStatus.equals(Constants.ENVOY_ALIVE)) {
                postEnvoyStatus = "Found envoy at IP: " + sharedPreferences.getString(Constants.ENVOY_IP, "");
            } else if (envoyStatus.equals(Constants.ENVOY_DEAD)) {
                postEnvoyStatus = "Envoy is dead";
            }
            tv.setText(postEnvoyStatus);
            setButtonLabel();
        }
    };

    /* draw the button label
      1. If monitoring is in progress (running) show the stop button
      2. If monitoring is finished show the empty button
      3. If monitoring is started (location found), show the start button
      4.  Otherwise, show the start / empty button
    */
    private void setButtonLabel() {
        ImageButton mb = findViewById(R.id.mainButton);
        int resId;
        boolean monitor_enabled;
        switch (monitorStatus) {
            case Constants.MONITOR_RUNNING:
                resId = R.drawable.stop_button;
                monitor_enabled = true;
                break;
            case Constants.MONITOR_FINISHED:
                resId = R.drawable.empty_button;
                monitor_enabled = false;
                break;
            case Constants.MONITOR_STARTED:
                resId = R.drawable.start_button;
                monitor_enabled = true;
                break;
            default:
                if (locationStartup == null || locationStartup.noInitialCoordinates()) {
                    resId = R.drawable.empty_button;
                    monitor_enabled = false;
                } else {
                    resId = R.drawable.start_button;
                    monitor_enabled = true;
                }
                break;
        }

        String sharedEnvoyStatus = sharedPreferences.getString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        if (sharedEnvoyStatus.equals(Constants.ENVOY_DEAD) && !monitorStatus.equals(Constants.MONITOR_RUNNING)) {
            monitor_enabled = false;
        }

        mb.setImageResource(resId);
        mb.setEnabled(monitor_enabled);
    }

    // Start the location service, when the start button is pressed
    public void startTracking() {
        locationStatus = getString(R.string.monitor_in_progress);
        guiHandler.post(updateLocationStatus);
        monitorStatus = Constants.MONITOR_RUNNING;
        //context.startService(new Intent(MainActivity.this, MonitorService.class));

        // Create a new dispatcher using the Google Play driver.
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        Job myJob = dispatcher.newJobBuilder()
                .setService(SolarLogJobService.class) // the JobService that will be called
                .setTag(Constants.LOG_JOB_SERVICE)        // uniquely identifies the job
                .build();
        dispatcher.mustSchedule(myJob);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.MONITOR_STATUS, Constants.MONITOR_RUNNING);
        editor.apply();
    }

    // Stop the location service when the stop button is pressed
    public void stopTracking() {
        if (locationStartup != null)
            locationStartup.setmCurrentLocation(null);
        locationStatus = getString(R.string.monitor_ended);
        guiHandler.post(updateLocationStatus);
        //if (UtilsMisc.isServiceRunning(context, MonitorService.class))
        //    context.stopService(new Intent(MainActivity.this, MonitorService.class));
        //if (UtilsMisc.isServiceRunning(context, SolarLogJobService.class))
        //    context.stopService(new Intent(MainActivity.this, SolarLogJobService.class));
        // clean out preferences for a fresh start
        SharedPreferences.Editor editor = sharedPreferences.edit();
        monitorStatus = Constants.MONITOR_FINISHED;
        editor.putString(Constants.MONITOR_STATUS, Constants.MONITOR_FINISHED);
        editor.putString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        editor.putString(Constants.LOG_FILE, "");
        editor.remove(Constants.LATITUDE_FLOAT);
        editor.remove(Constants.LONGITUDE_FLOAT);
        editor.apply();

        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        dispatcher.cancelAll();

        //solarLog.copy_logcat();
        if (envoyThread != null) { envoyThread.interrupt(); envoyThreadRunning = false; }
        if (coordinateThread != null) { coordinateThread.interrupt(); coordinateThreadRunning = false; }

    }

    private boolean isGooglePlayServicesAvailable() {
        final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if(result != ConnectionResult.SUCCESS) {
            if(googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            }
            return false;
        }
        return true;
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
        }
    }

    private String formatLocation(double lat, double lon) {
        String latLon;
        DecimalFormat df = new DecimalFormat("#.000000");
        latLon = "Lat: " + df.format(lat) + "° Lon: " + df.format(lon) + "°";
        return latLon;
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException ie) {
            //Log.d(TAG, "Could not sleep for " + period + " " + ie.getMessage(), localLog);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (envoyThread != null) { envoyThread.interrupt(); envoyThreadRunning = false; }
        envoyThread = null; envoyThreadRunning = false;
        if (coordinateThread != null) { coordinateThread.interrupt(); envoyThreadRunning = false; }
        coordinateThread = null; coordinateThreadRunning = false;
        Log.d(TAG, "onPause Status " + monitorStatus, localLog);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // ask for GPS permission in Android 6.0+
    // check if GPS Permission has been granted
    public void getPermissions() {
        if (!isPermissionsGranted()) {
            Log.d(TAG, "Getting GPS Permission", localLog);
            Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
            intent.putExtra("permission1", Manifest.permission.ACCESS_FINE_LOCATION);
            intent.putExtra("permission2", Manifest.permission.WRITE_EXTERNAL_STORAGE);
            startActivityForResult(intent, Constants.PERMISSION_CODE);
        }
    }

    private boolean isPermissionsGranted() {
        return (Constants.preMarshmallow ||
                (Constants.postMarshmallow &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                                PackageManager.PERMISSION_GRANTED));
    }

}