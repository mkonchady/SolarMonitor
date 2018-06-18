package org.mkonchady.solarmonitor;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

final class SolarLog {

    private String current_logfile = "";
    private SharedPreferences sharedPreferences;
    private File logDirectory = null;
    private Context context = null;
    final String TAG = "SolarLog";

    SolarLog(Context context) {
        this.context = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        // ensure that the logs directory is available
        if (!externalStorageAvailable()) return;
        logDirectory = new File(context.getExternalFilesDir(null), "logs");
        if (!logDirectory.exists())
            if (!logDirectory.mkdirs())
                return;

        // use the current timestamp to get the current date
        current_logfile = "logs/" + UtilsDate.getLogfileDateTime(System.currentTimeMillis()) + ".log";
    }

    int numLines() {
        try {
            return UtilsFile.countLines(context, current_logfile);
        } catch (IOException ie) {
            Log.e(TAG, "IO Error: " + ie.getMessage());
            return 0;
        }
    }

    void append(String msg) {
        BufferedWriter buf;
        File file;
        if (!externalStorageAvailable())
            return;
        try {
            file = new File(context.getExternalFilesDir(null), current_logfile);
            if (!file.exists()) {
                if (!file.createNewFile())
                    throw new IOException();
                FileOutputStream fos = new FileOutputStream(file, false);
                String firstline = "#" + TextUtils.join(",", Constants.envoy_keys) + "," +
                        TextUtils.join(",", Constants.openweather_keys) + Constants.NEWLINE;
                fos.write(firstline.getBytes());
                fos.close();
            }

            //Log.d("Log", "Appending " + msg);
            buf = new BufferedWriter(new FileWriter(file, true));
            buf.append(msg);
            buf.newLine();
            buf.flush();
            buf.close();
            UtilsFile.forceIndex(context, current_logfile);
        } catch (IOException ie) {
            Log.e(TAG, "Local IO Exception: " + ie.getMessage());
        }
    }

