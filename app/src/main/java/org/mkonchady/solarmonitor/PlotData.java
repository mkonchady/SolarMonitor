package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import flanagan.interpolation.LinearInterpolation;

// build the plots for the log
public class PlotData {

    Context context;
    DetailDB detailDB;
    SummaryDB summaryDB;
    float lat, lon;
    int localLog = 0;
    SharedPreferences sharedPreferences;

    final String TAG = "MonitorPlotActivity";

    PlotData(Context context) {
        this.context = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "0"));
        lat = sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0.0f);
        lon = sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0.0f);
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
    }

    // call the function to build the appropriate plot
    Bundle build_daily_plot(int[] log_ids, String plotTitle, int dataIndex) {

        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", plotTitle);
        bundle.putBoolean("singleParm", true);
        buildDailyValues(bundle, log_ids[0], "1", dataIndex); // for left y axis
        buildDailyValues(bundle, log_ids[1], "2", dataIndex); // for right y axis
        return bundle;
    }

    // Build
    private void buildDailyValues (Bundle bundle, int log_id, String suffix, int dataIndex) {

        SummaryProvider.Summary summary = summaryDB.getSummary(context, log_id);
        bundle.putString("ytitle" + suffix, summary.getName() + " (" + summary.getGenerated_watts() + ")");
        long midnight =  UtilsDate.get_midNightTimestamp(summary.getStart_time(), lat, lon);

        // set the x axis limits
        int xstart =  UtilsDate.getTimeSeconds(summary.getStart_time() - midnight);
        bundle.putInt("xorigin" + suffix, xstart + UtilsMisc.calcRange(xstart, -0.10f));
        int xend = UtilsDate.getTimeSeconds(summary.getEnd_time() - midnight);
        bundle.putInt("xlimit" + suffix, UtilsMisc.calcRange(xend, 1.1f));

        // set the y axis limits
        buildDaily(summary, log_id, suffix, midnight, dataIndex, bundle);
        float[]minMax = UtilsMisc.getMinMax(bundle.getFloatArray("ydata" + suffix));
        int ystart = Math.round(minMax[0]);
        bundle.putInt("yorigin" + suffix, ystart + UtilsMisc.calcRange(ystart, -0.10f));
        int yend = Math.round(minMax[1]);
        bundle.putInt("ylimit" + suffix, yend + UtilsMisc.calcRange(yend, 0.1f));

        // set the format for the axes
        bundle.putBoolean("xNumberFormat", false);
        bundle.putBoolean("yNumber1Format", true);
        bundle.putBoolean("yNumber2Format", true);
    }

    // build the x and y axes values from the detail records
    private void buildDaily(SummaryProvider.Summary summary, int log_id, String suffix,
                                 long midnight, int dataIndex, Bundle bundle) {
        ArrayList<DetailProvider.Detail> details = detailDB.getDetails(context, log_id);
        double[] raw_xdata = new double[details.size() + 2];
        double[] raw_ydata = new double[details.size() + 2];

        // set the first x and y values
        int xstart = bundle.getInt("xorigin" + suffix, 0);
        raw_xdata[0] = xstart - 3600;
        switch (dataIndex) {
            case Constants.DATA_WATTHOURS :
                raw_ydata[0] = 0.0f; break;
            case Constants.DATA_WATTSNOW:
                raw_ydata[0] = 0.0f; break;
            case Constants.DATA_TEMPERATURE:
                raw_ydata[0] = details.get(0).getTemperature() - 273; break;
            case Constants.DATA_WIND:
                raw_ydata[0] = details.get(0).getWind_speed(); break;
            case Constants.DATA_CLOUDS:
                raw_ydata[0] = UtilsMisc.getValueinRange(details.get(0).getClouds(), 1.0f, 100.0f); break;
            default :
                break;
        }

        // set the y values from the detail records
        for (int i = 1; i < details.size() + 1; i++) {
            DetailProvider.Detail detail = details.get(i-1);
            raw_xdata[i] = UtilsDate.getTimeSeconds((detail.getTimestamp() * 1000) - midnight);
            switch (dataIndex) {
                case Constants.DATA_WATTHOURS :
                    raw_ydata[i] = detail.getWatts_generated(); break;
                case Constants.DATA_WATTSNOW:
                    raw_ydata[i] = detail.getWatts_now();break;
                case Constants.DATA_TEMPERATURE:
                    raw_ydata[i] = detail.getTemperature() - 273; break;
                case Constants.DATA_WIND:
                    raw_ydata[i] = detail.getWind_speed(); break;
                case Constants.DATA_CLOUDS:
                    raw_ydata[i] = UtilsMisc.getValueinRange(detail.getClouds(), 1.0f, 100.0f); break;
                default :
                    break;
            }
        }

        // set the last y value
        int xend = UtilsDate.getTimeSeconds(details.get(details.size()-1).getTimestamp()* 1000 - midnight);
        raw_xdata[details.size()+1] = xend + 1;
        switch (dataIndex) {
            case Constants.DATA_WATTHOURS :
                raw_ydata[details.size() + 1] = summary.getGenerated_watts(); break;
            case Constants.DATA_WATTSNOW:
                raw_ydata[details.size() + 1] = 0.0f; break;
            case Constants.DATA_TEMPERATURE:
                raw_ydata[details.size() + 1] = details.get(details.size()-1).getTemperature() - 273; break;
            case Constants.DATA_WIND:
                raw_ydata[details.size() + 1] = details.get(details.size()-1).getWind_speed(); break;
            case Constants.DATA_CLOUDS:
                raw_ydata[details.size() + 1] =
                        UtilsMisc.getValueinRange(details.get(details.size()-1).getClouds(), 1.0f, 100.0f); break;
            default :
                break;
        }

        LinearInterpolation lp = new LinearInterpolation(raw_xdata, raw_ydata);
        int time = xstart;
        ArrayList<Integer> xList = new ArrayList<>();
        ArrayList<Float> yList = new ArrayList<>();
        while (time < xend) {
            float yval = (float) lp.interpolate(time);
            xList.add(time);
            yList.add(yval);
            time += Integer.parseInt(sharedPreferences.getString(Constants.MONITORING_FREQUENCY, "5")) * 60; // seconds
        }

        int[] xdata = Ints.toArray(xList);
        float[] ydata = Floats.toArray(yList);
        bundle.putIntArray("xdata" + suffix, xdata);
        bundle.putFloatArray("ydata" + suffix, ydata);

        switch (dataIndex) {
            case Constants.DATA_TEMPERATURE:
            case Constants.DATA_WIND:
            case Constants.DATA_CLOUDS:
                smooth_plot(bundle, true, suffix);
        }
    }


    // call the function to build the appropriate plot
    Bundle build_monthly_plot(int log_id, String plotTitle, String ytitle1, String ytitle2, String ytitle3, int dataIndex) {
        Bundle bundle = new Bundle();
        bundle.putString("plotTitle", plotTitle);
        bundle.putString("ytitle1", ytitle1);
        bundle.putString("ytitle2", ytitle2);
        bundle.putString("ytitle3", ytitle3);

        // set the format for the axes
        switch (dataIndex) {
            case Constants.DATA_PEAKWATTS :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", true); bundle.putBoolean("yNumber2Format", true);
                break;
            case Constants.DATA_PEAKTIME :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", false); bundle.putBoolean("yNumber2Format", true);
                break;
            case Constants.DATA_SUNLIGHT :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", false); bundle.putBoolean("yNumber2Format", true);
                break;
            case Constants.DATA_READINGS :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", true); bundle.putBoolean("yNumber2Format", true);
                break;
            case Constants.DATA_MONTHLY_TEMPERATURE :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", true); bundle.putBoolean("yNumber2Format", true);
                break;
            case Constants.DATA_MONTHLY_CLOUDS :
                bundle.putBoolean("xNumberFormat", true);
                bundle.putBoolean("yNumber1Format", true); bundle.putBoolean("yNumber2Format", true);
                break;
            default :
                break;
        }

        buildMonthlyValues(bundle, log_id, dataIndex); // for left y axis
        return bundle;
    }

    // Build
    private void buildMonthlyValues (Bundle bundle, int log_id, int dataIndex) {

        SummaryProvider.Summary summary = summaryDB.getSummary(context, log_id);

        // get the year, month, and number of days in the month from the timestamp
        int[] yearMonth = UtilsDate.getYearMonth(summary.getStart_time());
        int year = yearMonth[0]; int month = yearMonth[1];
        int numDays = UtilsDate.getNumMonthDays(year, month);
        bundle.putBoolean("singleParm", false);     // two separate y axes

        // set the x axis limits
        int xstart =  0;
        bundle.putInt("xorigin1", xstart); bundle.putInt("xorigin2", xstart);
        int xend = numDays + 1;
        bundle.putInt("xlimit1", xend); bundle.putInt("xlimit2", xend);

        buildMonthly(year, month, numDays, dataIndex, bundle);

        // set the y axis limits
        float[]minMax = UtilsMisc.getMinMax(bundle.getFloatArray("ydata1" ), Constants.DATA_INVALID);
        int ystart1 = Math.round(minMax[0]);
        bundle.putInt("yorigin1", ystart1 + UtilsMisc.calcRange(ystart1, -0.10f));
        int yend1 = Math.round(minMax[1]);
        bundle.putInt("ylimit1", yend1 + UtilsMisc.calcRange(yend1, 0.1f));

        minMax = UtilsMisc.getMinMax(bundle.getFloatArray("ydata2" ), Constants.DATA_INVALID);
        int ystart2 = Math.round(minMax[0]);
        bundle.putInt("yorigin2", ystart2 + UtilsMisc.calcRange(ystart2, -0.10f));
        int yend2 = Math.round(minMax[1]);
        bundle.putInt("ylimit2", yend2 + UtilsMisc.calcRange(yend2, 0.1f));

        if (dataIndex == Constants.DATA_MONTHLY_TEMPERATURE) {
            minMax = UtilsMisc.getMinMax(bundle.getFloatArray("ydata3" ), Constants.DATA_INVALID);
            int ystart3 = Math.round(minMax[0]);
            bundle.putInt("yorigin3", ystart3 + UtilsMisc.calcRange(ystart3, -0.10f));
            int yend3 = Math.round(minMax[1]);
            bundle.putInt("ylimit3", yend3 + UtilsMisc.calcRange(yend3, 0.1f));
        } else {
            bundle.putInt("yorigin3", Constants.DATA_INVALID);
            bundle.putInt("ylimit3",  Constants.DATA_INVALID);
        }
    }

    // build the x and y axes values from the detail records
    private void buildMonthly(int year, int month, int numdays, int dataIndex, Bundle bundle) {

        double[] raw_xdata = new double[numdays + 2];
        double[] raw_ydata1 = new double[numdays + 2];
        double[] raw_ydata2 = new double[numdays + 2];
        double[] raw_ydata3 = null;
        if (dataIndex == Constants.DATA_MONTHLY_TEMPERATURE) {
            raw_ydata3 = new double[numdays + 2];
            Arrays.fill(raw_ydata1, Constants.DATA_INVALID);
            Arrays.fill(raw_ydata2, Constants.DATA_INVALID);
            Arrays.fill(raw_ydata3, Constants.DATA_INVALID);
        }

        // set the first y1 and y2 values
        int xstart = bundle.getInt("xorigin1", 0);
        raw_xdata[0] = xstart;

        // set the y1 and y2 values for the month
        for (int i = 1; i < numdays + 1; i++) {
            raw_xdata[i] = i;
            String filename = UtilsMisc.getDecimalFormat(year, 4) + "-" +
                              UtilsMisc.getDecimalFormat(month + 1, 2) + "-" +
                              UtilsMisc.getDecimalFormat(i, 2);
            if (summaryDB.isInSummaryTable(context, filename)) {
                int log_id = summaryDB.getMonitorID(context, filename);
                SummaryProvider.Summary summary = summaryDB.getSummary(context, log_id);
                switch (dataIndex) {
                    case Constants.DATA_PEAKWATTS:
                        raw_ydata1[i] = summary.getPeak_watts();
                        raw_ydata2[i] = summary.getGenerated_watts();
                        break;
                    case Constants.DATA_PEAKTIME:
                        long midnight =  UtilsDate.get_midNightTimestamp(summary.getStart_time(), lat, lon);
                        raw_ydata1[i] = UtilsDate.getTimeSeconds(summary.getPeak_time() - midnight);
                        raw_ydata2[i] = summary.getGenerated_watts();
                        break;
                    case Constants.DATA_SUNLIGHT:
                        raw_ydata1[i] = UtilsDate.getTimeSeconds(summary.getEnd_time() - summary.getStart_time());
                        raw_ydata2[i] = summary.getGenerated_watts();
                        break;
                    case Constants.DATA_READINGS:
                        raw_ydata1[i] = summaryDB.getDetailCount(context, log_id);
                        raw_ydata2[i] = summary.getGenerated_watts();
                        break;
                    case Constants.DATA_MONTHLY_TEMPERATURE:
                        raw_ydata1[i] = summary.getMinTemperature();
                        raw_ydata2[i] = summary.getMaxTemperature();
                        raw_ydata3[i] = summary.getGenerated_watts();
                        break;
                    case Constants.DATA_MONTHLY_CLOUDS:
                        raw_ydata1[i] = summary.getAvgClouds();
                        raw_ydata2[i] = summary.getGenerated_watts();
                        break;
                    default:
                        break;
                }
            } else {
                if (dataIndex != Constants.DATA_MONTHLY_TEMPERATURE) {
                    raw_ydata1[i] = 0.0;
                    raw_ydata2[i] = 0.0;
                }
            }
        }

        // set the first and last y1 and y2 values
        int xend = numdays;
        raw_xdata[numdays+1] = xend + 1;
        switch (dataIndex) {
            case Constants.DATA_PEAKWATTS :
                raw_ydata1[numdays+1] = raw_ydata1[numdays]; raw_ydata2[numdays+1] = raw_ydata2[numdays];
                raw_ydata1[0] = raw_ydata1[1]; raw_ydata2[0] = raw_ydata2[1]; break;
            case Constants.DATA_PEAKTIME :
                raw_ydata1[numdays+1] = raw_ydata1[numdays]; raw_ydata2[numdays+1] = raw_ydata2[numdays];
                raw_ydata1[0] = raw_ydata1[1]; raw_ydata2[0] = raw_ydata2[1]; break;
            case Constants.DATA_SUNLIGHT :
                raw_ydata1[numdays+1] = raw_ydata1[numdays]; raw_ydata2[numdays+1] = raw_ydata2[numdays];
                raw_ydata1[0] = raw_ydata1[1]; raw_ydata2[0] = raw_ydata2[1]; break;
            case Constants.DATA_READINGS :
                raw_ydata1[numdays+1] = raw_ydata1[numdays]; raw_ydata2[numdays+1] = raw_ydata2[numdays];
                raw_ydata1[0] = raw_ydata1[1]; raw_ydata2[0] = raw_ydata2[1]; break;
            case Constants.DATA_MONTHLY_CLOUDS :
                raw_ydata1[numdays+1] = raw_ydata1[numdays]; raw_ydata2[numdays+1] = raw_ydata2[numdays];
                raw_ydata1[0] = raw_ydata1[1]; raw_ydata2[0] = raw_ydata2[1]; break;
            case Constants.DATA_MONTHLY_TEMPERATURE :
                Number[] numbers = UtilsMisc.toNumber(raw_ydata1, -273); // convert from kelvin to centigrade
                numbers = UtilsMisc.fix_end_numbers(numbers, true);
                numbers = UtilsMisc.fix_mid_numbers(numbers);
                raw_ydata1 = UtilsMisc.toDouble(numbers);

                numbers = UtilsMisc.toNumber(raw_ydata2, -273);
                numbers = UtilsMisc.fix_end_numbers(numbers, true);
                numbers = UtilsMisc.fix_mid_numbers(numbers);
                raw_ydata2 = UtilsMisc.toDouble(numbers);

                numbers = UtilsMisc.toNumber(raw_ydata3);
                numbers = UtilsMisc.fix_end_numbers(numbers, true);
                numbers = UtilsMisc.fix_mid_numbers(numbers);
                raw_ydata3 = UtilsMisc.toDouble(numbers);
            default :
                break;
        }

        LinearInterpolation lp1 = new LinearInterpolation(raw_xdata, raw_ydata1);
        LinearInterpolation lp2 = new LinearInterpolation(raw_xdata, raw_ydata2);
        LinearInterpolation lp3 = (raw_ydata3 != null)? new LinearInterpolation(raw_xdata, raw_ydata3): null;
        int time = xstart;
        ArrayList<Integer> xList = new ArrayList<>();
        ArrayList<Float> yList1 = new ArrayList<>();
        ArrayList<Float> yList2 = new ArrayList<>();
        ArrayList<Float> yList3 = new ArrayList<>();
        while (time < (xend+1)) {
            xList.add(time);
            yList1.add((float) lp1.interpolate(time));
            yList2.add((float) lp2.interpolate(time));
            if (lp3 != null)
                yList3.add((float) lp3.interpolate(time));
            time += 1;
        }

        int[] xdata = Ints.toArray(xList);
        float[] ydata1 = Floats.toArray(yList1);
        float[] ydata2 = Floats.toArray(yList2);
        float[] ydata3 = Floats.toArray(yList3);
        bundle.putIntArray("xdata1", xdata);
        bundle.putIntArray("xdata2", xdata);
        bundle.putIntArray("xdata3", xdata);
        bundle.putFloatArray("ydata1", ydata1);
        bundle.putFloatArray("ydata2", ydata2);
        bundle.putFloatArray("ydata3", ydata3.length > 0? ydata3: null);

        switch (dataIndex) {
            case Constants.DATA_MONTHLY_TEMPERATURE:
                smooth_plot(bundle, true, "1");
                smooth_plot(bundle, true, "2");
                break;
            case Constants.DATA_MONTHLY_CLOUDS:
                smooth_plot(bundle, true, "1");
                break;
        }

    }

    // place the smoothed data in the bundle
    private void smooth_plot(Bundle bundle, boolean spliceCurve, String suffix) {

        int[] xdata = bundle.getIntArray("xdata" + suffix);
        float[] ydata = bundle.getFloatArray("ydata" + suffix);

        // create a list of the points
        int totalPoints = xdata.length;
        ArrayList<UtilsMisc.XYPoint> details = new ArrayList<>();
        for (int i = 0; i < totalPoints; i++)
            details.add(new UtilsMisc.XYPoint(xdata[i], ydata[i]));

        // break up the list into chunks of NUM_POINTS based on the number of curves
        List<List<UtilsMisc.XYPoint>> partitions = new ArrayList<>();
        int NUM_CURVES = (int) Math.round(Math.log10(totalPoints)) * 2;
        int NUM_POINTS = (NUM_CURVES == 0) ? 1 : totalPoints / NUM_CURVES;
        for (int i = 0; i < totalPoints; i += NUM_POINTS)
            partitions.add(details.subList(i, Math.min(i + NUM_POINTS, totalPoints)));

        // for each partition, get 4 control points
        ArrayList<UtilsMisc.XYPoint> fourPoints = new ArrayList<>();
        int color = 0;
        for (int i = 0; i < partitions.size(); i++) {
            //  verify that there are at least four points
            if (partitions.get(i).size() < 4) continue;

            // get the subset of details
            List<UtilsMisc.XYPoint> subDetails = partitions.get(i);
            UtilsMisc.XYPoint[] p = new UtilsMisc.XYPoint[4];

            // set the first and last control points
            double x, y;
            int first = (i == 0) ? 1 : 0;
            //x = (subDetails.get(first).getTimestamp() - start_time) / 1000.0;
            //y = getYCoordinate(subDetails, first, dataIndex);
            x = subDetails.get(first).getX();
            y = subDetails.get(first).getY();
            p[0] = new UtilsMisc.XYPoint(x, y, color);

            int last = subDetails.size() - 1;
            //x = (subDetails.get(last).getTimestamp() - start_time) / 1000.0;
            //y = getYCoordinate(subDetails, last, dataIndex);
            x = subDetails.get(last).getX();
            y = subDetails.get(last).getY();
            p[3] = new UtilsMisc.XYPoint(x, y, color);

            // find the min. and max values for the second and third control points
            // in between the first and last control points
            int min_index = 0;
            int max_index = 0;
            float max_sub_value = 0.0f;
            float min_sub_value = Constants.LARGE_FLOAT;
            for (int j = 1; j < subDetails.size() - 1; j++) {
               // float value = getYCoordinate(subDetails, j, dataIndex);
                float value = (float) subDetails.get(j).getY();
                if (value < min_sub_value) {
                    min_index = j;
                    min_sub_value = value;
                }
                if (value > max_sub_value) {
                    max_index = j;
                    max_sub_value = value;
                }
            }

            double xmin = subDetails.get(min_index).getX();
            double ymin = subDetails.get(min_index).getY();
            double xmax = subDetails.get(max_index).getX();
            double ymax = subDetails.get(max_index).getY();

            if (max_index > min_index) {
                p[1] = new UtilsMisc.XYPoint(xmin, ymin, color);
                p[2] = new UtilsMisc.XYPoint(xmax, ymax, color);
            } else {
                p[2] = new UtilsMisc.XYPoint(xmin, ymin, color);
                p[1] = new UtilsMisc.XYPoint(xmax, ymax, color);
            }

            fourPoints.addAll(Arrays.asList(p));            // build the list of main points
        }

        // build the list of control points,
        ArrayList<UtilsMisc.XYPoint> controlPoints = new ArrayList<>();
        for (int i = 0; i < fourPoints.size(); i++) {
            controlPoints.add(fourPoints.get(i));
            // in a spliced curve, add a center control point for every alternate pair of points
            if (spliceCurve && (i % 2 == 0) && (i > 0) && (i + 3) < fourPoints.size()) {
                controlPoints.add(UtilsMisc.center(fourPoints.get(i), fourPoints.get(i + 1)));
            }
        }

        // for a spliced curve do the 4 point bezier interpolation from 0..3, 3..6, 6..9
        // otherwise, do the 4 point bezier interpolation from 0..3, 4..7, 8..11
        UtilsMisc.XYPoint p1, p2, p3, p4;
        ArrayList<UtilsMisc.XYPoint> allPoints = new ArrayList<>();
        int INCREMENT = spliceCurve ? 3 : 4;
        for (int i = 0; i < controlPoints.size(); i += INCREMENT) {
            if ((i + 3) < controlPoints.size()) {
                p1 = controlPoints.get(i);
                p2 = controlPoints.get(i + 1);
                p3 = controlPoints.get(i + 2);
                p4 = controlPoints.get(i + 3);
                allPoints.addAll(UtilsMisc.BezierInterpolate(p1, p2, p3, p4));
            }
        }

        // remove duplicate x coordinates
        ArrayList<Integer> times_list = new ArrayList<>();
        ArrayList<Float> values_list = new ArrayList<>();
        Hashtable<Integer, Boolean> seenInt = new Hashtable<>();
        for (int i = 0; i < allPoints.size(); i++) {
            int xloc = (int) Math.round(allPoints.get(i).getX());
            if (seenInt.containsKey(xloc)) continue;
            seenInt.put(xloc, true);
            times_list.add(xloc);
            values_list.add((float) allPoints.get(i).getY());
        }

        // convert array list to array
        int[] smooth_times = UtilsMisc.convertIntegers(times_list);
        float[] smooth_values = UtilsMisc.convertFloats(values_list);

        bundle.putIntArray("xdata" + suffix, smooth_times);
        bundle.putFloatArray("ydata" + suffix, smooth_values);
    }

}