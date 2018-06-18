package org.mkonchady.solarmonitor;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.common.primitives.Ints;

import org.mkonchady.solarmonitor.SummaryProvider.Summary;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MonitorActivity extends Activity {

    // Table fields
    SummaryDB summaryTable = null;      // summary table DB handler
    int NUM_ROWS = 40;                  // max. number of rows to display
    ArrayList<TableRow> rows = new ArrayList<>();
    LayoutInflater inflater = null;
    Context context = null;
    TableLayout tableLayout = null;
    ArrayList<Integer> selectedlogs = new ArrayList<>();
    PlotData plotData;
    SharedPreferences sharedPreferences;
    int localLog = 0;
    boolean storagePermissionGranted = false;

    // GUI fields
    private TextView statusView;
    private EditText startDate, endDate;
    private DatePickerDialog fromDatePickerDialog;
    private DatePickerDialog toDatePickerDialog;

    // colors for the table rows
    int rowBackColor;
    int rowSelectColor;
    int rowHighlightColor;
    String TAG = "MonitorActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);
        context = this;
        summaryTable = new SummaryDB(SummaryProvider.db);
        tableLayout = findViewById(R.id.logtablelayout);
        inflater = getLayoutInflater();
        getDateViews();
        setDateDialogs();
        statusView = this.findViewById(R.id.statusLog);

        rowBackColor = ContextCompat.getColor(context, R.color.row_background);
        rowSelectColor = ContextCompat.getColor(context, R.color.row_selected_background);
        rowHighlightColor = ContextCompat.getColor(context, R.color.row_highlight_background);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "0"));

        // ask for storage permission in Android 6.0+
        // check if storahe Permission has been granted
        storagePermissionGranted = Constants.preMarshmallow;
        if (Constants.postMarshmallow && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
            storagePermissionGranted = true;

        if (!storagePermissionGranted) {
            Log.d(TAG, "Getting Storage Permission", localLog);
            Intent intent = new Intent(getBaseContext(), PermissionActivity.class);
            intent.putExtra("permission", Manifest.permission.WRITE_EXTERNAL_STORAGE);
            startActivityForResult(intent, Constants.PERMISSION_CODE);
        }

        // check if a starting date  and category was passed in the intent
        Intent intent = getIntent();
        Long fromDateTimestamp = intent.getLongExtra(Constants.LOG_FROM_DATE, 0L);
        if (fromDateTimestamp != 0) {
            startDate.setText(UtilsDate.getDate(fromDateTimestamp, Constants.LARGE_INT, Constants.LARGE_INT));
            long endTimestamp = fromDateTimestamp + Constants.MILLISECONDS_PER_DAY;
            selectedlogs = summaryTable.getMonitorIds(context, fromDateTimestamp, endTimestamp);
        }

        NUM_ROWS = Integer.parseInt(sharedPreferences.getString(Constants.NUMBER_LOG_ROWS, "40"));
        plotData = new PlotData(context);
        setActionBar();
        fetch_rows(false);  // sort by date
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // get the start date and end date views
    private void getDateViews() {
        startDate = findViewById(R.id.start_date);
        startDate.setInputType(InputType.TYPE_NULL);
        startDate.requestFocus();
        startDate.setKeyListener(null);
        endDate = findViewById(R.id.end_date);
        endDate.setInputType(InputType.TYPE_NULL);
        endDate.setKeyListener(null);
    }

    // build the date dialogs
    private void setDateDialogs() {
        Calendar calendar = Calendar.getInstance();
        fromDatePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker view, int year, int month, int day) {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, day);
                        startDate.setText(UtilsDate.getDate(newDate.getTimeInMillis(), Constants.LARGE_INT, Constants.LARGE_INT));
                        fetch_rows(false);
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        startDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fromDatePickerDialog.show();
            }
        });

        toDatePickerDialog = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker view, int year, int month, int day) {
                        Calendar newDate = Calendar.getInstance();
                        newDate.set(year, month, day);
                        endDate.setText(UtilsDate.getDate(newDate.getTimeInMillis(), Constants.LARGE_INT, Constants.LARGE_INT));
                        fetch_rows(false);
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        endDate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toDatePickerDialog.show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_activity_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_watthours:
                plot_watthours(); break;
            case R.id.action_wattsnow:
                plot_wattsnow(); break;
            case R.id.action_temperature:
                plot_temperature(); break;
            case R.id.action_wind:
                plot_wind(); break;
            case R.id.action_clouds:
                plot_clouds(); break;
            case R.id.action_analyze:
                analyze_log(); break;
            case R.id.action_peakwatts:
                plot_peakwatts(); break;
            case R.id.action_peaktime:
                plot_peaktime(); break;
            case R.id.action_sunlight:
                plot_sunlight(); break;
            case R.id.action_readings:
                plot_readings(); break;
            case R.id.action_monthly_temperature:
                plot_monthly_temperature(); break;
            case R.id.action_monthly_clouds:
                plot_monthly_clouds(); break;
            case R.id.action_delete:
                delete_rows(); break;
            case R.id.action_export:
                export_rows(); break;
            case R.id.action_import:
                import_rows(); break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    // delete the selected rows or the rows in the date range
    private void delete_rows() {
        ArrayList<Integer> ids = getSelectedIds();
        String plural = (ids.size() > 1) ? "s" : "";
        final Object[] params = {this};
        new AlertDialog.Builder(this)
                .setTitle("Delete selected logs")
                .setMessage("Are you sure you want to delete " + ids.size() +
                        " log" + plural + "?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        new DeleteRows().execute(params);  // delete rows
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // export one or more logs, a zip file maybe created for many exported logs
    private void export_rows() {
        ArrayList<Integer> ids = getSelectedIds();
        int size = ids.size();
        if (size > 1) {
            final Object[] params = {this};
            new AlertDialog.Builder(this)
                    .setTitle("Export selected logs")
                    .setMessage("Are you sure you want to export " + size + " logs" + "?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            new ExportRows().execute(params);  // export rows
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        } else {
            final Object[] params = {this};
            new ExportRows().execute(params);
        }

    }

    // import Log from a file
    private void import_rows() {
        final Object[] params = {this};
        new ImportRows().execute(params);
    }

    // call the watthours plot
    private void plot_watthours() {
        if (!log_number_error(1, 2)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int[] log_ids = new int[2];
            log_ids[0] = ids[0];
            log_ids[1] = (ids.length == 1)? ids[0]: ids[1];
            Bundle bundle = plotData.build_daily_plot(log_ids, "Generated Watts vs. Time", Constants.DATA_WATTHOURS);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the wattsnow plot
    private void plot_wattsnow() {
        if (!log_number_error(1, 2)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int[] log_ids = new int[2];
            log_ids[0] = ids[0];
            log_ids[1] = (ids.length == 1)? ids[0]: ids[1];
            Bundle bundle = plotData.build_daily_plot(log_ids, "Watts vs. Time", Constants.DATA_WATTSNOW);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the temperature plot
    private void plot_temperature() {
        if (!log_number_error(1, 2)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int[] log_ids = new int[2];
            log_ids[0] = ids[0];
            log_ids[1] = (ids.length == 1)? ids[0]: ids[1];
            Bundle bundle = plotData.build_daily_plot(log_ids, "Temperature (C°) vs. Time", Constants.DATA_TEMPERATURE);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the wind plot
    private void plot_wind() {
        if (!log_number_error(1, 2)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int[] log_ids = new int[2];
            log_ids[0] = ids[0];
            log_ids[1] = (ids.length == 1)? ids[0]: ids[1];
            Bundle bundle = plotData.build_daily_plot(log_ids, "Wind (m/s) vs. Time", Constants.DATA_WIND);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the cloud plot
    private void plot_clouds() {
        if (!log_number_error(1, 2)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int[] log_ids = new int[2];
            log_ids[0] = ids[0];
            log_ids[1] = (ids.length == 1)? ids[0]: ids[1];
            Bundle bundle = plotData.build_daily_plot(log_ids, "Cloud(%) vs. Time", Constants.DATA_CLOUDS);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    private void analyze_log() {
        if (!log_number_error(1, 1)) {
            int log_id = getSelectedIds().get(0);
            Intent intent = new Intent(MonitorActivity.this, LogAnalysisActivity.class);
            intent.putExtra(Constants.LOG_ID, log_id);
            startActivity(intent);
        }
    }

    // call the peak watts plot
    private void plot_peakwatts() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "Peak Watts & Generated Watts vs. Day",
                    "Peak Watts", "Generated Watts", "", Constants.DATA_PEAKWATTS);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the peak time plot
    private void plot_peaktime() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "Peak Time & Generated Watts vs. Day",
                    "Peak Time", "Generated Watts", "", Constants.DATA_PEAKTIME);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the sunlight plot
    private void plot_sunlight() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "Sunlight Time & Generated Watts vs. Day",
                    "Sunlight Time", "Generated Watts", "", Constants.DATA_SUNLIGHT);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the readings plot
    private void plot_readings() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "No. of Readings & Generated Watts vs. Day",
                    "No. of Readings", "Generated Watts", "", Constants.DATA_READINGS);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the monthly temp plot
    private void plot_monthly_temperature() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "Min/Max Temperature & Generated Watts vs. Day",
                    "Min. Temp.", "Max. Temp.", "Generated Watts",  Constants.DATA_MONTHLY_TEMPERATURE);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }

    // call the monthly cloud plot
    private void plot_monthly_clouds() {
        if (!log_number_error(1, 1)) {
            int[] ids = Ints.toArray(getSelectedIds());
            int log_id = ids[0];
            Bundle bundle = plotData.build_monthly_plot(log_id,  "Avg. Cloud % & Generated Watts vs. Day",
                    "Cloud %", "Generated Watts", "",  Constants.DATA_MONTHLY_CLOUDS);
            Intent intent = new Intent(MonitorActivity.this, PlotActivity.class);
            intent.putExtra(Constants.DATA_BUNDLE, bundle);
            startActivity(intent);
        }
    }


    // fetch the table rows from the DB in the background
    private void fetch_rows(boolean sortByID) {
        new FetchRows(buildParams(sortByID)).execute();
    }

    // verify that the number of logs selected is correct
    private boolean log_number_error(int min, int max) {
        int log_size = getHighlightedRows(true).size();
        if ( (min <= log_size) && (log_size <= max))
            return false;
        String dup = (min > 1)? "s": "";
        if (min > log_size) {
            String status = "Please select at least " + min + " log" + dup;
            statusView.setText(status);

        } else if (log_size > max) {
            String status = (max > min)? "Please select between " + min + " and " + max + " logs":
                                         "Please select " + min + " log" + dup;
            statusView.setText(status);
        }
        return true;
    }

    // build the date parameters for the select
    private Object[] buildParams(boolean sortByID) {
        // extract the dates
        String startText = startDate.getText().toString();
        String endText = endDate.getText().toString();
        // construct the SQL clauses
        String whereClause = setWhereClause(startText, endText);
        String sortOrder = setSortOrder(startText, endText, sortByID);
        return new Object[] {this, whereClause, sortOrder};
    }

    // Load the list of summaries asynchronously and then build the table rows
    private class FetchRows extends AsyncTask <Object, String, String> {
        ArrayList<Summary> summaries = null;
        Context context;
        String whereClause, sortOrder;
        long startTimestamp, endTimestamp;

        FetchRows(Object... params) {
            if (params.length < 3) return;
            context = (Context) params[0];
            whereClause = (String) params[1];
            sortOrder = (String) params[2];
            String startText = startDate.getText().toString();
            startTimestamp = (startText.length() > 0)? getTimestamp(startText): 0;
            String endText = endDate.getText().toString();
            endTimestamp = (endText.length() > 0)? getTimestamp(endText): Long.MAX_VALUE;
        }

        @Override
        protected String doInBackground(Object...params) {
           // Context context = (Context) params[0];
           // String whereClause = (String) params[1];
           // String sortOrder = (String) params[2];
            //summaries = summaryTable.getSummaries(context, whereClause, sortOrder, -1);

            ArrayList<SummaryProvider.Summary> all_summaries = summaryTable.getSummaries(context, whereClause, sortOrder, -1);
            summaries = new ArrayList<>();

            for (SummaryProvider.Summary summary : all_summaries) {
                TimeZone tz = TimeZone.getTimeZone(
                        TimezoneMapper.latLngToTimezoneString(0 / Constants.MILLION, 0 / Constants.MILLION));
                long correction = tz.getOffset(System.currentTimeMillis());
                long local_startTime = summary.getStart_time() + correction;
                long local_endTime = summary.getEnd_time() + correction;
                if ( (local_startTime >= startTimestamp) && (local_endTime <= endTimestamp) ){
                    summaries.add(summary);
                }
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            for (TableRow row : rows) tableLayout.removeView(row);
            rows = new ArrayList<>();
            buildTableRows(summaries);
            if (summaries.size() == 0) {
                String status = "Start monitoring by pressing the Start Button in the main window.";
                statusView.setText(status);
            }
            //Log.d(TAG, "selected logs : " + selectedlogs.size());
            selectlogs(selectedlogs);
        }
    }

    /* dump the summary and details files
      To locate the output file on Linux:
        cd ~/.android/avd/<avd name>.avd/sdcard.img
        sudo mount sdcard.img -o loop /mnt/sdcard
        cd /mnt/sdcard/Android/data/org.mkonchady.solarmonitor/files
    */
    private class ExportRows extends AsyncTask <Object, Integer, String> implements ProgressListener {
        int numSummaries = 0;
        @Override
        protected String doInBackground(Object...params) {
            String msg = "";
            if (!isExternalStorageWritable()) {
                msg = "Could not write to external storage";
                Log.e(TAG, msg, localLog);
                return msg;
            }
            // get the list of summaries,  // get all summaries if none were selected
            ArrayList<Summary> summaries = getSelectedSummaries();
            if (summaries.size() == 0)
                summaries = summaryTable.getSummaries(context, "", "", -1);
            numSummaries = summaries.size();
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String exportFormat = SP.getString(Constants.FILE_FORMAT, "csv");
            ExportFile exportFile = new ExportFile(context, summaries, this);
            switch (exportFormat) {
                case "csv": msg = exportFile.exportCSV(); break;
                case "xml": msg = exportFile.exportXML(); break;
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String result) {
            String status = "Finished export ...";
            statusView.setText(status);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            int files = progress[0];
            String status = "Completed " + files + " of " + numSummaries;
            statusView.setText(status);
        }

        @Override
        protected void onPreExecute() {
            String status = "Started export ...";
            statusView.setText(status);
        }
    }

    // Import a Log from a file
    private class ImportRows extends AsyncTask <Object, Integer, String> implements  ProgressListener {
        String filename = "";
        FileDialog fileDialog = null;
        int log_id = 0;
        int[] logIds = null;

        @Override
        protected String doInBackground(Object...params) {
            while (fileDialog.isShowing()) {                  // wait for file selection
                try { Thread.sleep(1000); }
                catch (InterruptedException ie) {
                    Log.e(TAG, "Interrupted sleep " + ie.getMessage(), localLog);
                }
            }
            if (filename.length() == 0) return "";      // no file was selected
            publishProgress(5);
            String suffix = UtilsFile.getFileSuffix(filename);
            ImportFile importFile = new ImportFile(context, this, filename);
            switch (suffix) {
                case "csv": log_id = importFile.importCSV(); break;
                case "xml": log_id = importFile.importXML(); break;
                case "zip": logIds = importFile.importZIP(); break;
            }
            return "Started importing file";
        }

        @Override
        protected void onPreExecute() {
            File mPath = new File(Environment.getExternalStorageDirectory() + "//DIR//");
            String[] suffixes = {"zip", "xml", "csv"};
            fileDialog = new FileDialog((Activity) context, mPath, suffixes);
            fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
                public void fileSelected(File file) {
                  filename = file.toString();
                  fileDialog.setShowing(false);
                }
            });
            fileDialog.showDialog();
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Importing file " + progress[0] + "%";
            statusView.setText(status);
        }

        @Override
        protected void onPostExecute(String result) {
            String status = "Finished import ...";
            statusView.setText(status);
            fetch_rows(true);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

    }

    // Delete the selected rows
    private class DeleteRows extends AsyncTask <Object, Integer, String>  implements  ProgressListener {
        @Override
        protected String doInBackground(Object...params) {
            // get the list of ids and delete the summary and associated details
            String msg = "";
            int deletedlogs = 0;
            int num_logs_to_delete = getSelectedIds().size();
            for (Integer id: getSelectedIds()) {
                deletedlogs += summaryTable.delSummary(context, id, true); // also delete details
                if (deletedlogs > 0) {
                    String plural = (deletedlogs > 1) ? "s" : "";
                    msg = deletedlogs + " log" + plural + " deleted";
                    int percent = (int) (100.0 * (deletedlogs * 1.0 / num_logs_to_delete));
                    publishProgress(percent);
                }
                //deletedlogs = 0;
            }
            return msg;
        }

        @Override
        protected void onPostExecute(String result) {
            selectedlogs = new ArrayList<>();
            fetch_rows(false);
            statusView.setText(result);
        }

        @Override
        public void reportProgress(int i) {
            publishProgress(i);
        }

        @Override
        protected void onProgressUpdate(Integer...progress) {
            String status = "Completed delete logs " + progress[0] + "%";
            statusView.setText(status);
        }

    }

    // return an array list of Summaries objects
    private ArrayList<Summary> getSelectedSummaries() {
        return getSelectedRows(false);
    }

    // return an array list of Summary ids
    private ArrayList<Integer> getSelectedIds() {
        return getSelectedRows(true);
    }

    /* Extract the ids of the selected rows
        1. First check if any rows were highlighted
        2. If not, use the date range to extract the corresponding rows.
        3. Return either a list of summaries or ids
    */
    @SuppressWarnings("unchecked")
    private <T> ArrayList<T> getSelectedRows(boolean getIDs) {
        // first get the highlighted rows
        ArrayList<T> selectedRows = getHighlightedRows(getIDs);
        if (!selectedRows.isEmpty())
            return selectedRows;

        // if none, get the list of summaries using the start and end dates
        final Object[] params = buildParams(false);
        ArrayList<Summary> summaries = summaryTable.getSummaries((Context) params[0], (String) params[1], (String) params[2], -1);
            for (Summary summary : summaries) {
                // skip a summary that is running
                if (summary.getStatus().equals(Constants.MONITOR_RUNNING)) continue;
                if (getIDs)
                    //selectedRows.add((T) new Integer(summary.getlog_id()));
                    selectedRows.add((T) Integer.valueOf(summary.getMonitor_id()));
                else
                    selectedRows.add((T) summary);
            }
        return selectedRows;
    }

    // get the ids or summaries of rows that have been selected
    @SuppressWarnings("unchecked")
    private <T> ArrayList<T> getHighlightedRows(boolean getIDs) {
        ArrayList<T> selectedRows = new ArrayList<>();
        // first check if any rows have been clicked on
        for (TableRow row: rows) {
            Drawable background = row.getBackground();
            if (background instanceof ColorDrawable) {
                int backColor = ((ColorDrawable) background).getColor();
                if (backColor == rowHighlightColor) continue;
                if (backColor == rowSelectColor) {
                    TextView tv = (TextView) row.getChildAt(0);  // get the Log id
                    Integer id = Integer.valueOf(tv.getText().toString());
                    //Log.d(TAG, "Selected " + id, localLog);
                    if (getIDs)
                        selectedRows.add((T) id);
                    else
                        selectedRows.add((T) summaryTable.getSummaries(this, "", "", id).get(0));
                }
            }
        }
        return selectedRows;
    }

    // build the list of table rows
    private void buildTableRows(ArrayList<Summary> summaries) {
        int row_count = 0;
        for (Summary summary : summaries) {
            addTableRow(getSummaryColumns(summary));
            if (++row_count >= NUM_ROWS) break; // limit the number of rows
        }
    }

    // build the string values of summary data
    private String[] getSummaryColumns(Summary summary) {
        String log_id = String.format(Locale.getDefault(), "%03d", summary.getMonitor_id());
        long start = summary.getStart_time();
        long end = summary.getEnd_time();
        if (summary.getStatus().equals(Constants.MONITOR_RUNNING))
            end = System.currentTimeMillis();
        int lat = Integer.parseInt(sharedPreferences.getString(Constants.LATITUDE_INT, "0"));
        int lon = Integer.parseInt(sharedPreferences.getString(Constants.LONGITUDE_INT, "0"));
        String dateString = UtilsDate.getDate(start, lat, lon);
        String duration = UtilsDate.getTimeDurationHHMMSS(end - start, false);;

        String watts = summary.getGenerated_watts() + "";
        return new String[] {log_id, dateString, duration, watts};
    }

    // create a new row using the passed column data
    private void addTableRow(String[] cols){
        final TableRow tr = (TableRow) inflater.inflate(R.layout.table_log_row, tableLayout, false);
        tr.setClickable(true);
        final TextView summaryID = tr.findViewById(R.id.summaryID);
        final TextView summaryDate = tr.findViewById(R.id.summaryDate);
        final TextView summaryDuration = tr.findViewById(R.id.summaryDuration);
        final TextView summaryWatts = tr.findViewById(R.id.summaryWatts);

        // set the background color to indicate if the row was selected
        tr.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Drawable background = summaryID.getBackground();
                //int backColor = getResources().getColor(R.color.row_background);
                int backColor = ContextCompat.getColor(context, R.color.row_background);
                if ( (background instanceof ColorDrawable) ) {
                    int currentBackColor = ((ColorDrawable) background).getColor();
                    if (currentBackColor == rowSelectColor)
                        backColor = rowBackColor;
                    else if (currentBackColor == rowHighlightColor)
                        backColor = rowHighlightColor;
                    else
                        backColor = rowSelectColor;
                }
                summaryID.setBackgroundColor(backColor);
                summaryDate.setBackgroundColor(backColor);
                summaryDuration.setBackgroundColor(backColor);
                summaryWatts.setBackgroundColor(backColor);
                tr.setBackgroundColor(backColor);
            }
        });

        // check the status of the row and set the back color
        int backColor = rowBackColor;

        // set the background color and text of the table row
        summaryID.setText(cols[0]);         summaryID.setBackgroundColor(backColor);
        summaryDate.setText(cols[1]);       summaryDate.setBackgroundColor(backColor);
        summaryDuration.setText(cols[2]);   summaryDuration.setBackgroundColor(backColor);
        summaryWatts.setText(cols[3]);      summaryWatts.setBackgroundColor(backColor);
        rows.add(tr);                       // save the collection of rows
        tableLayout.addView(tr);
    }

    // select particular rows
    private void selectlogs(ArrayList<Integer> logs) {
        if (logs == null || logs.size() == 0) return;
        for (TableRow row: rows) {
            TextView tv = (TextView) row.getChildAt(0);  // get the Log id
            Integer id = Integer.valueOf(tv.getText().toString());
            if (logs.contains(id)) {
                //Log.d(TAG, " Selected row " + id, localLog);
                final TextView summaryID = row.findViewById(R.id.summaryID);
                final TextView summaryDate = row.findViewById(R.id.summaryDate);
                final TextView summaryDuration = row.findViewById(R.id.summaryDuration);
                final TextView summaryWatts = row.findViewById(R.id.summaryWatts);
                String[] cols = getSummaryColumns(summaryTable.getSummary(context, id));

                // set the background color and text of the table row
                summaryID.setText(cols[0]);         summaryID.setBackgroundColor(rowSelectColor);
                summaryDate.setText(cols[1]);       summaryDate.setBackgroundColor(rowSelectColor);
                summaryDuration.setText(cols[2]);   summaryDuration.setBackgroundColor(rowSelectColor);
                summaryWatts.setText(cols[3]);      summaryWatts.setBackgroundColor(rowSelectColor);
                row.setBackgroundColor(rowSelectColor);
            }
        }
    }

    // return the where clause, handling all cases of startLocationFind and end dates
    // extend the start and end timestamp by a day to cover all possible timezones
    private String setWhereClause(String startText, String endText) {

        ArrayList<String> clauses = new ArrayList<>();

        // Case 1: Both startLocationFind and end are non-blank
        if (!startText.isEmpty() && !endText.isEmpty() ) {
            if (UtilsDate.isDate(startText)) {
                //long startTimestamp = Constants.parseDate(startText).getTime();
                long startTimestamp = getTimestamp(startText) - Constants.MILLISECONDS_PER_DAY;
                long endTimestamp = (UtilsDate.isDate(endText)) ?
                        getTimestamp(endText) + Constants.MILLISECONDS_PER_DAY * 2 :
                        Long.MAX_VALUE;
                if (endTimestamp > startTimestamp) {
                    clauses.add(SummaryProvider.START_TIME + " >= " + startTimestamp);
                    clauses.add(SummaryProvider.END_TIME + " <= " + endTimestamp);
                } else
                    clauses.add(" 1 == 0 ");
            }
            return join(" and ", clauses);
        }

        // Case 2: Start is non-blank
        if (!startText.isEmpty()) {
            if (UtilsDate.isDate(startText)) {
                //long timeStamp = Constants.parseDate(startText).getTime();
                long startTimestamp = getTimestamp(startText) - Constants.MILLISECONDS_PER_DAY;
                clauses.add(SummaryProvider.START_TIME + " >= " + startTimestamp);
            }
            return  join(" and ", clauses);
        }

        // Case 3: End is non-blank
        if (!endText.isEmpty()) {
            if (UtilsDate.isDate(endText)) {
                long endTimestamp = getTimestamp(endText) + Constants.MILLISECONDS_PER_DAY * 2;
                clauses.add(SummaryProvider.END_TIME + " <= " + endTimestamp);
            }
            return  join(" and ", clauses);
        }

        // Case 4: All blank
        return join(" and ", clauses);
    }

    private String join(String operator, ArrayList<String> clauses) {
        StringBuilder whereClause = new StringBuilder();
        for (int i = 0; i < clauses.size(); i++) {
            if (i+1 == clauses.size()) whereClause.append(clauses.get(i));
            else {
                whereClause.append(clauses.get(i));
                whereClause.append(operator);
            }

        }
        return whereClause.toString();
    }

    // return the sort order, handling all cases of startLocationFind and end dates
    private String setSortOrder(String startText, String endText, boolean sortByID) {
        String sortClause = "";

        if (sortByID) return SummaryProvider.MONITOR_ID + " desc";

        // Case 1: Both startLocationFind and end are non-blank
        if ( !startText.isEmpty() && !endText.isEmpty() ) {
            if (UtilsDate.isDate(startText) && UtilsDate.isDate(endText)) {
                sortClause = SummaryProvider.START_TIME + " asc";
            }
            return sortClause;
        }

        // Case 2: Start is non-blank
        if (!startText.isEmpty()) {
            if (UtilsDate.isDate(startText)) {
                sortClause = SummaryProvider.START_TIME + " asc";
            }
            return sortClause;
        }

        // Case 3: End is non-blank
        if (!endText.isEmpty()) {
            if (UtilsDate.isDate(endText)) {
                sortClause = SummaryProvider.END_TIME + " desc";
            }
            return sortClause;
        }

        // Case 4: All blank
        //return (sortByID)? SummaryProvider.log_ID + " desc": SummaryProvider.START_TIME + " desc";
        return SummaryProvider.START_TIME + " desc";
    }

    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

    // given a string timestamp, return the equivalent long value
    private long getTimestamp(String dateText) {
        Date date = UtilsDate.parseDate(dateText);
        if (date != null) return date.getTime();
        return 0L;
    }

    // sort logs by id
    public void idOrder(View v) {
        fetch_rows(true);
    }

    // sort logs by date
    public void dateOrder(View v) {
        fetch_rows(false);
    }

    // display the action bar
    private void setActionBar() {
        ActionBar actionBar = getActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setLogo(R.drawable.icon);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(getResources().getString(R.string.log_list));
        }
    }

    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        statusView.setText("");
    }

    private void sleep(long period) {
        try {
            Thread.sleep(period);
        } catch (InterruptedException ie) {
            //Log.d(TAG, "Could not sleep for " + period + " " + ie.getMessage(), localLog);
        }
    }


}