    void clean_up_old_files() {
        // get a list of existing log files in the directory and clean up old files
        long DELETE_INTERVAL = Constants.MILLISECONDS_PER_DAY * Constants.KEEP_LOGS_DAYS; // for old log files
        for (File f : logDirectory.listFiles()) {
            if ((f.isFile()) && ((System.currentTimeMillis() - f.lastModified()) > DELETE_INTERVAL))
                if (!f.delete())
                    Log.e(TAG, "Could not delete old log files");
        }

    }
/*
    void copy_logcat() {
        try {
            String filepath = Environment.getExternalStorageDirectory() + "logs/logcat.txt";
            Runtime.getRuntime().exec(new String[]{"logcat", "-f", filepath, "org.mkonchady.solarmonitor:V", "*:S"});
        } catch (IOException ie) {
            Log.e(TAG, "Copy logcat IOException : " + ie.getMessage());
        }
    }
*/
    /*
      1. Get the current date and loop through the log directory for unprocessed log files.
      2. For each unprocessed log file, read all the lines
      3. For each line, extract all the fields and build each detail record
      4. Build a single summary record

        envoy keys {"watt_hours", "watts_now", "envoy_timestamp"};
        openweather keys{ "weather_main", "weather_description",
            "temp", "temp_min", "temp_max", "humidity", "wind_speed", "wind_deg", "clouds_all",
            "open_timestamp", "sunrise_timestamp", "sunset_timestamp", "name"};

     */
    void process_logfiles() {
        SummaryDB summaryDB = new SummaryDB(SummaryProvider.db);
        //summaryDB.delSummary(context, "2018-02-28", true);
        //int monitoring_frequency = Integer.parseInt(sharedPreferences.getString(Constants.MONITORING_FREQUENCY, "5"));

        DetailDB detailDB = new DetailDB(DetailProvider.db);
        String current_date = UtilsDate.getLogfileDateTime(System.currentTimeMillis());
        for (File f :logDirectory.listFiles()) {
            if (f.isFile()) {
                String fileName = f.getName();
                if (!fileName.startsWith("201")) continue; // skip other logs
                String file_date = fileName.substring(0, fileName.length() - 4);
                if (!file_date.equals(current_date) && !summaryDB.isInSummaryTable(context, file_date)) {
                    SummaryProvider.Summary summary = new SummaryProvider.Summary(0, file_date,
                            0, 0, 0, 0, 0,
                            Constants.MONITOR_RUNNING, "");
                    summaryDB.addSummary(context, summary);
                    int monitor_id = summaryDB.getMonitorID(context, file_date);
                    int peak_watts = 0, peak_generated_watts = 0;
                    long peak_time = 0L;
                    long sunrise = 0L, sunset = 0L;
                    int index = 0;
                    String[] fileLines = UtilsFile.readFile(context, "logs/" + file_date + ".log", true);
                    //int min_temp = 373; int max_temp = 0; // in kelvin

                    // need at least two entries
                    if (fileLines.length < 2) continue;

                    // keep track of the list of temp, wind, cloud, watts, and time between readings
                    ArrayList<Float> temps = new ArrayList<>();
                    ArrayList<Float> winds = new ArrayList<>();
                    ArrayList<Float> clouds = new ArrayList<>();
                    ArrayList<Integer> watts = new ArrayList<>();
                    ArrayList<Long> reading_time = new ArrayList<>();
                    HashSet<String> fileLineSet = new HashSet<>();
                    for (String fileLine: fileLines) {

                        if (!isValidLine(fileLine)) continue;           // check if it is a valid line

                        if (fileLineSet.contains(fileLine)) continue;   // check if it is a duplicate line
                        fileLineSet.add(fileLine);

                        String[] values = fileLine.split(",");

                        // replace nulls with zeroes
                        values = UtilsMisc.fixNulls(values, "0");

                        int generated_watts = Integer.parseInt(values[0]);
                        int watts_now = Integer.parseInt(values[1]);
                        watts.add(watts_now);

                        long envoy_timestamp = Long.parseLong(values[2]);
                        reading_time.add(envoy_timestamp);

                        String weather = values[4];

                        float temperature = Float.parseFloat(values[5]);
                        temps.add(temperature);
                        //int i_temperature = Math.round(temperature);
                        //if (i_temperature > max_temp) max_temp = i_temperature;
                        //if (i_temperature < min_temp) min_temp = i_temperature;

                        float wind_speed = Float.parseFloat(values[9]);
                        winds.add(wind_speed);
                        float cloud = Float.parseFloat(values[11]);
                        clouds.add(cloud);

                        sunrise = Long.parseLong(values[13]);
                        sunset = Long.parseLong(values[14]);
                        index++;

                        DetailProvider.Detail detail = new DetailProvider.Detail(monitor_id, envoy_timestamp,
                                generated_watts, watts_now, weather, temperature, cloud, wind_speed, index + "");
                        detailDB.addDetail(context, detail);
                        if (peak_watts < watts_now) {
                            peak_watts = watts_now;
                            peak_time = envoy_timestamp;
                        }
                        peak_generated_watts = (generated_watts > peak_generated_watts)? generated_watts: peak_generated_watts;
                    }

                    ContentValues values = new ContentValues();  // save time values in milliseconds
                    values.put(SummaryProvider.START_TIME, sunrise * 1000);
                    values.put(SummaryProvider.END_TIME, sunset *1000);
                    values.put(SummaryProvider.NAME, summary.getName());
                    values.put(SummaryProvider.PEAK_WATTS, peak_watts);
                    values.put(SummaryProvider.PEAK_TIME, peak_time * 1000);
                    values.put(SummaryProvider.GENERATED_WATTS, peak_generated_watts);
                    values.put(SummaryProvider.STATUS, Constants.MONITOR_FINISHED);

                    String extras = summaryDB.getExtras(context, monitor_id);
                    float[] minMaxFloat = UtilsMisc.getMinMaxFloat(temps); float avg_float = UtilsMisc.getAverageFloat(temps);
                    extras =  summaryDB.appendSummaryExtras(minMaxFloat[0], minMaxFloat[1], avg_float, "temp", extras);

                    minMaxFloat = UtilsMisc.getMinMaxFloat(winds); avg_float = UtilsMisc.getAverageFloat(winds);
                    extras = summaryDB.appendSummaryExtras(minMaxFloat[0], minMaxFloat[1], avg_float, "wind", extras);

                    minMaxFloat = UtilsMisc.getMinMaxFloat(clouds); avg_float = UtilsMisc.getAverageFloat(clouds);
                    extras = summaryDB.appendSummaryExtras(minMaxFloat[0], minMaxFloat[1], avg_float, "clouds", extras);

                    float[] minMaxLongs = UtilsMisc.getMinMaxGaps(reading_time);
                    extras = summaryDB.appendSummaryExtras(minMaxLongs[0], minMaxLongs[1], minMaxLongs[2], "reading_time", extras);

                    int[] minMaxInts = UtilsMisc.getMinMaxInt(watts);
                    float avg_int = UtilsMisc.getAverageInt(watts);
                    extras = summaryDB.appendSummaryExtras(minMaxInts[0], minMaxInts[1], avg_int, "watts", extras);

                    extras = summaryDB.appendSummaryExtras("readings", index + "", extras);

                    values.put(SummaryProvider.EXTRAS, extras);
                    summaryDB.setSummaryParameters(context, values, monitor_id);
                }
            }
        }
    }

    private boolean isValidLine(String line) {
        String[] values = line.split(",");
        for (String value: values)
            if (value.equalsIgnoreCase("null"))
                return false;

        if (values.length != 16)
            return false;
        else if (Integer.parseInt(values[0]) == 0) // check for non-zero generated watts
            return false;

        return true;
    }

    private boolean externalStorageAvailable() {
        return (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) );
    }

}