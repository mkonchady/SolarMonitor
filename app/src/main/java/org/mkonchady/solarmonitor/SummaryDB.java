package org.mkonchady.solarmonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.mkonchady.solarmonitor.SummaryProvider.Summary;

import java.util.ArrayList;

/*
 A collection of utilities to manage the Summary tables
 */
class SummaryDB {

    SQLiteDatabase db;

    // set the database handler
    SummaryDB(SQLiteDatabase db) {
        this.db = db;
    }

    // get a particular summary
    Summary getSummary(Context context, int monitor_id) {
        ArrayList<Summary> summaries = getSummaries(context, "", "", monitor_id);
        return summaries.get(0);
    }

    // get a list of summaries or a single summary
    public ArrayList<Summary> getSummaries(Context context, String whereClause, String sortOrder, int monitor_id) {
        ArrayList<Summary> summaries = new ArrayList<>();
        Uri days = Uri.parse(SummaryProvider.SUMMARY_ROW);
        if (sortOrder == null || sortOrder.length() == 0)
            sortOrder = SummaryProvider.MONITOR_ID + " desc";
        Cursor c = (monitor_id == -1)? context.getContentResolver().query(days, null, whereClause, null, sortOrder):
                                    context.getContentResolver().query(days, null, "monitor_id = " + monitor_id, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                Summary summary = new Summary(
                        c.getInt(c.getColumnIndex(SummaryProvider.MONITOR_ID)),
                        c.getString(c.getColumnIndex(SummaryProvider.NAME)),
                        c.getLong(c.getColumnIndex(SummaryProvider.START_TIME)),
                        c.getLong(c.getColumnIndex(SummaryProvider.END_TIME)),
                        c.getInt(c.getColumnIndex(SummaryProvider.PEAK_WATTS)),
                        c.getLong(c.getColumnIndex(SummaryProvider.PEAK_TIME)),
                        c.getInt(c.getColumnIndex(SummaryProvider.GENERATED_WATTS)),
                        c.getString(c.getColumnIndex(SummaryProvider.STATUS)),
                        c.getString(c.getColumnIndex(SummaryProvider.EXTRAS))
                        );
                summaries.add(summary);
            } while (c.moveToNext());
            c.close();
        }
        return summaries;
    }

    // get a list of summary ids
    public ArrayList<Integer> getSummaryIDs(Context context) {
        ArrayList<Integer> summaryIDs = new ArrayList<>();
        Uri days = Uri.parse(SummaryProvider.SUMMARY_ROW);
        Cursor c = context.getContentResolver().query(days, null, null, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                summaryIDs.add(c.getInt(c.getColumnIndex(SummaryProvider.MONITOR_ID)));
            } while (c.moveToNext());
            c.close();
        }
        return summaryIDs;
    }

    // add a new summary
    public int addSummary(Context context, Summary summary) {
        Uri uri;
        ContentValues values = putSummaryValues(summary);
        uri = context.getContentResolver().insert(Uri.parse(SummaryProvider.SUMMARY_ROW), values);
        int id = -1;
        if (uri == null) return id;
        // get the ID of the last entered row
        switch (SummaryProvider.uriMatcher.match(uri)) {
            case SummaryProvider.SUMMARY:
                id = Integer.parseInt(uri.getPathSegments().get(1));
                break;
            default:
                break;
        }
        return id;
    }

    public ContentValues putSummaryValues(Summary summary) {
        ContentValues values = new ContentValues();
       // values.put(SummaryProvider.MONITOR_ID, 0 );
        values.put(SummaryProvider.START_TIME, summary.getStart_time());
        values.put(SummaryProvider.NAME, summary.getName());
        values.put(SummaryProvider.START_TIME, summary.getStart_time());
        values.put(SummaryProvider.END_TIME, summary.getEnd_time());
        values.put(SummaryProvider.PEAK_WATTS, summary.getPeak_watts());
        values.put(SummaryProvider.PEAK_TIME, summary.getPeak_time());
        values.put(SummaryProvider.GENERATED_WATTS, summary.getGenerated_watts());
        values.put(SummaryProvider.STATUS, summary.getStatus());
        values.put(SummaryProvider.EXTRAS, summary.getExtras());
        return values;
    }

    // get a list of monitor ids in the timestamp range
    public ArrayList<Integer> getMonitorIds(Context context, long startTimestamp, long endTimestamp) {
        ArrayList<Integer> monitorIds = new ArrayList<>();
        String whereClause = SummaryProvider.START_TIME + " >= " + startTimestamp + " and " +
                             SummaryProvider.END_TIME + " <= " + endTimestamp;
        ArrayList<Summary> summaries = getSummaries(context, whereClause, "", -1);
        for (Summary summary: summaries) {
            monitorIds.add(summary.getMonitor_id());
        }
        return monitorIds;
    }

    // update the status of a summary
    public void setSummaryStatus(Context context, String status, int monitor_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.STATUS + " = \"" + status +
                "\"  where " + SummaryProvider.MONITOR_ID + " =  " + monitor_id;

        runSQL(execSQL);
    }

    // update the start timestamp of a summary
    //public boolean updateSummaryStartTimestamp(Context context, long timestamp, int monitor_id) {
    //    String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
    //            SummaryProvider.START_TIME + " = " + timestamp +
    //            " where " + SummaryProvider.monitor_id + " =  " + monitor_id;
    //    runSQL(execSQL);
    //    return true;
    //}

    // update the end timestamp of a summary
    public boolean setSummaryEndTimestamp(Context context, long timestamp, int monitor_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.END_TIME + " = " + timestamp +
                " where " + SummaryProvider.MONITOR_ID + " =  " + monitor_id;
        runSQL(execSQL);
        return true;
    }


    // return a JSON string containing the min. and max. temp
    String appendSummaryExtras(String key, String val, String extras) {
        try {
            JSONObject jsonObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            jsonObject.put(key, val);
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Log.e("JSON", "JSON formatting error in encoding Summary extras "  + je.getMessage());
        }
        return extras;
    }

    // return a JSON string containing the min. and max. temp
    String appendSummaryExtras(float min_val, float max_val, float avg_val, String suffix, String extras) {
        try {
            JSONObject jsonObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            jsonObject.put("min_" + suffix, UtilsMisc.formatFloat(min_val, 1));
            jsonObject.put("max_" + suffix, UtilsMisc.formatFloat(max_val, 1));
            jsonObject.put("avg_" + suffix, UtilsMisc.formatFloat(avg_val, 1));
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Log.e("JSON", "JSON formatting error in encoding Summary extras "  + je.getMessage());
        }
        return extras;
    }

    // return a JSON string containing the min. and max. temp
    String appendSummaryExtras(long min_val, long max_val, float avg_val, String suffix, String extras) {
        try {
            JSONObject jsonObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            jsonObject.put("min_" + suffix, min_val);
            jsonObject.put("max_" + suffix, max_val);
            jsonObject.put("avg_" + suffix, UtilsMisc.formatFloat(avg_val, 1));
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Log.e("JSON", "JSON formatting error in encoding Summary extras "  + je.getMessage());
        }
        return extras;
    }

    // return a JSON string containing the min. and max. temp
    String appendSummaryExtras(int min_val, int max_val, float avg_val, String suffix, String extras) {
        try {
            JSONObject jsonObject = (extras.length() > 0) ? new JSONObject(extras): new JSONObject();
            jsonObject.put("min_" + suffix, min_val);
            jsonObject.put("max_" + suffix, max_val);
            jsonObject.put("avg_" + suffix, UtilsMisc.formatFloat(avg_val, 1));
            extras = jsonObject.toString();
        }catch (JSONException je) {
            Log.e("JSON", "JSON formatting error in encoding Summary extras "  + je.getMessage());
        }
        return extras;
    }
    // update the extras of a summary
    private void setSummaryExtras(Context context, String extras, int monitor_id) {
        extras = UtilsMisc.escapeQuotes(extras);
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.EXTRAS + " = \"" + extras +
                "\" where " + SummaryProvider.MONITOR_ID + " =  " + monitor_id;
        runSQL(execSQL);
    }

    // update the parameters of a summary
    public void setSummaryParameters(Context context, ContentValues values, int monitor_id) {
        String execSQL = "update " + SummaryProvider.SUMMARY_TABLE + " set " +
                SummaryProvider.NAME + " = \"" + values.getAsString(SummaryProvider.NAME) + "\"," +
                SummaryProvider.START_TIME + " = " + values.getAsLong(SummaryProvider.START_TIME) + "," +
                SummaryProvider.END_TIME + " = " + values.getAsLong(SummaryProvider.END_TIME) + "," +
                SummaryProvider.PEAK_WATTS + " = " + values.getAsFloat(SummaryProvider.PEAK_WATTS) + "," +
                SummaryProvider.PEAK_TIME + " = " + values.getAsLong(SummaryProvider.PEAK_TIME) + "," +
                SummaryProvider.GENERATED_WATTS + " = " + values.getAsInteger(SummaryProvider.GENERATED_WATTS) + "," +
                SummaryProvider.STATUS            + " = \"" + values.getAsString(SummaryProvider.STATUS)         + "\"," +
                SummaryProvider.EXTRAS            + " = \"" + UtilsMisc.escapeQuotes(values.getAsString(SummaryProvider.EXTRAS)) + "\"" +
                " where " + SummaryProvider.MONITOR_ID + " =  " + monitor_id;
        runSQL(execSQL);
    }

    // get the peak watts of the summary
    public int getPeakWatts(Context context , int monitor_id) {
        int distance = -1;
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.MONITOR_ID + " = " + monitor_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(summaries, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            distance = Math.round(c.getInt(c.getColumnIndex(SummaryProvider.PEAK_WATTS)));
            c.close();
        }
        return distance;
    }

    // get the extras of the summary
    String getExtras(Context context , int monitor_id) {
        String extras = "";
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.MONITOR_ID + " = " + monitor_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(summaries, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            extras = c.getString(c.getColumnIndex(SummaryProvider.EXTRAS));
            c.close();
        }
        return extras;
    }

    // get the id of the most recent running summary
    public int getRunningSummaryID(Context context) {
        int id = -1;
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String sortOrder = SummaryProvider.MONITOR_ID + " desc ";
        Cursor c = context.getContentResolver().query(summaries, null, null, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                String status = c.getString(c.getColumnIndex(SummaryProvider.STATUS));
                if (status.equals(Constants.MONITOR_RUNNING))
                    id = c.getInt(c.getColumnIndex(SummaryProvider.MONITOR_ID));
            } while (c.moveToNext());
            c.close();
        }
        return id;
    }

    // get the monitor id for the file date
    public int getMonitorID(Context context, String filedate) {
        int monitor_id = -1;
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.NAME + " = \"" + filedate + "\"";
        Cursor c = context.getContentResolver().query(summaries, null, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            monitor_id = c.getInt(0);
            c.close();
        }
        return monitor_id;
    }

    // has the summary been modified
    public boolean isEdited(Context context, int monitor_id) {
        boolean edited = false;
        try {
            String extras = getExtras(context, monitor_id);
            if (extras.length() > 0) {
                JSONObject jsonObject = new JSONObject(extras);
                edited = Boolean.valueOf(jsonObject.getString("edited"));
            }
        } catch (JSONException je) {
            Log.d("JSON", "Could not parse edited from Summary extras " + je.getMessage());
        }
        return edited;
    }

    // does a summary row exist with the given date
    public boolean isInSummaryTable(Context context, String file_date) {
        boolean inTable = false;
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String sortOrder = SummaryProvider.MONITOR_ID + " desc ";
        Cursor c = context.getContentResolver().query(summaries, null, null, null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            do {
                String tableDate = c.getString(c.getColumnIndex(SummaryProvider.NAME));
                if (tableDate.equals(file_date))
                    inTable = true;
            } while (c.moveToNext());
            c.close();
        }
        return inTable;
    }

    // check if the summary was imported from an external source
    // extras field is blank for imported external files
    public boolean isImported(Context context, int monitor_id) {
        return (UtilsNetwork.isImported(getExtras(context, monitor_id)) );
    }

    // is summary finished
    public boolean isSummaryFinished(Context context, int monitor_id) {
        boolean finished = false;
        Uri summaries = Uri.parse(SummaryProvider.SUMMARY_ROW);
        String whereClause =  " " + SummaryProvider.MONITOR_ID + " = " + monitor_id;
        String sortOrder = "";
        Cursor c = context.getContentResolver().query(summaries, null, whereClause,
                null, sortOrder);
        if ( (c != null) && (c.moveToFirst()) ) {
            String status = c.getString(c.getColumnIndex(SummaryProvider.STATUS));
            c.close();
            if (status.equals(Constants.MONITOR_FINISHED))
                finished = true;
        }
        return finished;
    }


    // get the number of detail records for the summary
    public int getDetailCount(Context context, int monitor_id) {
        int readings = -1;
        Uri details = Uri.parse(DetailProvider.DETAIL_ROW);
        String whereClause =  " " + DetailProvider.MONITOR_ID + " = " + monitor_id;
        String[] projection = {"count(*) as count"};
        Cursor c = context.getContentResolver().query(details, projection, whereClause, null, null);
        if ( (c != null) && (c.moveToFirst()) ) {
            readings = c.getInt(0);
            c.close();
        }
        return readings;
    }

    // delete a summary and associated details
    public int delSummary(Context context, int monitor_id, boolean deleteDetails) {
        String URL = SummaryProvider.SUMMARY_ROW + "/" + monitor_id;
        Uri summary = Uri.parse(URL);
        if (deleteDetails) {
            DetailDB detailDB = new DetailDB(DetailProvider.db);
            detailDB.deleteDetails(context, monitor_id);
        }
        return (context.getContentResolver().delete(summary, null, null));
    }


    // delete a summary and associated details
    public int delSummary(Context context, String file_date, boolean deleteDetails) {
        int monitor_id = getMonitorID(context, file_date);
        if (monitor_id == -1) return 0;
        String URL = SummaryProvider.SUMMARY_ROW + "/" + monitor_id;
        Uri summary = Uri.parse(URL);
        if (deleteDetails) {
            DetailDB detailDB = new DetailDB(DetailProvider.db);
            detailDB.deleteDetails(context, monitor_id);
        }
        return (context.getContentResolver().delete(summary, null, null));
    }

    // run a sql statement
     void runSQL(String sql) {
        db.execSQL(sql);
    }

}