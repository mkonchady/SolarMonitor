package org.mkonchady.solarmonitor;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import java.util.HashMap;

/*------------------------------------------------------------
  Content provider for Details
  Latitude and longitude stored in microdegrees
 -------------------------------------------------------------*/
public class DetailProvider extends ContentProvider {

    // match with the authority in manifest
    static final String PROVIDER_NAME = "org.mkonchady.solarmonitor.DetailProvider";
    static final String DATABASE_NAME = "detail.db";
    static final String READING = "d_reading";
    static final String OPEN_READING = "<" + READING + ">";
    static final String CLOSE_READING = "<" + READING + "/>";
    static final int NUM_FIELDS = 9;
    static final int DATABASE_VERSION = 1;

    // detail table
    // latitude and longitude are in micro degrees
    static final String DETAIL_TABLE = "detail";
    static final String DETAIL_ROW = "content://" + PROVIDER_NAME + "/" + DETAIL_TABLE;
    static final String CREATE_DETAIL =
            " CREATE TABLE " + DETAIL_TABLE +
                    " (d_monitor_id INTEGER NOT NULL, " +
                    " d_timestamp INTEGER NOT NULL, " +
                    " d_watts_generated INTEGER NOT NULL, " +
                    " d_watts_now INTEGER NOT NULL, " +
                    " d_weather TEXT NOT NULL, " +
                    " d_temperature REAL NOT NULL, " +
                    " d_clouds REAL NOT NULL, " +
                    " d_wind_speed REAL NOT NULL," +
                    " d_index TEXT) ";
    static final int DETAILS = 3;
    static final int DETAIL = 4;

    // detail table columns
    static final String MONITOR_ID = "d_monitor_id";
    static final String TIMESTAMP = "d_timestamp";
    static final String WATTS_GENERATED = "d_watts_generated";
    static final String WATTS_NOW = "d_watts_now";
    static final String WEATHER = "d_weather";
    static final String TEMPERATURE = "d_temperature";
    static final String CLOUDS = "d_clouds";
    static final String WIND_SPEED = "d_wind_speed";
    static final String INDEX = "d_index";

