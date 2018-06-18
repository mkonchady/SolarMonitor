package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;

// class to import files in different formats
class ImportFile {

    private Context context;
    private DetailDB detailTable = null;
    private SummaryDB summaryTable = null;
    private String LOG_FILE = "";
    private ProgressListener progressListener = null;

    private final String TAG = "ImportFile";
    int localLog = 0;

    ImportFile(Context context, ProgressListener progressListener, String filename) {
        this.context = context;
        this.progressListener = progressListener;
        detailTable = new DetailDB(DetailProvider.db);
        summaryTable = new SummaryDB(SummaryProvider.db);
        LOG_FILE = filename;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
    }

    // import summaries in CSV format
    int importCSV() {
        String msg;
        Log.d(TAG, "Started importing CSV file ...", localLog);
        int log_id = 0;
        try {
            int file_size = countLines();
            int num_lines = 0;
            int num_sub_lines = file_size / 4;
            String line;
            BufferedReader br = new BufferedReader(new FileReader(new File(LOG_FILE)));
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",", SummaryProvider.NUM_FIELDS);
                if (firstLine) {
                    firstLine = false;
                    long start_time = Long.parseLong(fields[2].trim());
                    long end_time = Long.parseLong(fields[3].trim());
                    int peak_watts = Integer.parseInt(fields[4].trim());
                    long peak_time = Long.parseLong(fields[5].trim());
                    int generated_watts = Integer.parseInt(fields[6].trim());
                    SummaryProvider.Summary summary = new SummaryProvider.Summary(0,
                            fields[1].trim(), start_time, end_time, peak_watts, peak_time, generated_watts,
                            Constants.MONITOR_RUNNING, "");
                    log_id = summaryTable.addSummary(context, summary);
                } else {
                    DetailProvider.Detail detail = DetailProvider.createDetail(log_id, fields);
                    detailTable.addDetail(context, detail);
                }
                if (progressListener != null && ++num_lines % num_sub_lines == 0)
                    progressListener.reportProgress( Math.round(100.0f * (num_lines + 1.0f) / file_size) );
            }
            //br.close();
        } catch (IOException ie) {
            msg = "Could not read file: " + LOG_FILE + " " + ie.getMessage();
            Log.e(TAG, msg, localLog);
        } catch (IndexOutOfBoundsException ee) {
            msg = "No. of fields mismatch " + LOG_FILE + " " + ee.getMessage();
            Log.e(TAG, msg, localLog);
        } finally {
            summaryTable.setSummaryStatus(context, Constants.MONITOR_FINISHED, log_id);
        }
        return log_id;
    }

    int importXML() {

        Log.d(TAG, "Started importing XML file ...", localLog);

        // create a new summary for the log with default values and generate a log id
        SummaryProvider.Summary summary = SummaryProvider.createSummary(0L);
        int log_id = summaryTable.addSummary(context, summary);

        try {
            // open and count the number of lines in the file
            int file_size = countLines();
            int num_lines = 0;
            int num_sub_lines = (file_size >= 16)? file_size / 16: 1;

            // use the parser to read the file
            XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
            XmlPullParser parser = xmlFactoryObject.newPullParser();

            InputStream fos = new FileInputStream(new File(LOG_FILE));
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(fos, null);

            int event = parser.getEventType();
            String text = "";

            // format for lat and lon values
            DecimalFormat df = new DecimalFormat("##.######");
            df.setRoundingMode(RoundingMode.FLOOR);

            // default detail parameters
            int watts_now = 0;
            int watts_generated = 0;
            long timestamp = 0;
            String weather = "";
            float temperature = 0.0f;
            float clouds = 0.0f;
            float wind_speed = 0.0f;
            String index = "";

            // parse the document
            while (event != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();

                switch (event) {
                    case XmlPullParser.START_TAG:
                        break;

                    case XmlPullParser.TEXT:
                        text = parser.getText().trim();
                        break;

                    case XmlPullParser.END_TAG:
                        switch (name) {

                            // ------ Summary fields -------------
                            case SummaryProvider.NAME:
                                summary.setName(text);break;
                            case SummaryProvider.START_TIME:
                                summary.setStart_time(Long.parseLong(text));break;
                            case SummaryProvider.END_TIME:
                                summary.setEnd_time(Long.parseLong(text));break;
                            case SummaryProvider.PEAK_WATTS:
                                summary.setPeak_watts(Integer.parseInt(text));break;
                            case SummaryProvider.PEAK_TIME:
                                summary.setPeak_time(Long.parseLong(text));break;
                            case SummaryProvider.GENERATED_WATTS:
                                summary.setGenerated_watts(Integer.parseInt(text));break;
                            case SummaryProvider.STATUS:
                                summary.setStatus(text); break;
                            case SummaryProvider.EXTRAS:
                                summary.setExtras(text); break;

                            // ----------- Detail fields ---------------
                            case DetailProvider.TIMESTAMP:
                                timestamp = Long.parseLong(text); break;
                            case DetailProvider.WATTS_NOW:
                                watts_now = Integer.parseInt(text);break;
                            case DetailProvider.WATTS_GENERATED:
                                watts_generated = Integer.parseInt(text);break;
                            case DetailProvider.WEATHER:
                                weather = text; break;
                            case DetailProvider.TEMPERATURE:
                                temperature = Float.parseFloat(text);break;
                            case DetailProvider.CLOUDS:
                                clouds = Float.parseFloat(text);break;
                            case DetailProvider.WIND_SPEED:
                                wind_speed = Float.parseFloat(text);break;
                            case DetailProvider.INDEX:
                                index = text; break;
                            case DetailProvider.READING:
                                DetailProvider.Detail detail = new DetailProvider.Detail(log_id, timestamp,
                                    watts_generated, watts_now, weather, temperature, clouds, wind_speed, index);
                            detailTable.addDetail(context, detail);

                        }
                        break;
                }
                event = parser.next();
                if (progressListener != null && (++num_lines % num_sub_lines == 0) && (num_lines < file_size) )
                    progressListener.reportProgress( Math.round(100.0f * (num_lines + 1.0f) / file_size) );
            }

            summaryTable.setSummaryParameters(context, summaryTable.putSummaryValues(summary), log_id);
            summaryTable.setSummaryStatus(context, Constants.MONITOR_FINISHED, log_id);
            Log.d(TAG, "Finished importing file with " + summaryTable.getDetailCount(context, log_id) + " details", localLog);
            fos.close();
        } catch (XmlPullParserException xe) {
            Log.e(TAG, "GPX Parse error: " + xe.getMessage(), localLog);
        } catch (IOException ie) {
            Log.e(TAG, "GPX IO Error: " + ie.getMessage(), localLog);
        } catch (NumberFormatException ne) {
            Log.e(TAG, "Number format error: " + ne.getMessage(), localLog);
        }
        return log_id;
    }

    // restore a bunch of logs from a zip file
    int[] importZIP() {
        ArrayList<String> files = UtilsFile.unzip(context, LOG_FILE);
        Collections.sort(files);
        int[] logIds = new int[files.size()];
        int logId = 0;
        int i = 0;
        for (String file: files) {
            Log.d(TAG, "Importing file: " + file, localLog);
            LOG_FILE = UtilsFile.getFileName(context, file);
            String suffix = UtilsFile.getFileSuffix(file);
            switch (suffix) {
                case "csv": logId = importCSV(); break;
                case "xml": logId = importXML(); break;
            }
            logIds[i++] = logId;
        }
        return logIds;
    }

    private int countLines() throws  IOException {
        BufferedReader br = new BufferedReader(new FileReader(new File(LOG_FILE)));
        int lines = 0;
        while (br.readLine() != null) lines++;
        br.close();
        return lines;
    }
}