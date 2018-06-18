package org.mkonchady.solarmonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import java.util.ArrayList;

/*
 A collection of utilities to manage the Detail table
 */

class DetailDB {

    SQLiteDatabase db;

    // set the database handler
    DetailDB(SQLiteDatabase db) {
        this.db = db;
    }

    // get a list of details for the entire log
    public ArrayList<DetailProvider.Detail> getDetails(Context context, int log_id) {
        return  getDetails(context, log_id, Constants.ALL_SEGMENTS);
    }

    // get a list of details for the log segment sorted in ascending order of timestamp
    public ArrayList<DetailProvider.Detail> getDetails(Context context, int log_id, int segment) {
        ArrayList<DetailProvider.Detail> details = new ArrayList<>();
        Uri logs = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = null;
        if (log_id > 0) {
            whereClause = DetailProvider.MONITOR_ID + " = " + log_id;
        }

        // get the details in ascending order of timestamp
        Cursor c = context.getContentResolver().query(logs, null, whereClause, null, DetailProvider.TIMESTAMP);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                DetailProvider.Detail detail = createDetailRecord(c);
                details.add(detail);
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return details;
    }

    public boolean addDetail(Context context, DetailProvider.Detail detail) {
        ContentValues values = getContentValues(detail);
        Uri uri = context.getContentResolver().insert(Uri.parse(DetailProvider.DETAIL_ROW), values);
        return (uri != null);
    }

    public DetailProvider.Detail cloneDetail(DetailProvider.Detail detail) {
        ContentValues values = getContentValues(detail);
        return (new DetailProvider.Detail(
                (int) (values.get(DetailProvider.MONITOR_ID)),
                (long) values.get(DetailProvider.TIMESTAMP),
                (int) values.get(DetailProvider.WATTS_GENERATED),
                (int) values.get(DetailProvider.WATTS_NOW),
                (String) values.get(DetailProvider.WEATHER),
                (float) values.get(DetailProvider.TEMPERATURE),
                (float) values.get(DetailProvider.CLOUDS),
                (float) values.get(DetailProvider.WIND_SPEED),
                (String) values.get(DetailProvider.INDEX)
        ));
    }

    // build the content values for the Detail
    private ContentValues getContentValues(DetailProvider.Detail detail) {
        ContentValues values = new ContentValues();
        values.put(DetailProvider.MONITOR_ID, detail.getMonitor_id());
        values.put(DetailProvider.TIMESTAMP, detail.getTimestamp());
        values.put(DetailProvider.WATTS_GENERATED, detail.getWatts_generated());
        values.put(DetailProvider.WATTS_NOW, detail.getWatts_now());
        values.put(DetailProvider.WEATHER, detail.getWeather());
        values.put(DetailProvider.TEMPERATURE, detail.getTemperature());
        values.put(DetailProvider.CLOUDS, detail.getClouds());
        values.put(DetailProvider.WIND_SPEED, detail.getWind_speed());
        values.put(DetailProvider.INDEX, detail.getIndex());
        return values;
    }

    // get the immediate neighbour details
    public DetailProvider.Detail[] getDetailNeighbours(Context context, int log_id, String detailIndex) {
       return getDetailNeighbours(context, log_id, detailIndex, 1);
    }

    // get a detail INC before and after the given detail index
    public DetailProvider.Detail[] getDetailNeighbours(Context context, int log_id, String detailIndex, int INC) {
        DetailProvider.Detail[] closeDetails = new DetailProvider.Detail[2];
        int index = Integer.valueOf(detailIndex);
        DetailProvider.Detail detail = getDetail(context, log_id, index - INC);
        if (detail != null) closeDetails[0] = detail;
        detail = getDetail(context, log_id, index + INC);
        if (detail != null) closeDetails[1] = detail;
        return closeDetails;
    }

    // get closest detail based on the time stamp
    public DetailProvider.Detail getClosestDetailByTime(Context context, int log_id, long timestamp) {
        Uri logs = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.MONITOR_ID + " = " + log_id;
        String sortOrder = " abs(" + timestamp + " - " + DetailProvider.TIMESTAMP + ")";
        Cursor c = context.getContentResolver().query(logs, null, whereClause, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            DetailProvider.Detail detail = createDetailRecord(c);
            c.close();
            return detail;
        }
        return null;
    }

    // get a detail record by index
    public DetailProvider.Detail getDetail(Context context, int log_id, int detailIndex) {
        Uri logs = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.MONITOR_ID + " = " + log_id + " and " +
                DetailProvider.INDEX + " = \"" + detailIndex + "\"";
        DetailProvider.Detail detail = null;
        Cursor c = context.getContentResolver().query(logs, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) detail = createDetailRecord(c);
        if (c!= null) c.close();
        return detail;
    }

    // get a detail record by timestamp
    public DetailProvider.Detail getDetail(Context context, long timestamp, int log_id) {
        Uri logs = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.TIMESTAMP + " = " + timestamp + " and " +
                             " " + DetailProvider.MONITOR_ID   + " = " +  log_id;
        DetailProvider.Detail detail = null;
        Cursor c = context.getContentResolver().query(logs, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) detail = createDetailRecord(c);
        if (c!= null) c.close();
        return detail;
    }

    public DetailProvider.Detail getFirstDetail(Context context, int log_id, int segmentNumber) {
        Uri logs = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause = " " + DetailProvider.MONITOR_ID + " = " + log_id;
        String sortOrder = DetailProvider.TIMESTAMP;
        Cursor c = context.getContentResolver().query(logs, null, whereClause, null, sortOrder);
        DetailProvider.Detail detail = null;
        if ((c != null) && (c.moveToFirst()))
            detail = createDetailRecord(c);
        if (c != null) c.close();
        return detail;
    }

    // create the detail record from the cursor
    private DetailProvider.Detail createDetailRecord(Cursor c) {
        return (new DetailProvider.Detail(
                c.getInt(c.getColumnIndex(DetailProvider.MONITOR_ID)),
                c.getLong(c.getColumnIndex(DetailProvider.TIMESTAMP)),
                c.getInt(c.getColumnIndex(DetailProvider.WATTS_GENERATED)),
                c.getInt(c.getColumnIndex(DetailProvider.WATTS_NOW)),
                c.getString(c.getColumnIndex(DetailProvider.WEATHER)),
                c.getFloat(c.getColumnIndex(DetailProvider.TEMPERATURE)),
                c.getFloat(c.getColumnIndex(DetailProvider.CLOUDS)),
                c.getFloat(c.getColumnIndex(DetailProvider.WIND_SPEED)),
                c.getString(c.getColumnIndex(DetailProvider.INDEX))
        ));
    }

    // delete a single detail row based on the timestamp
    public boolean deleteDetail(int log_id, long timestamp) {
        String execSQL = "delete from " + DetailProvider.DETAIL_TABLE +
                " where " + DetailProvider.TIMESTAMP + " =  " + timestamp + " and " +
                DetailProvider.MONITOR_ID + " = " + log_id;
        runSQL(execSQL);
        return true;
    }

    // delete all details for a summary
    public int deleteDetails(Context context, int log_id) {
        String URL = DetailProvider.DETAIL_ROW + "/" + log_id;
        Uri detail = Uri.parse(URL);
        return (context.getContentResolver().delete(detail, null, null));
    }

    // run a sql statement
    public void runSQL(String sql) {
        db.execSQL(sql);
    }
}