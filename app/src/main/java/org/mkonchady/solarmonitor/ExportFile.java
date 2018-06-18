package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

// class to dump files in different formats
public class ExportFile {

    private Context context;
    private SummaryProvider.Summary[] summaries;
    private DetailDB detailsTable = null;
    private ProgressListener progressListener = null;
    private final String newline = Constants.NEWLINE;
    int lat, lon;
    int localLog = 0;

    ExportFile(Context context, ArrayList<SummaryProvider.Summary> summaryArrayList, ProgressListener progressListener) {
        this.context = context;
        summaries = new SummaryProvider.Summary[summaryArrayList.size()];
        this.progressListener = progressListener;
        summaryArrayList.toArray(summaries);
        detailsTable = new DetailDB(DetailProvider.db);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        lat = (int) (sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0.0f) * Constants.MILLION);
        lon = (int) (sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0.0f) * Constants.MILLION);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
    }

    // export summaries in CSV format
    String exportCSV() {
        String msg = "";
        String LOG_FILE;
        FileOutputStream fos1;
        ArrayList<String> outfiles = new ArrayList<>();
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDateTimeSec(summary.getStart_time(), lat, lon);
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                LOG_FILE = "log_" + summary.getMonitor_id() + "_" + dateTime + ".csv";
                fos1 = UtilsFile.openOutputFile(context, LOG_FILE);
                String sb1 = summary.toString("csv") + newline;
                fos1.write(sb1.getBytes());
                ArrayList<DetailProvider.Detail> summaryDetails =
                        detailsTable.getDetails(context, summary.getMonitor_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                    String sb2 = detail.toString("csv") + newline;
                    fos1.write(sb2.getBytes());
                }
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, LOG_FILE);
                outfiles.add(UtilsFile.getFileName(context, LOG_FILE));
                //msg = "Finished export... " + TRIP_FILE;
                progressListener.reportProgress(++numSummaries);
            }

            if (outfiles.size() > 1)
                UtilsFile.zip(context, outfiles.toArray(new String[outfiles.size()]), "backup.zip");

        } catch (IOException ie) {
            msg = "Could not write CSV file " + ie.getMessage();
            Log.e("TAG", msg, localLog);
        }
        return msg;
    }

    String exportXML() {
        String msg = "";
        String LOG_FILE;
        FileOutputStream fos1;
        ArrayList<String> outfiles = new ArrayList<>();
        int numSummaries = 0;
        try {
            for (SummaryProvider.Summary summary: summaries) {
                String dateTime = UtilsDate.getDate(summary.getStart_time(), lat, lon);
                dateTime = dateTime.replace(',','_').replace(' ', '_');
                LOG_FILE = "log_" + summary.getMonitor_id() + "_" + dateTime + ".xml";
                fos1 = UtilsFile.openOutputFile(context, LOG_FILE);
                String header = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?> "      + newline;
                fos1.write(header.getBytes());
                String sb1 = summary.toString("xml") + newline;
                fos1.write(sb1.getBytes());
                ArrayList<DetailProvider.Detail> summaryDetails =
                        detailsTable.getDetails(context, summary.getMonitor_id());
                for (DetailProvider.Detail detail: summaryDetails) {
                    String sb2 = detail.toString("xml") + newline;
                    fos1.write(sb2.getBytes());
                }
                fos1.flush(); fos1.close();
                UtilsFile.forceIndex(context, LOG_FILE);
                outfiles.add(UtilsFile.getFileName(context, LOG_FILE));
                progressListener.reportProgress(++numSummaries);
            }

            if (outfiles.size() > 1)
                UtilsFile.zip(context, outfiles.toArray(new String[outfiles.size()]), "backup.zip");

        } catch (IOException ie) {
            msg = "Could not write XML file " + ie.getMessage();
            Log.e("TAG", msg, localLog);
        }
        return msg;
    }


}