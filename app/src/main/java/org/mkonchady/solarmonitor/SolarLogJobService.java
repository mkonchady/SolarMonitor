package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

/*
    Trigger time setting:

    1. Pre-sunrise (< sunrise): set to previous day's sunrise + 24 hours
    2. Monitoring period (>= sunrise and < sunset): set to previous alarm + period (5 minutes)
    3. Post-sunset (>= sunset): set to current sunrise + 24 hours

 */
public class SolarLogJobService extends JobService {

    private final static String TAG = "SolarLogJobService";
    Context context;
    SolarLog solarLog;
    Log log;
    int localLog;
    final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();

    private static long sharedSunset = 0L;
    private static long sharedSunrise = 0L;
    private static SharedPreferences sharedPreferences;

    // build the envoy and openweather URLs
    String ENVOY_IP;
    Runnable mainTask;

    final String OPENWEATHER_URL = Constants.OPENWEATHER_URL;
    private String OPENWEATHER_PARMS;
    float f_lat, f_lon;
    boolean retryJob;

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        this.context = getApplicationContext();
        final long current_time = System.currentTimeMillis();

        // build the globals using shared prefs
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
        Log.d(TAG, "Started job", localLog);

        // build the openweather and envoy urls
        f_lat = sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0.0f);
        f_lon = sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0.0f);
        OPENWEATHER_PARMS = "&lat=" + f_lat + "&lon=" + f_lon;
        ENVOY_IP = sharedPreferences.getString(Constants.ENVOY_IP, Constants.DEFAULT_ENVOY_IP);

        // check if the envoy is up
        final int TIMECHECK_CURRENT_IP = 5;         // time in seconds
        final int TIMECHECK_SERVER_LIST = 30;
        final int TIMECHECK_SERVERS = 15;

        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        edit.apply();
        UtilsNetwork.isEnvoyAlive(ENVOY_IP, sharedPreferences);
        sleep(TIMECHECK_CURRENT_IP);
        String envoy_status = sharedPreferences.getString(Constants.ENVOY_STATUS, Constants.ENVOY_DEAD);
        if (envoy_status.equals(Constants.ENVOY_DEAD)) {
            // start the search for the server and wait 30 seconds
            WifiManager wm = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo connectionInfo = wm.getConnectionInfo();
                UtilsNetwork.get_lan_servers(context, connectionInfo);  // build the list of live http servers on the lan
                sleep(TIMECHECK_SERVER_LIST);
                // scan the list of servers and check if the envoy is alive
                String serverList = sharedPreferences.getString(Constants.LAN_SERVERS, "");
                Log.d(TAG, "List of servers: " + serverList, localLog);
                String[] servers = serverList.split(Constants.DELIMITER);
                for (String server : servers) UtilsNetwork.isEnvoyAlive(server, sharedPreferences);
                sleep(TIMECHECK_SERVERS);
            }
            Log.d(TAG, "Retrying with Envoy IP: " +
                    sharedPreferences.getString(Constants.ENVOY_IP, Constants.DEFAULT_ENVOY_IP), localLog);
        }

        // set up the logs
        solarLog = new SolarLog(context);
        log = new Log(context);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        sharedSunrise = sharedPreferences.getLong(Constants.SUNRISE, -1);
        sharedSunset = sharedPreferences.getLong(Constants.SUNSET,-1);

        mainTask = new Runnable() {
            @Override
            public void run() {
                if (sharedSunset < current_time || sharedSunrise == -1) {   /*--- 1. Pre-sunrise: */
                    UtilsNetwork.setSunriseSunset(sharedPreferences, context);
                    final long current_time = System.currentTimeMillis();
                    sharedSunrise = sharedPreferences.getLong(Constants.SUNRISE, current_time);
                    sharedSunset = sharedPreferences.getLong(Constants.SUNSET, current_time + 43200);
                }
                dispatchJob(current_time, jobParameters);
            }
        };

        AsyncTask.execute(mainTask);
        return true; // Answers the question: "Is there still work going on?"
    }

    // get the time for the next job and set in dispatcher. Run the job
    private void dispatchJob(long current_time, JobParameters job) {

        // set the next alarm or stop if monitoring is finished
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(context));
        long trigger_time = getNextAlarm(current_time); // in milliseconds

        if (trigger_time == -1) stopSelf();
        current_time = current_time + Constants.MILLISECONDS_PER_SECOND;
        long nextTime = (trigger_time < current_time) ? current_time : (trigger_time - current_time);
        int startTime = (int) (nextTime / 1000);
        // set the schedule for the next job run
        Job myJob = dispatcher.newJobBuilder()
                .setService(SolarLogJobService.class)
                .setTag(Constants.LOG_JOB_SERVICE)                   // uniquely identifies the job
                .setRecurring(false)                                 // one-off job
                .setLifetime(Lifetime.UNTIL_NEXT_BOOT)               // don't persist past a device reboot
                .setTrigger(Trigger.executionWindow(startTime, startTime + 60))
                .setReplaceCurrent(true)                            // overwrite an existing job with the same tag
                .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR)
                .setConstraints(Constraint.ON_UNMETERED_NETWORK)
                .build();
        dispatcher.mustSchedule(myJob);
        // run the job
        try {
            callEnvoyOpenWeather();
        } catch (IOException e) {
            retryJob = true;
            Log.e(TAG, "IO Exception: " + e.getMessage() + ":", localLog);
        }
        jobFinished(job, retryJob);
    }

    // set the alarm to run monitor service again
    private long getNextAlarm(long current_time) {
        // return if we are no longer tracking
        String monitor_status = sharedPreferences.getString(Constants.MONITOR_STATUS, Constants.MONITOR_FINISHED);
        if (monitor_status.equals(Constants.MONITOR_FINISHED))
            return -1L;

        /* 2. >= Sunrise and < sunset : set to previous alarm + 5 minutes
           3. >= Sunset: set to current sunrise + 24 hours*/
        final int monitoring_frequency = Integer.parseInt(sharedPreferences.getString(Constants.MONITORING_FREQUENCY, "5"));
        long trigger_time = (current_time >= sharedSunset) ?  sharedSunrise + Constants.MILLISECONDS_PER_DAY:
                                                            current_time + 1000 * 60 * monitoring_frequency;

        // if after sharedSunset, then fix the sharedSunrise and sharedSunrise values in shared preferences
        if (sharedSunset < current_time)
            updateSharedSunriseSunset( sharedSunrise + Constants.MILLISECONDS_PER_DAY, sharedSunset + Constants.MILLISECONDS_PER_DAY,
                    getString(R.string.monitor_in_progress));

        int i_lat = (int) Math.round(f_lat * Constants.MILLION);
        int i_lon = (int) Math.round(f_lon * Constants.MILLION);
        Log.d(TAG, "Set alarm for " + UtilsDate.getDateTime(trigger_time, i_lat, i_lon), localLog);
        return trigger_time;
    }


    @Override
    public boolean onStopJob(JobParameters job) {
        Log.d(TAG, "Stopped job" , localLog);
        return retryJob; // Answers the question: "Should this job be retried?"
    }

    @Override
    public void onCreate() {
    }
    /*
        1. First call Envoy to get the current watts generated, etc.
        2. Then call OpenWeather to get the current weather
    */
    private void callEnvoyOpenWeather() throws IOException {

        retryJob = false;
        final OkHttpClient okClient = new OkHttpClient();
        final OkHttpClient client = okClient.newBuilder()
                .connectTimeout(Constants.NET_TIMEOUT_MILLSECONDS, TimeUnit.MILLISECONDS)
                .readTimeout(Constants.NET_TIMEOUT_MILLSECONDS, TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();
        String ENVOY_URL = (ENVOY_IP.startsWith("http://"))? ENVOY_IP: "http://" + ENVOY_IP;
        Request request1 = new Request.Builder().url(ENVOY_URL + Constants.ENVOY_PARMS).build();
        client.newCall(request1).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                Log.e(TAG, "Call to Envoy cancelled: " + e.getMessage(), localLog);
                retryJob = true;
                stopSelf();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Envoy Unexpected response: " + response);
                }
                final String envoyResponse = response.body().string();  // save the envoy response
                Request request2 = new Request.Builder().url(OPENWEATHER_URL + OPENWEATHER_PARMS).build();
                client.newCall(request2).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        call.cancel();
                        Log.e(TAG, "Call to OpenWeather cancelled: " + e.getMessage(), localLog);
                        retryJob = true;
                        stopSelf();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            throw new IOException("Openweather Unexpected response: " + response);
                        }
                        final String openWeatherResponse = response.body().string();
                        Map<String, String> envoy_map = UtilsNetwork.parseEnvoyJSON(envoyResponse);
                        Map<String, String> openWeather_map = UtilsNetwork.parseOpenWeatherJSON(openWeatherResponse);
                        StringBuilder outline = new StringBuilder();
                        for (String key : Constants.envoy_keys) {
                            outline.append(envoy_map.get(key)); outline.append(",");
                        }
                        for (String key : Constants.openweather_keys) {
                            outline.append(openWeather_map.get(key)); outline.append(",");
                        }
                        solarLog.append(UtilsFile.removeLastChar(outline.toString(), ','));
                        // save the sharedSunrise and sharedSunset timestamps in shared preferences
                        long sunrise = Long.parseLong(openWeather_map.get("sunrise_timestamp")) * 1000L;
                        long sunset = Long.parseLong(openWeather_map.get("sunset_timestamp")) * 1000L;
                        // build the log status
                        int i_lat = (int) Math.round(f_lat * Constants.MILLION);
                        int i_lon = (int) Math.round(f_lon * Constants.MILLION);
                        String log_status = solarLog.numLines() + " readings till "
                                + UtilsDate.getTime(System.currentTimeMillis(), i_lat, i_lon) +
                                " (" + envoy_map.get("watt_hours") + " w)";
                        updateSharedSunriseSunset(sunrise, sunset, log_status);
                        Log.d(TAG, "Successfully appended to Log", localLog);
                        stopSelf();
                    }
                });
            }
        });
    }

    private void updateSharedSunriseSunset(long sunrise, long sunset, String log_status) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong(Constants.SUNRISE, sunrise);
        editor.putLong(Constants.SUNSET, sunset);
        editor.putString(Constants.LOG_STATUS, log_status);
        editor.apply();
    }


    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException ie) {
            Log.e(TAG, "Could not sleep: " + ie.getMessage(), localLog);
        }
    }

}

