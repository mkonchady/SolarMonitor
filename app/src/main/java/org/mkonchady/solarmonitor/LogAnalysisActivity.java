package org.mkonchady.solarmonitor;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.mkonchady.solarmonitor.SummaryProvider.Summary;

import java.util.ArrayList;

// show a window with the analysis of the summary and details of the trip
public class LogAnalysisActivity extends Activity implements View.OnClickListener {

    // Table fields
    SummaryDB summaryTable = null;      // summary table DB handler
    ArrayList<TableRow> rows = new ArrayList<>();
    LayoutInflater inflater = null;
    Context context = null;
    int log_id;
    TableLayout tableLayout = null;

    // colors for the table rows
    int rowBackColor;
    int rowHighlightColor;

    SharedPreferences sharedPreferences;
    int lat, lon;
    String TAG = "LogAnalysisActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_analysis);
        context = this;
        log_id = getIntent().getIntExtra(Constants.LOG_ID, -1);
        summaryTable = new SummaryDB(SummaryProvider.db);
        tableLayout = findViewById(R.id.tableAnalysislayout);
        inflater = getLayoutInflater();
        rowBackColor = ContextCompat.getColor(context, R.color.row_background);
        rowHighlightColor = ContextCompat.getColor(context, R.color.row_highlight_background);
        setActionBar();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        lat = (int) (sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0.0f) * Constants.MILLION);
        lon = (int) (sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0.0f) * Constants.MILLION);
        new FetchSummary().execute(this, " where " + SummaryProvider.MONITOR_ID + " = " + log_id, null);
    }

    // Load the list of summaries asynchronously and then build the table rows
    private class FetchSummary extends AsyncTask<Object, Integer, String> {

        ArrayList<Summary> summaries = null;
        @Override
        protected String doInBackground(Object...params) {
            Context context = (Context) params[0];
            String whereClause = (String) params[1];
            String sortOrder = (String) params[2];
            summaries = summaryTable.getSummaries(context, whereClause, sortOrder,  log_id);
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            for (TableRow row : rows) tableLayout.removeView(row);
            rows = new ArrayList<>();
            buildTableRows(summaries.get(0));
        }
    }

    // build the list of table rows
    private void buildTableRows(Summary summary) {

        String sunrise = UtilsDate.getTimeDurationHHMMSS(summary.getStart_time(), true, lat, lon);
        String sunset = UtilsDate.getTimeDurationHHMMSS(summary.getEnd_time(), true, lat, lon);
        addRow("Sunrise ", sunrise, false, "Sunset ", sunset, false, true);

        String duration = UtilsDate.getTimeDurationHHMM(summary.getEnd_time() - summary.getStart_time());
        String numReadings = "" + summaryTable.getDetailCount(context, log_id);
        addRow("Readings ", numReadings, true, "Duration ", duration , true, true);

        String genWatts = summary.getGenerated_watts() + "";
        String peakTime = UtilsDate.getTimeDurationHHMMSS(summary.getPeak_time(), true, lat, lon);
        addRow("Generated(w) ", genWatts, false, "Peak Time ", peakTime, false, true);

        // min, max, average rows
        addTitleRow("Feature", "Minimum", "Maximum", "Average");

        String minTemp = "" + UtilsMisc.formatFloat(summary.getMinTemperature() - 273, 1);
        String maxTemp = "" + UtilsMisc.formatFloat(summary.getMaxTemperature() - 273, 1);
        String avgTemp = "" + UtilsMisc.formatFloat(summary.getAvgTemperature() - 273, 1);
        addRow("Temp.(°C)", minTemp, false, maxTemp, avgTemp, false, true);

        String minWind = "" + summary.getMinWind();
        String maxWind = "" + summary.getMaxWind();
        String avgWind = "" +  UtilsMisc.formatFloat(summary.getAvgWind(), 1);
        addRow("Wind(m/s)", minWind, true, maxWind, avgWind ,  true, true);

        String minClouds = "" + summary.getMinClouds();
        String maxClouds = "" + summary.getMaxClouds();
        String avgClouds = "" +  UtilsMisc.formatFloat(summary.getAvgClouds(), 1);
        addRow("Clouds(%)", minClouds, false, maxClouds, avgClouds,  false, true);

        String minTime = "" + UtilsDate.getTimeDurationHHMM((long) summary.getMinReadingTime() * 1000);
        String maxTime = "" + UtilsDate.getTimeDurationHHMM((long) summary.getMaxReadingTime() * 1000);
        String avgTime = "" + UtilsDate.getTimeDurationHHMM((long) summary.getAvgReadingTime() * 1000);
        addRow("Time/entry(s) ", minTime, true, maxTime, avgTime,  true, true);

        String minWatts = "" + UtilsMisc.formatFloat(summary.getMinWatts(), 1);
        String maxWatts = "" + UtilsMisc.formatFloat(summary.getMaxWatts(), 1);
        String avgWatts = "" + UtilsMisc.formatFloat(summary.getAvgWatts(), 1);
        addRow("Watts", minWatts, false, maxWatts, avgWatts ,  false, true);
    }

    private void addRow(String label1, String description1, boolean background1,
                        String label2, String description2, boolean background2, boolean drawLine) {
        final TableRow tr = (TableRow)inflater.inflate(R.layout.table_analysis_row, tableLayout, false);
        final TextView labelView1 = tr.findViewById(R.id.label1);
        final TextView descriptionView1 = tr.findViewById(R.id.description1);
        labelView1.setText(label1); descriptionView1.setText(description1);
        if (background1) {
            labelView1.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
            descriptionView1.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
        }

        final TextView labelView2 =  tr.findViewById(R.id.label2);
        final TextView descriptionView2 =  tr.findViewById(R.id.description2);
        labelView2.setText(label2); descriptionView2.setText(description2);
        if (background2) {
            labelView2.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
            descriptionView2.setBackgroundColor(ContextCompat.getColor(context, R.color.LightBlue));
        }

        final int MAXLEN = 10;
        if ( label1.length() > MAXLEN  || description1.length() > MAXLEN ||
             label2.length() > MAXLEN  || description2.length() > MAXLEN ) {
            labelView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descriptionView1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            labelView2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            descriptionView2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }

        rows.add(tr);                   // save the collection of rows
        tableLayout.addView(tr);        // add to the table

        if (drawLine) {
            View v = new View(this);
            v.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1));
            v.setBackgroundColor(ContextCompat.getColor(context, R.color.Black));
            tableLayout.addView(v);
        }
    }

    private void addTitleRow(String label1, String description1, String label2, String description2) {
        final TableRow tr = (TableRow)inflater.inflate(R.layout.table_analysis_row, tableLayout, false);

        //tr.setClickable(true);
        final TextView labelView1 =  tr.findViewById(R.id.label1);
        final TextView descriptionView1 =  tr.findViewById(R.id.description1);
        labelView1.setText(label1); descriptionView1.setText(description1);
        labelView1.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        descriptionView1.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        labelView1.setTextColor(ContextCompat.getColor(context, R.color.white));
        descriptionView1.setTextColor(ContextCompat.getColor(context, R.color.white));

        final TextView labelView2 =  tr.findViewById(R.id.label2);
        final TextView descriptionView2 =  tr.findViewById(R.id.description2);
        labelView2.setText(label2); descriptionView2.setText(description2);
        labelView2.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        descriptionView2.setBackgroundColor(ContextCompat.getColor(context, R.color.header_background));
        labelView2.setTextColor(ContextCompat.getColor(context, R.color.white));
        descriptionView2.setTextColor(ContextCompat.getColor(context, R.color.white));

        tr.setPadding(0, 45, 0, 0);
        rows.add(tr);                   // save the collection of rows
        tableLayout.addView(tr);        // add to the table

        View v = new View(this);
        v.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, 1));
        v.setBackgroundColor(ContextCompat.getColor(context, R.color.Black));
        tableLayout.addView(v);
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

    @Override
    protected void onResume() {
        super.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    public void onClick(View view) {

    }
}