   // private static HashMap<String, String> DETAIL_PROJECTION_MAP;

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, DETAIL_TABLE + "/#", DETAIL);
        uriMatcher.addURI(PROVIDER_NAME, DETAIL_TABLE, DETAILS);
    }
    public static SQLiteDatabase db;

    // Database helper class
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_DETAIL);
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + DETAIL_TABLE);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
        if (db == null) {
            db = dbHelper.getReadableDatabase();
            if (db == null)
                return false;
        }
        return true;
    }

    @Override
    public Uri insert(@NonNull  Uri uri, ContentValues values) {
        long row;
        Uri _uri = null;
        switch (uriMatcher.match(uri)) {
            case DETAILS:
                row = db.insert(DETAIL_TABLE, "", values);
                if (row >= 0) {
                    _uri = ContentUris.withAppendedId(Uri.parse(DETAIL_ROW), row);
                    if (getContext() != null)
                        getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            default:
                break;
        }
        if (_uri != null)
            return _uri;
        throw new SQLException("Did not add row in Detail table " + uri);
    }

    @Override
    public Cursor query(@NonNull  Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        HashMap<String, String> DETAIL_PROJECTION_MAP = new HashMap<>();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (uriMatcher.match(uri)) {
            case DETAIL:
                qb.setTables(DETAIL_TABLE);
                qb.appendWhere( MONITOR_ID + "=" + uri.getPathSegments().get(1));
                break;
            case DETAILS:
                qb.setTables(DETAIL_TABLE);
                qb.setProjectionMap(DETAIL_PROJECTION_MAP);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // run the query
        Cursor c = qb.query(db,	projection,	selection, selectionArgs,
                null, null, sortOrder);
        if (getContext() != null)
            c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int delete(@NonNull  Uri uri, String selection, String[] selectionArgs) {
        int count;
        String log_id;
        switch (uriMatcher.match(uri)){
            case DETAIL:
                log_id = uri.getPathSegments().get(1);
                count = db.delete(DETAIL_TABLE, MONITOR_ID +  " = " + log_id +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            case DETAILS:
                count = db.delete(DETAIL_TABLE, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (getContext() != null)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull  Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int count;
        switch (uriMatcher.match(uri)){
            case DETAILS:
                count = db.update(DETAIL_TABLE, values,
                        selection, selectionArgs);
                break;
            case DETAIL:
                count = db.update(DETAIL_TABLE, values, MONITOR_ID +
                        " = " + uri.getPathSegments().get(1) +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri );
        }
        if (getContext() != null)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(@NonNull  Uri uri) {
        switch (uriMatcher.match(uri)){
            case DETAILS:
                return "vnd.android.cursor.dir/vnd.example.details";
            case DETAIL:
                return "vnd.android.cursor.item/vnd.example.detail";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    public static Detail createDetail(int monitor_id, String[] fields) {
        for (int i = 0; i < fields.length; i++)
            fields[i] = fields[i].trim();
        long timestamp = Long.parseLong(fields[1]);
        int watts_generated = Integer.parseInt(fields[2]);
        int watts_now = Integer.parseInt(fields[3]);
        String weather = fields[4];
        float temperature = Float.parseFloat(fields[5]);
        float clouds = Float.parseFloat(fields[6]);
        float wind_speed = Float.parseFloat(fields[7]);
        String index = fields[8];
        return (new DetailProvider.Detail(
                monitor_id, timestamp, watts_generated, watts_now, weather, temperature, clouds, wind_speed, index) );
    }

    // Class for Detail
    public static class Detail {

        private int monitor_id;
        private long timestamp;
        private int watts_generated;
        private int watts_now;
        private String weather;
        private float temperature;
        private float clouds;
        private float wind_speed;
        private String index;

        Detail(int monitor_id, long timestamp, int watts_generated, int watts_now, String weather, float temperature,
               float clouds, float wind_speed, String index) {
            this.monitor_id = monitor_id;
            this.timestamp = timestamp;
            this.watts_generated = watts_generated;
            this.watts_now = watts_now;
            this.weather = weather;
            this.temperature = temperature;
            this.clouds = clouds;
            this.wind_speed = wind_speed;
            this.index = index;
        }

        public String toString(String format) {
            if (format.equalsIgnoreCase("csv"))
                return monitor_id + "," + timestamp + ", " + watts_generated + ", " + watts_now + ", " +
                        weather + ", " + temperature + ", " + clouds + "," + wind_speed + ", " + index;

            if (format.equalsIgnoreCase("xml")) {
                String newline = Constants.NEWLINE;
                return  ( OPEN_READING + newline +
                    ("     <" + DetailProvider.MONITOR_ID + ">" + monitor_id + "</" + DetailProvider.MONITOR_ID + ">" + newline) +
                    ("     <" + DetailProvider.TIMESTAMP + ">" + timestamp  + "</" + DetailProvider.TIMESTAMP + ">" + newline) +
                    ("     <" + DetailProvider.WATTS_GENERATED + ">" + watts_generated + "</" + DetailProvider.WATTS_GENERATED + ">" + newline) +
                    ("     <" + DetailProvider.WATTS_NOW + ">" + watts_now + "</" + DetailProvider.WATTS_NOW + ">" + newline) +
                    ("     <" + DetailProvider.WEATHER + ">" + weather + "</" + DetailProvider.WEATHER + ">" + newline) +
                    ("     <" + DetailProvider.TEMPERATURE + ">" + temperature + "</" + DetailProvider.TEMPERATURE + ">" + newline) +
                    ("     <" + DetailProvider.CLOUDS + ">" + clouds + "</" + DetailProvider.CLOUDS + ">" + newline) +
                    ("     <" + DetailProvider.WIND_SPEED + ">" + wind_speed + "</" + DetailProvider.WIND_SPEED + ">" + newline) +
                    ("     <" + DetailProvider.INDEX + ">" + index + "</" + DetailProvider.INDEX + ">" + newline)
                    + CLOSE_READING);
            }
            return ("");
        }

        public int getMonitor_id() {
            return monitor_id;
        }

        public void setMonitor_id(int monitor_id) {
            this.monitor_id = monitor_id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public int getWatts_generated() {
            return watts_generated;
        }

        public void setWatts_generated(int watts_generated) {
            this.watts_now = watts_generated;
        }

        public int getWatts_now() {
            return watts_now;
        }

        public void setWatts_now(int watts_now) {
            this.watts_now = watts_now;
        }

        public String getWeather() {
            return weather;
        }

        public void setWeather(String weather) {
            this.weather = weather;
        }

        public float getTemperature() {
            return temperature;
        }

        public void setTemperature(float temperature) {
            this.temperature = temperature;
        }

        public float getClouds() {
            return clouds;
        }

        public void setClouds(float clouds) {
            this.clouds = clouds;
        }

        public float getWind_speed() {
            return wind_speed;
        }

        public void setWind_speed(float wind_speed) {
            this.wind_speed = wind_speed;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }
    }

}