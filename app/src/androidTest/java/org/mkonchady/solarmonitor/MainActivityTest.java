package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

import com.robotium.solo.Solo;

import java.util.ArrayList;
import java.util.Arrays;

import flanagan.interpolation.CubicInterpolation;
import flanagan.interpolation.LinearInterpolation;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private Solo solo = null;
    MainActivity mainActvity = null;
    Context context;
    private String TAG = "MainActivity";

    // calls startUp
    public MainActivityTest() {
        super(MainActivity.class);
    }

    public void setUp() throws Exception {
        super.setUp();
        solo = new Solo(this.getInstrumentation(), this.getActivity());
        mainActvity = (MainActivity) solo.getCurrentActivity();
        context = getInstrumentation().getContext();
    }

    public void tearDown() throws Exception {
        solo.finishOpenedActivities();
    }

    public void testFunctions() throws Exception {
        //checkDBFunctions();
        checkDateFunctions();
        //checkDistanceFunctions();
        //checkOtherFunctions();
        //checkNetworkFunctions();
        //checkSMSFunction();
        //checkInteropFunctions();
    }

    void checkDBFunctions() throws Exception {
        Context context = mainActvity;
        SummaryDB summaryDB = new SummaryDB(SummaryProvider.db);

        summaryDB.delSummary(context, 2, true);
        ArrayList<SummaryProvider.Summary> summaries = summaryDB.getSummaries(context, null,
                null, -1);
        for (SummaryProvider.Summary summary: summaries) {
            String outline = summary.toString("csv");
            assertEquals(outline, "");
        }
    }

    void checkDateFunctions() {
        boolean validDate = UtilsDate.isDate("Jul 21, 2016");
        assertEquals(true, validDate);
        validDate = UtilsDate.isDate("Jul 35, 2016");
        assertEquals(false, validDate);

        String dateTime = UtilsDate.getDateTimeSec(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 2016 17:35:52", dateTime);
        dateTime = UtilsDate.getDateTime(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 17:35", dateTime);
        dateTime = UtilsDate.getTime(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("17:35", dateTime);
        dateTime = UtilsDate.getDate(1455797152543L, Constants.LARGE_INT, Constants.LARGE_INT);
        assertEquals("Feb 18, 2016", dateTime);
        dateTime = UtilsDate.getZuluDateTimeSec(1517726644000L);
        assertEquals("2018-02-04T06:44:04Z", dateTime);


        long detailTimestamp = UtilsDate.getDetailTimeStamp("2016-09-30T11:23:28.276+0530");
        assertEquals(1475214808276L, detailTimestamp);
        detailTimestamp = UtilsDate.getDetailTimeStamp("2010-02-11T03:52:45.000Z");
        assertEquals(1265860365000L, detailTimestamp);

        String duration = UtilsDate.getTimeDurationHHMMSS(3800 * 1000, false);
        assertEquals("01:03:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(3800100, false);
        assertEquals("01:03:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(3800900, false);
        assertEquals("01:03:21", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(800 * 1000, false);
        assertEquals("13:20", duration);
        duration = UtilsDate.getTimeDurationHHMMSS(4 * 1000, false);
        assertEquals("00:04", duration);

        String filename = "monitor_2018-03-25.log";
        String fdate = UtilsDate.getLogfileDateTime(filename, "monitor_", ".log");
        assertEquals("2018-03-25", fdate);

        long millis = 1522209607000L;       // march 28th, 2018, 9:30 am local TZ
        long midnight = UtilsDate.get_midNightTimestamp(millis, 13.023474f, 77.57711f);
        long expected = 1522175400000L;     // march 28th, 2018, 12:00 am local TZ
        assertEquals(expected, midnight);

        millis = 1582948807000L;            // feb 29th, 2020, 4:07 am local TZ
        midnight = UtilsDate.get_midNightTimestamp(millis, 13.023474f, 77.57711f);
        expected = 1582914600000L;          // feb 29th, 2020, 12:00 am local TZ
        assertEquals(expected, midnight);

    }

    void checkDistanceFunctions() {

        // kms to miles
       // float miles = Constants.kmsToMiles(2.6f);
       // assertEquals(1.61557f, miles, 0.05f);

        // rounding up/down numbers
        boolean ceil = false;
        int rounded = UtilsMisc.roundToNumber(13256789, 2, ceil);
        assertEquals(13256700, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(13256789, 2, ceil);
        assertEquals(13256800, rounded);
        ceil = false;
        rounded = UtilsMisc.roundToNumber(13256789, 1, ceil);
        assertEquals(13256780, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(13256789, 1, ceil);
        assertEquals(13256790, rounded);

        rounded = UtilsMisc.roundToNumber(-13256789, 2, ceil);
        assertEquals(-13256600, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(-13256789, 2, ceil);
        assertEquals(-13256600, rounded);
        ceil = false;
        rounded = UtilsMisc.roundToNumber(-13256789, 1, ceil);
        assertEquals(-13256780, rounded);
        ceil = true;
        rounded = UtilsMisc.roundToNumber(-13256789, 1, ceil);
        assertEquals(-13256770, rounded);

        // normalization
        float[] floats = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f};
        float[] norms = UtilsMisc.normalize(floats);
        assertEquals("[0.0, 25.0, 50.0, 75.0, 100.0]", Arrays.toString(norms));

        // string rep. of float with 3 significant digits
        float number = 2.54678f;
        String formatNumber = UtilsMisc.formatFloat(number, 3);
        assertEquals("2.547", formatNumber);
        number = 2.54638f;
        formatNumber = UtilsMisc.formatFloat(number, 3);
        assertEquals("2.546", formatNumber);

        // check the standard deviation calculation
        double[] nums = {1.0f, 2.0f, 3.0f, 4.0f};
        double sd = UtilsMisc.calcStandardDeviation(nums);
        assertEquals(1.29f, sd, 0.005f * sd);

        double[] nums1 = {101.0f, 202.0f, 303.0f, 404.0f};
        sd = UtilsMisc.calcStandardDeviation(nums1);
        assertEquals(130.9f, sd, 0.005f * sd);

        double lat = 13.023366; double lon = 77.577815;
        String timezone = TimezoneMapper.latLngToTimezoneString(lat, lon);
        assertEquals("Timezone1 mismatch", "Asia/Kolkata", timezone);
        lat = 38.9072; lon = -77.0369;
        timezone = TimezoneMapper.latLngToTimezoneString(lat, lon);
        assertEquals("Timezone2 mismatch", "America/New_York", timezone);
    }

    void checkOtherFunctions() {

        // number of elements, max. size, min. size
        int[] partitions = UtilsMisc.getPartitions(20, 90, 10);
        assertTrue("Partition1", intArrayCheck(partitions, new int[] {20}));

        partitions = UtilsMisc.getPartitions(90, 90, 10);
        assertTrue("Partition2", intArrayCheck(partitions, new int[] {90}));

        partitions = UtilsMisc.getPartitions(100, 90, 10);
        assertTrue("Partition3", intArrayCheck(partitions, new int[] {100}));

        partitions = UtilsMisc.getPartitions(101, 90, 10);
        assertTrue("Partition4", intArrayCheck(partitions, new int[] {90, 11}));

        partitions = UtilsMisc.getPartitions(120, 90, 10);
        assertTrue("Partition5", intArrayCheck(partitions, new int[] {90,30}));

        partitions = UtilsMisc.getPartitions(180, 90, 10);
        assertTrue("Partition6", intArrayCheck(partitions, new int[] {90,90}));

        partitions = UtilsMisc.getPartitions(190, 90, 10);
        assertTrue("Partition7", intArrayCheck(partitions, new int[] {90,100}));

        partitions = UtilsMisc.getPartitions(191, 90, 10);
        assertTrue("Partition8", intArrayCheck(partitions, new int[] {90,90, 11}));

        String doubleValue = UtilsMisc.formatDouble(2.1234567, 6);
        assertEquals("Rounding", "2.123457", doubleValue);
        doubleValue = UtilsMisc.formatDouble(2.1234563, 6);
        assertEquals("Rounding", "2.123456", doubleValue);

        String floatValue = UtilsMisc.formatFloat(2.1234567f, 3);
        assertEquals("Rounding", "2.123", floatValue);
        floatValue = UtilsMisc.formatFloat(200.1236563f, 3);
        assertEquals("Rounding", "200.124", floatValue);

        floatValue = UtilsMisc.formatFloat(2.1234567f, 2);
        assertEquals("Rounding", "2.12", floatValue);
        floatValue = UtilsMisc.formatFloat(200.1266563f, 2);
        assertEquals("Rounding", "200.13", floatValue);

        // calculate the percentage
        double percentage = UtilsMisc.calcPercentage(0, 100, 50);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(0, 200, 50);
        assertEquals("Percentage Error", 25.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(100, 200, 150);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(-100, 100, 0);
        assertEquals("Percentage Error", 50.0, percentage, 0.001);

        percentage = UtilsMisc.calcPercentage(-200, 200, 50.0f);
        assertEquals("Percentage Error", 62.5, percentage, 0.001);

        // smooth function
        //float[] raw1 = {1.0f, 2.0f, 3.0f, 25.0f, 6.0f, 7.0f, 8.0f};
        //double[] smoothed = UtilsMisc.smoothSpeeds(raw1);
        //assertTrue("Smooth failed", arrayCheck(smoothed, new float[]{1.0f, 2.0f, 3.0f, 4.5f, 6.0f, 7.0f, 8.0f}));

        //float[] raw2 = {1.0f, 2.0f, 3.0f, 25.0f, 6.0f, 7.0f, 8.0f, 9.0f};
        //smoothed = UtilsMisc.smoothData(raw2, 1.5f);
        //assertTrue("Smooth failed", arrayCheck(smoothed, new float[]{1.0f, 2.0f, 3.0f, 4.5f, 6.0f}));

        // interpolation
        Number[] raw = new Number[] {10.0, 20.0, 0.0, -1.0, -1.0, 30.0, -1.0, -1.0, 20.0};
        Number[] interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 1", numberCheck(interpolated, new Number[]{10.0, 20.0, 22.5, 25.0, 27.5, 30.0, 26.66, 23.33, 20.0}, 0.5f));

        raw = new Number[] {10.0, 20.0, 30.0,20.0};
        interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 2", numberCheck(interpolated, new Number[]{10.0, 20.0, 30.0, 20.0}, 0.5f));

        raw = new Number[] {0.0, 99.0, 99.0,99.0};
        interpolated = UtilsMisc.Interpolate(raw, true);
        assertTrue("Smooth Numbers failed 2a", numberCheck(interpolated, new Number[]{99.0, 99.0, 99.0, 99.0}, 0.5f));

        Number[] numbers =  {1.0f, 0.0f, 5.0f};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 3", numberCheck(interpolated, new Number[]{1.0, 3.0, 5.0}, 0.1f));

        numbers = new Number[] {-10.0f, 20.0f, null,20.0f};
        interpolated = UtilsMisc.Interpolate(numbers, false);
        assertTrue("Smooth Numbers failed 2", numberCheck(interpolated, new Number[]{-10.0, 20.0, 20.0, 20.0}, 0.5f));

        numbers = new Number[] {null, 1.0f, 0.0f, 3.0f};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 3", numberCheck(interpolated, new Number[]{1.0f, 1.0, 2.0, 3.0}, 0.1f));

        numbers = new Number[] {null, 1.0f, 0.0f, 3.0f, null, null};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 4", numberCheck(interpolated, new Number[]{1.0f, 1.0, 2.0, 3.0, 3.0, 3.0}, 0.1f));

        numbers = new Number[] {null, 20.0f, 0.0f, -10.0f, 30.0f, 10.0f, 20.0f, null};
        interpolated = UtilsMisc.fix_end_numbers(numbers, true);
        assertTrue("Smooth Numbers failed 5", numberCheck(interpolated, new Number[]{20.0, 20.0, 0.0, -10.0, 30.0, 10.0f, 20.0f, 20.0f}, 0.1f));

        numbers = new Number[] {null, 20.0f, 0.0f, -10.0f, 30.0f, null, 20.0f, null};
        interpolated = UtilsMisc.fix_end_numbers(numbers, true);
        assertTrue("Smooth Numbers failed 5a", numberCheck(interpolated, new Number[]{20.0, 20.0, 0.0, -10.0, 30.0, null, 20.0f, 20.0f}, 0.1f));

        numbers = new Number[] {20.0f, 20.0f, 0.0f, -10.0f, 30.0f, null, 20.0f, 20.0f};
        interpolated = UtilsMisc.fix_mid_numbers(numbers);
        assertTrue("Smooth Numbers failed 5b", numberCheck(interpolated, new Number[]{20.0, 20.0, 0.0, -10.0, 30.0, 30.0, 20.0f, 20.0f}, 0.1f));

        numbers = new Number[] {20.0f, 20.0f, -10.0f, null, null, 30.0f, null, 20.0f, 20.0f};
        interpolated = UtilsMisc.fix_mid_numbers(numbers);
        assertTrue("Smooth Numbers failed 5b", numberCheck(interpolated, new Number[]{20.0, 20.0, -10.0, -10.0, -10.0, 30.0, 30.0, 20.0f, 20.0f}, 0.1f));

        // interpolate with positive numbers alone
        numbers = new Number[] {null, 20.0f, 0.0f, -10.0f, -15.0f, 30.0f, null, 20.0f, null};
        interpolated = UtilsMisc.Interpolate(numbers, true);
        assertTrue("Smooth Numbers failed 6", numberCheck(interpolated, new Number[]{20.0, 20.0, 22.5, 25.0, 27.5, 30.0, 25.0, 20.0f, 20.0f}, 0.1f));

        // interpolate with non-zero numbers alone
        numbers = new Number[] {null, 20.0f, null, -10.0f, -15.0f, 30.0f, null, 20.0f, null};
        interpolated = UtilsMisc.Interpolate(numbers, false);
        assertTrue("Smooth Numbers failed 7", numberCheck(interpolated, new Number[]{20.0, 20.0, 5.0f, -10.0, -15.0, 30.0, 25.0, 20.0f, 20.0f}, 0.1f));

        Integer[] nums =  new Integer[]{1, 0, 5};
        interpolated = UtilsMisc.Interpolate(nums, true);
        assertTrue("Smooth Numbers failed 8", numberCheck(interpolated, new Number[]{1, 3, 5}, 0.1f));

        Number[] numsi = new Number[] {0, 20, 0, -10, -15, 30, 0, 20, 0};
        interpolated = UtilsMisc.Interpolate(numsi, true);
        assertTrue("Smooth Numbers failed 9", numberCheck(interpolated, new Number[]{20, 20, 22.5, 25.0, 27.5, 30, 25, 20, 20}, 0.1f));

        numsi = new Number[] {0, 20, 0, -10, -15, 30, 0, 20, 0};
        interpolated = UtilsMisc.Interpolate(numsi, false);
        assertTrue("Smooth Numbers failed 10", numberCheck(interpolated, new Number[]{20, 20, 5.0, -10.0, -15.0, 30, 25, 20, 20}, 0.1f));

        // linear interpolation
        ArrayList<Number> linear = UtilsMisc.LinearInterpolate(10, 20, 4);
        assertTrue("Linear interpolation 1 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                                                               new Number[]{12, 14, 16, 18}, 0.5f));

        linear = UtilsMisc.LinearInterpolate(-10, -20, 4);
        assertTrue("Linear interpolation 2 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                new Number[]{-12, -14, -16, -18}, 0.5f));

        linear = UtilsMisc.LinearInterpolate(-10, 10, 9);
        assertTrue("Linear interpolation 3 failed", numberCheck(linear.toArray(new Number[linear.size()]),
                new Number[]{-8, -6, -4, -2, 0, 2, 4, 6, 8}, 0.5f));

        assertTrue("Bezier interpolation failed", bezierTest());

        // file suffix extraction
        String suffix = UtilsFile.getFileSuffix("/opt/android/aa.txt");
        assertEquals("txt", suffix);
        suffix = UtilsFile.getFileSuffix("C:\\Progams Files\\code\\test.java");
        assertEquals("java", suffix);



        String[] in = {"0", null, "1", null};
        assertTrue("Fix nulls failed",
                stringArrayCheck(new String[]{"0", "0", "1", "0"}, UtilsMisc.fixNulls(in, "0")));

        double x = 13.456702 * Constants.MILLION;
        assertEquals("Failed to convert to Integer ", 13456702, UtilsMisc.convertToInteger(x));
        x = 13.456702333 * Constants.MILLION;
        assertEquals("Failed to convert to Integer ", 13456702, UtilsMisc.convertToInteger(x));
        x = 13.456702733 * Constants.MILLION;
        assertEquals("Failed to convert to Integer ", 13456702, UtilsMisc.convertToInteger(x));

        String[] dupString = {"one", "two", "two", "three", "four", "four"};
        String[] dedup = UtilsMisc.removeDups(dupString);
        String[] expected = {"one", "two", "three", "four"};
        assertTrue(stringArrayCheck(expected, dedup));
    }

    void checkInteropFunctions() {

        final int SIZE = 10;
        final int OFFSET = 15600000;
        double[] xx = new double[SIZE];
        for (int i = 0; i < SIZE; i++) xx[i] = i + OFFSET;
        double[] yy = new double[SIZE];
        for (int i = 0; i < SIZE; i++) yy[i] = i * 2;

        LinearInterpolation lp = new LinearInterpolation(xx, yy);
        double y = lp.interpolate(7.5 + OFFSET);
        assertEquals(15.0, y);

        xx = new double[4];
        yy = new double[4];

        xx[0] = 1519866339; yy[0] = 0;
        xx[1] = 1519883840; yy[1] = 4819;
        xx[2] = 1520333840; yy[2] = 15872;

        lp = new LinearInterpolation(xx, yy);
        double xval = xx[0] + (xx[1] - xx[0]) / 2.0;
        y = lp.interpolate(xval);
        assertEquals(yy[1] / 2.0, y);
        xval = xx[1] + (xx[2] - xx[1]) / 2.0;
        y = lp.interpolate(xval);
        assertEquals(yy[1] + (yy[2] - yy[1]) / 2.0, y);


        xx[0] = 1519866339; yy[0] = 0;
        xx[1] = 1519883840; yy[1] = 4819;
        xx[3] = 1520333840; yy[3] = 15872;
        xx[2] = 1519909179; yy[2] = 15872;
        CubicInterpolation cp = new CubicInterpolation(xx, yy, 0);
        xval = xx[1] + (xx[2] - xx[1]) * 0.85;
        y = cp.interpolate(xval);
        assertEquals(y, 10.0);

    }

    void checkNetworkFunctions() {

        /*
        //String url = "http://api.openweathermap.org/data/2.5/weather?appid=c5d14b4cafe6af9c3e1606b744f73156&lat=13.023315&lon=77.577896";
        for (int i = 0; i < 10; i++) {
            String url = "http://192.168.1.75/api/v1/production/";
            String json_response = UtilsNetwork.getJSON(url, context);
            boolean no_response = json_response.equals(UtilsNetwork.NO_RESPONSE);
            Log.d("Test", "Test no. " + i);
            assertFalse(no_response);
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        UtilsNetwork.isEnvoyAlive("192.168.1.75", sp);
        for (int i = 0; i < 10; i++) {
            String alive = sp.getString(Constants.ENVOY_STATUS,Constants.ENVOY_DEAD);
            Log.d(TAG, "Envoy is " + alive);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException ie) {

            }
        }

        UtilsNetwork.get_lan_servers(context);
        try {
            TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException ie) {
        }
*/
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String[] servers = pref.getString(Constants.LAN_SERVERS, "").split(Constants.DELIMITER);
        Log.d(TAG, "FOund the servers");
    }

    private boolean stringArrayCheck(String[] one, String[] two) {
        if (one.length != two.length) return false;
        for (int i = 0; i < one.length; i++) {
            if (!one[i].equals(two[i])) return false;
        }
        return true;
    }

    private boolean intArrayCheck(int[] one, int[] two) {
        if (one.length != two.length) return false;
        for (int i = 0; i < one.length; i++) {
            if (one[i] != two[i]) return false;
        }
        return true;
    }

    private boolean numberCheck(Number[] one, Number[] two, float tolerance) {
        if (one.length != two.length) return false;
        for (int i = 0; i < one.length; i++) {
            if (one[i] == null && two[i] != null) return false;
            if (one[i] != null && two[i] == null) return false;
            if (one[i] == null && two[i] == null) continue;
            if (Math.abs(one[i].floatValue() - two[i].floatValue()) > tolerance)
                return false;
        }
        return true;
    }

    private boolean bezierTest() {

        // Set control points
        UtilsMisc.XYPoint p1 = new UtilsMisc.XYPoint(100, 300, 0);
        UtilsMisc.XYPoint p2 = new UtilsMisc.XYPoint(150, 100, 0);
        UtilsMisc.XYPoint p3 = new UtilsMisc.XYPoint(200, 100, 0);
        UtilsMisc.XYPoint p4 = new UtilsMisc.XYPoint(250, 300, 0);
        ArrayList<UtilsMisc.XYPoint> points = UtilsMisc.BezierInterpolate(p1, p2, p3, p4);
        String expected = " 100 300 104 285 104 285 108 271 108 271 111 258 111 258 115 246 115" +
                          " 246 119 234 119 234 123 223 123 223 126 213 126 213 130 204 130 204" +
                          " 134 195 134 195 138 187 138 187 141 180 141 180 145 174 145 174 149" +
                          " 168 149 168 153 163 153 163 156 159 156 159 160 156 160 156 164 153" +
                          " 164 153 168 151 168 151 171 150 171 150 175 150 175 150 179 150 179" +
                          " 150 183 152 183 152 186 153 186 153 190 156 190 156 194 159 194 159" +
                          " 198 164 198 164 201 168 201 168 205 174 205 174 209 180 209 180 213" +
                          " 188 213 188 216 195 216 195 220 204 220 204 224 213 224 213 228 224" +
                          " 228 224 231 234 231 234 235 246 235 246 239 258 239 258 243 272 243" +
                          " 272 246 285 246 285 250 300";
        StringBuilder results = new StringBuilder();
        for (int i = 1; i < points.size(); i++) {
            int x1 = (int) Math.round(points.get(i-1).getX());
            int y1 = (int) Math.round(points.get(i-1).getY());
            int x2 = (int) Math.round(points.get(i).getX());
            int y2 = (int) Math.round(points.get(i).getY());
            String out = " " + x1 + " " +  y1 + " " + x2 + " " + y2;
            results.append(out);
        }
        return (expected.compareTo(results.toString()) == 0);
    }

    private String dumpArrayList(ArrayList<String> arrayList) {
        StringBuilder out = new StringBuilder();
        for (String key: arrayList)
            out.append(key + "!!!");
        return out.toString();
    }

    private String dumpArray(String[] arrayList) {
        StringBuilder out = new StringBuilder();
        for (String key: arrayList)
            out.append(key + "!!!");
        return out.toString();
    }
}