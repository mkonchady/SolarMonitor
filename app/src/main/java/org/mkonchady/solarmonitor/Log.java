package org.mkonchady.solarmonitor;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Log {
    private static File logFileHandle = null;
    private static String logFile = "";

    // create the log file if necessary
    public Log(Context context)  {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "0"));

        // return if debug mode is "no messages" or Android Log
        if (localLog != Integer.parseInt(Constants.DEBUG_LOCAL_FILE)) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(Constants.LOG_FILE, "");
            editor.apply();
            return;
        }

        // ensure that the logs directory is available
        if (!externalStorageAvailable()) return;
        File logDirectory = new File(context.getExternalFilesDir(null), "logs");
        if (!logDirectory.exists())
            if (!logDirectory.mkdirs())
                return;

        // check if a Log FileHandle was created earlier, if not same as today, create a new one
        logFile = sharedPreferences.getString(Constants.LOG_FILE, "");
        if (logFile.length() == 0) {
            createNewLogfile(sharedPreferences, context);
        } else {
            String fileDate = UtilsDate.getLogfileDateTime(logFile, "monitor_", ".log");
            String currentDate = UtilsDate.getLogfileDateTime(System.currentTimeMillis());
            if (fileDate.equals(currentDate)) {
                UtilsFile.forceIndex(context, logFile);
                logFileHandle = new File(context.getExternalFilesDir(null), logFile);
            } else {
                createNewLogfile(sharedPreferences, context);
            }
        }

        // get a list of existing Log files in the directory and clean up old files
        long DELETE_INTERVAL = Constants.MILLISECONDS_PER_DAY * Constants.KEEP_LOGS_DAYS; // for old Log files
        for (File f :logDirectory.listFiles()) {
            if ( (f.isFile()) && ( (System.currentTimeMillis()-f.lastModified()) > DELETE_INTERVAL) )
                if (!f.delete())
                    e("Log", "Could not delete old log files");
        }

    }

    // Android Log functions
    public static void d(String TAG, String msg) {
        android.util.Log.d(TAG, msg);
    }

    public static void e(String TAG, String msg) {
        android.util.Log.e(TAG, msg);
    }

    public static void i(String TAG, String msg) {
        android.util.Log.e(TAG, msg);
    }

    // Local functions
    public static void d(String TAG, String msg, int local)  {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Debug");
        else d(TAG, msg);
    }

    public static void e(String TAG, String msg, int local) {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Error");
        else e(TAG, msg);
    }

    public static void i(String TAG, String msg, int local) {
        if (local == 0) return;
        if (local == 1) append(TAG, msg, "Info");
        else i(TAG, msg);
    }

    private static void createNewLogfile(SharedPreferences sharedPreferences, Context context) {
        logFile = "logs/monitor_" + UtilsDate.getLogfileDateTime(System.currentTimeMillis()) + ".log";
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.LOG_FILE, logFile);
        editor.apply();
        try {
            logFileHandle = new File(context.getExternalFilesDir(null), logFile);
            if (!logFileHandle.exists()) {
                if (logFileHandle.createNewFile()) {
                    FileOutputStream fos = new FileOutputStream(logFileHandle, false);
                    String logLine = UtilsDate.getDateTimeSec(System.currentTimeMillis(), Constants.LARGE_INT, Constants.LARGE_INT) + ": Debug: Log: " +
                            "------ Start of Log --------" + Constants.NEWLINE;
                    fos.write(logLine.getBytes());
                    fos.close();
                } else {
                    throw new IOException();
                }
            }
        } catch (IOException ie) {
            android.util.Log.e("Log", "Open IO Exception: " + ie.getMessage());
        } finally {
            UtilsFile.forceIndex(context, logFile);
        }
    }


    private static void append(String TAG, String msg, String type)  {
        if (!externalStorageAvailable())
            return;
        try {
            if (logFileHandle == null) return;
            FileOutputStream fos = new FileOutputStream(logFileHandle, true);
            String logLine = UtilsDate.getDateTimeSec(System.currentTimeMillis(), Constants.LARGE_INT, Constants.LARGE_INT) +
                    ": " + type + ": " + TAG + ": " + msg + Constants.NEWLINE;
            fos.write(logLine.getBytes());
            fos.close();
        } catch (IOException ie) {
            android.util.Log.e("Log", "Local IO Exception: " + ie.getMessage());
        }
    }

    private static boolean externalStorageAvailable() {
        return (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) );
    }

}