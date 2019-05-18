package org.mkonchady.solarmonitor;

import android.os.Build;

public final class Constants {

    // constants for the monitor status
    final static String MONITOR_STATUS = "monitor_status";
    final static String MONITOR_NOT_STARTED = "monitor_not_started";
    final static String MONITOR_STARTED = "monitor_started";
    final static String MONITOR_RUNNING = "monitor_running";
    final static String MONITOR_FINISHED = "monitor_finished";

    // Time constants
    final static long MILLISECONDS_PER_DAY = 86400 * 1000;
    final static long MILLISECONDS_PER_MINUTE = 60 * 1000;
    final static long MILLISECONDS_PER_SECOND = 1000;
    final static int TWO_MINUTES_SECONDS = 60 * 2;
    final static int ONE_MINUTES_SECONDS = 60;
    final static String UTC_TIMEZONE = "UTC";
    final static double MILLION = 1000000.0;
    final static float LARGE_FLOAT = 10000000.0f;
    final static int LARGE_INT = 2147483647;

    // envoy keys
    final static String[] envoy_keys = {"watt_hours", "watts_now", "envoy_timestamp"};
    final static String ENVOY_PARMS = "/api/v1/production/";
    final static String DEFAULT_ENVOY_IP = "192.168.1.10";

    // openweather map keys
    final static String[] openweather_keys = { "weather_main", "weather_description",
            "temp", "temp_min", "temp_max", "humidity", "wind_speed", "wind_deg", "clouds_all",
            "open_timestamp", "sunrise_timestamp", "sunset_timestamp", "name"};

    private final static String OPENWEATHER_SITE = "http://api.openweathermap.org/data/2.5/weather";
    private final static String OPENWEATHER_APPID = "c5d14b4cafe6af9c3e1606b744f7****";
    final static String OPENWEATHER_URL = OPENWEATHER_SITE + "?appid=" + OPENWEATHER_APPID;

    final static String LOG_JOB_SERVICE = "solar_log_job_service";
    final static int NET_NUMBER_TRIES = 5;
    final static long NET_TIMEOUT_MILLSECONDS = Constants.MILLISECONDS_PER_MINUTE;
    final static int PING_TIMEOUT_MILLSECONDS = 15000;

    // debug modes
    //  public final static String DEBUG_NO_MESSAGES = "0";
    final static String DEBUG_LOCAL_FILE = "1";
    //  public final static String DEBUG_ANDROID_LOG = "2";
    final static int KEEP_LOGS_DAYS = 90;
    final static String LATITUDE_FLOAT = "lat_preference";
    final static String LONGITUDE_FLOAT = "lon_preference";

    // shared parameters for the log
    final static String LONGITUDE_INT = "Longitude";
    final static String LATITUDE_INT = "Latitude";
    final static String LOG_FROM_DATE = "from_date";   // from date timestamp
    final static String SUNRISE = "sunrise";
    final static String SUNSET = "sunset";
    final static String LAN_SERVERS = "lan_servers";
    final static String LOG_FILE = "Log_file";
    final static String LOG_STATUS = "Log_status";

    // shared parameters based on the settings -- key is stored in preferences.xml
    final static String MONITORING_FREQUENCY = "Monitoring_frequency";
    final static String DEBUG_MODE = "Debug_mode";
    final static String NUMBER_LOG_ROWS = "Number_of_log_rows";
    final static String ENVOY_IP = "envoy_ip";
    final static String ENVOY_STATUS = "envoy_status";
    final static String ENVOY_ALIVE = "envoy_alive";
    final static String ENVOY_DEAD = "envoy_dead";

    // location preferences (shared parameters)
    final static String LOCATION_PROVIDER = "Location_provider";
    final static String LOCATION_ACCURACY = "Location_accuracy";
    final static String FILE_FORMAT = "File_format";
    final static String LOG_ID = "log_id";

    // constants for plots
    final static String DATA_BUNDLE = "data_bundle";
    final static int DATA_WATTHOURS = 0;
    final static int DATA_WATTSNOW = 1;
    final static int DATA_TEMPERATURE = 2;
    final static int DATA_WIND = 3;
    final static int DATA_CLOUDS = 4;
    final static int DATA_PEAKWATTS = 5;
    final static int DATA_PEAKTIME = 6;
    final static int DATA_SUNLIGHT = 7;
    final static int DATA_READINGS = 8;
    final static int DATA_MONTHLY_TEMPERATURE = 9;
    final static int DATA_MONTHLY_CLOUDS = 10;
    final static int DATA_INVALID = -100000;
    final static int ALL_SEGMENTS = -1;

    // android versions
    final static boolean preMarshmallow  =  Build.VERSION.SDK_INT < Build.VERSION_CODES.M; // < 23
    final static boolean postMarshmallow =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.M; // >= 23
    final static boolean postNougat =  Build.VERSION.SDK_INT >= Build.VERSION_CODES.N; // >= 24
    final static int PERMISSION_CODE = 100;

    final static String DELIMITER = "!!!";
    final static String VERSION = "0.1";
    final static String NEWLINE = System.getProperty("line.separator");

    /**
     * no default constructor
     */
    private Constants() {
        throw new AssertionError();
    }

}
