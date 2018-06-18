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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

/*------------------------------------------------------------
  Content provider for Summary
 -------------------------------------------------------------*/
public class SummaryProvider extends ContentProvider {

    // match with the authority in manifest
    static final String PROVIDER_NAME = "org.mkonchady.solarmonitor.SummaryProvider";
    static final String DATABASE_NAME = "summary.db";

    // summary table
    static final String SUMMARY_TABLE = "summary";
    static final String SUMMARY_ROW = "content://" + PROVIDER_NAME + "/" + SUMMARY_TABLE;
    static final String CREATE_SUMMARY =
            " CREATE TABLE " + SUMMARY_TABLE +
                    " (monitor_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " name TEXT NOT NULL, " +
                    " start_time INTEGER NOT NULL, " +
                    " end_time INTEGER NOT NULL, " +
                    " peak_watts INTEGER NOT NULL, " +
                    " peak_time INTEGER NOT NULL, " +
                    " generated_watts INTEGER NOT NULL, " +
                    " status text NOT NULL, " +
                    " extras text)";
    static final int SUMMARIES = 1;
    static final int SUMMARY = 2;

    // summary table columns
    static final String MONITOR_ID = "monitor_id";
    static final String NAME = "name";
    static final String START_TIME = "start_time";
    static final String END_TIME = "end_time";
    static final String PEAK_WATTS = "peak_watts";
    static final String PEAK_TIME = "peak_time";
    static final String GENERATED_WATTS = "generated_watts";
    static final String STATUS = "status";
    static final String EXTRAS = "extras";
    static final int NUM_FIELDS = 9;
    static final int DATABASE_VERSION = 1;
    static final String TAG = "SummaryProvider";

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(PROVIDER_NAME, SUMMARY_TABLE + "/#", SUMMARY);
        uriMatcher.addURI(PROVIDER_NAME, SUMMARY_TABLE, SUMMARIES);
    }
    public static SQLiteDatabase db;

    // Database helper class
    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_SUMMARY);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + SUMMARY_TABLE);
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

            case SUMMARIES:
                row = db.insert(SUMMARY_TABLE, "", values);
                if (row >= 0) {
                    _uri = ContentUris.withAppendedId(Uri.parse(SUMMARY_ROW), row);
                    if (getContext() != null)
                        getContext().getContentResolver().notifyChange(_uri, null);
                }
                break;
            default:
                break;
        }
        if (_uri != null)
            return _uri;
        throw new SQLException("Did not add row in Summary table " + uri);
    }

    @Override
    public Cursor query(@NonNull  Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        HashMap<String, String> SUMMARY_PROJECTION_MAP = new HashMap<>();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (uriMatcher.match(uri)) {
            case SUMMARY:
                qb.setTables(SUMMARY_TABLE);
                qb.appendWhere(MONITOR_ID + "=" + uri.getPathSegments().get(1));
                break;
            case SUMMARIES:
                qb.setTables(SUMMARY_TABLE);
                qb.setProjectionMap(SUMMARY_PROJECTION_MAP);
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
        String monitor_id;
        switch (uriMatcher.match(uri)){
            case SUMMARY:
                monitor_id = uri.getPathSegments().get(1);
                count = db.delete(SUMMARY_TABLE, MONITOR_ID +  " = " + monitor_id +
                        (!TextUtils.isEmpty(selection) ? " AND (" +
                                selection + ')' : ""), selectionArgs);
                break;
            case SUMMARIES:
                  count = db.delete(SUMMARY_TABLE, selection, selectionArgs);
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
            case SUMMARIES:
                count = db.update(SUMMARY_TABLE, values,
                        selection, selectionArgs);
                break;
            case SUMMARY:
                count = db.update(SUMMARY_TABLE, values, MONITOR_ID +
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
            case SUMMARIES:
                return "vnd.android.cursor.dir/vnd.example.summaries";
            case SUMMARY:
                return "vnd.android.cursor.item/vnd.example.summary";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    public static Summary createSummary() {
        return createSummary(Constants.LARGE_INT);
    }

    public static Summary createSummary(long max_time) {
        return createSummary(max_time, false);
    }

    // create a summary with default values
    public static Summary createSummary(long max_time, boolean local) {
        long start_time = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put(SummaryProvider.MONITOR_ID, 0);
        values.put(SummaryProvider.NAME, "");
        values.put(SummaryProvider.START_TIME, start_time);
        values.put(SummaryProvider.END_TIME, start_time + max_time);
        values.put(SummaryProvider.PEAK_WATTS, 0);
        values.put(SummaryProvider.PEAK_TIME, 0);
        values.put(SummaryProvider.GENERATED_WATTS, 0);
        values.put(SummaryProvider.STATUS, Constants.MONITOR_RUNNING);
        String extras = (local)? UtilsNetwork.buildInitialExtras(): "";       // imported files will have a blank extras
        values.put(SummaryProvider.EXTRAS, extras); // field
        return (new SummaryProvider.Summary(values));
    }

    // create a summary from an ordered list of values
    public static Summary createSummary(String fields[]) {
        for (int i = 0; i < fields.length; i++)
            fields[i] = fields[i].trim();
        return (new SummaryProvider.Summary(0, fields[1],
                Long.parseLong(fields[2]), Long.parseLong(fields[3]),
                Integer.parseInt(fields[6]), Integer.parseInt(fields[7]),
                Integer.parseInt(fields[8]), fields[9], fields[10]));
    }

    // Class for Summary
    public static class Summary {

        private int monitor_id;
        private String name;
        private long start_time;
        private long end_time;
        private int peak_watts;
        private long peak_time;
        private int generated_watts;
        private String status;
        private String extras;

        Summary(int monitor_id, String name, long start_time, long end_time,
                int peak_watts, long peak_time, int generated_watts,
                String status, String extras) {
            this.monitor_id = monitor_id;
            this.name = name.trim();
            this.start_time = start_time;
            this.end_time = end_time;
            this.peak_watts = peak_watts;
            this.peak_time = peak_time;
            this.generated_watts = generated_watts;
            this.status = status;
            this.extras = extras;
        }

        Summary (ContentValues contentValues) {
            this.monitor_id = contentValues.getAsInteger(MONITOR_ID);
            this.name = contentValues.getAsString(NAME);
            this.start_time = contentValues.getAsLong(START_TIME);
            this.end_time = contentValues.getAsLong(END_TIME);
            this.peak_watts = contentValues.getAsInteger(PEAK_WATTS);
            this.peak_time = contentValues.getAsInteger(PEAK_TIME);
            this.generated_watts = contentValues.getAsInteger(GENERATED_WATTS);
            this.status = contentValues.getAsString(STATUS);
            this.extras = contentValues.getAsString(EXTRAS);
        }

        public String toString(String format) {
            String[] fields = {monitor_id + "", name + "", start_time + "", end_time + "",
                    peak_watts + "", peak_time + "",  generated_watts + "",
                    status, extras};
            if (format.equalsIgnoreCase("csv")) {
                StringBuilder result = new StringBuilder();
                for(int i = 0; i < fields.length; i++) {
                    result.append(fields[i]);
                    if (i < (fields.length - 1)) result.append(",");
                }
                return result.toString();
            }

            if (format.equalsIgnoreCase("xml")) {
                String newline = Constants.NEWLINE;
                return (
                       ("     <" + SummaryProvider.MONITOR_ID + ">"  + monitor_id + "</" + SummaryProvider.MONITOR_ID + ">"  + newline) +
                       ("     <" + SummaryProvider.NAME + ">" + name.trim() + "</" + SummaryProvider.NAME + ">" + newline) +
                       ("     <" + SummaryProvider.START_TIME + ">"  + start_time + "</" + SummaryProvider.START_TIME + ">" + newline) +
                       ("     <" + SummaryProvider.END_TIME + ">" + end_time + "</" + SummaryProvider.END_TIME + ">"  + newline) +
                       ("     <" + SummaryProvider.PEAK_WATTS + ">"  + peak_watts + "</" + SummaryProvider.PEAK_WATTS + ">"  + newline) +
                       ("     <" + SummaryProvider.PEAK_TIME + ">"  + peak_time + "</" + SummaryProvider.PEAK_TIME + ">"  + newline) +
                       ("     <" + SummaryProvider.GENERATED_WATTS + ">" + generated_watts + "</" + SummaryProvider.GENERATED_WATTS + ">" + newline) +
                       ("     <" + SummaryProvider.STATUS + ">" + status + "</" + SummaryProvider.STATUS + ">" + newline) +
                       ("     <" + SummaryProvider.EXTRAS + ">" + extras + " </" + SummaryProvider.EXTRAS + ">" + newline)
                );
            }
            return ("");
        }

        public int getMonitor_id() {
            return monitor_id;
        }
        public void setMonitor_id(int monitor_id) {
            this.monitor_id = monitor_id;
        }

        public String getName() { return name; }
        public void setName(String name) {
            this.name = name;
        }

        public long getStart_time() {
            return start_time;
        }
        public void setStart_time(long start_time) {
            this.start_time = start_time;
        }

        public long getEnd_time() {
            return end_time;
        }
        public void setEnd_time(long end_time) {
            this.end_time = end_time;
        }

        public int getGenerated_watts() {
            return generated_watts;
        }

        public void setGenerated_watts(int generated_watts) {
            this.generated_watts = generated_watts;
        }
        public int getPeak_watts() {
            return peak_watts;
        }

        public void setPeak_watts(int peak_watts) {
            this.peak_watts = peak_watts;
        }
        public long getPeak_time() {
            return peak_time;
        }

        public void setPeak_time(long peak_time) {
            this.peak_time = peak_time;
        }

        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        String getExtras() {
            return extras;
        }
        void setExtras(String extras) {
            this.extras = extras;
        }

        //*---- min. max avg temp
        float getMinTemperature() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("min_temp"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getMaxTemperature() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("max_temp"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getAvgTemperature() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("avg_temp"));
            } catch (JSONException je) {
                return 0;
            }
        }

        //*---- min. max avg wind
        float getMinWind() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("min_wind"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getMaxWind() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("max_wind"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getAvgWind() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("avg_wind"));
            } catch (JSONException je) {
                return 0;
            }
        }

        //*---- min. max avg clouds
        float getMinClouds() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("min_clouds"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getMaxClouds() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("max_clouds"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getAvgClouds() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("avg_clouds"));
            } catch (JSONException je) {
                return 0;
            }
        }

        //*---- min. max avg reading time
        float getMinReadingTime() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("min_reading_time"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getMaxReadingTime() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("max_reading_time"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getAvgReadingTime() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("avg_reading_time"));
            } catch (JSONException je) {
                return 0;
            }
        }

        //*---- min. max avg watts
        float getMinWatts() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("min_watts"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getMaxWatts() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("max_watts"));
            } catch (JSONException je) {
                return 0;
            }
        }

        float getAvgWatts() {
            String extras = getExtras();
            if (extras.length() == 0) return 0;
            try {
                JSONObject jsonObj = new JSONObject(extras);
                return Float.parseFloat(jsonObj.getString("avg_watts"));
            } catch (JSONException je) {
                return 0;
            }
        }

    }
}