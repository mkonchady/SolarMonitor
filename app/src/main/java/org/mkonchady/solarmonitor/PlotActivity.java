package org.mkonchady.solarmonitor;

import android.app.Activity;
//import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.MotionEvent;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;

import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.Utils;

import java.util.ArrayList;

/**
 * Extract / Plot data from the details table and return to caller
 */

public class PlotActivity extends Activity implements SeekBar.OnSeekBarChangeListener,
        OnChartGestureListener, OnChartValueSelectedListener {
    //private

    //private Context context;
    DetailDB detailDB;
    SummaryDB summaryDB;
    final String TAG = "PlotActivity";

    final static IndexAxisValueFormatter timeFormatter= new IndexAxisValueFormatter() {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            return UtilsDate.getTimeDurationHHMM((long) value * 1000);
        }
    };

    final static IndexAxisValueFormatter numberFormatter= new IndexAxisValueFormatter() {
        @Override
        public String getFormattedValue(float value, AxisBase axis) {
            return UtilsMisc.getDecimalFormat(value);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Paint transparentPaint = new Paint(); transparentPaint.setAlpha(0);
        detailDB = new DetailDB(DetailProvider.db);
        summaryDB = new SummaryDB(SummaryProvider.db);
        buildLChart(getIntent().getBundleExtra(Constants.DATA_BUNDLE));
        //this.context = getApplicationContext();
    }

    /*
        String: plotTitle, yTitle1, yTitle2,
        int: log_id, xorigin, xLimit, yorigin1, yLimit1, yorigin2, yLimit2
        int[]: xdata
        float[]: ydata1, ydata2
     */
    public void buildLChart(Bundle bundle) {

        String plotTitle = bundle.getString("plotTitle");
        String ytitle1 = bundle.getString("ytitle1");
        String ytitle2 = bundle.getString("ytitle2");
        String ytitle3 = bundle.getString("ytitle3");
        boolean singleParm = bundle.getBoolean("singleParm");

        int xorigin1 = bundle.getInt("xorigin1");
        int xlimit1 = bundle.getInt("xlimit1");
        int yorigin1 = bundle.getInt("yorigin1");
        int ylimit1 = bundle.getInt("ylimit1");

        int xorigin2 = bundle.getInt("xorigin2");
        int xlimit2 = bundle.getInt("xlimit2");
        int yorigin2 = bundle.getInt("yorigin2");
        int ylimit2 = bundle.getInt("ylimit2");
        int yorigin3 = bundle.getInt("yorigin3");
        int ylimit3 = bundle.getInt("ylimit3");

        int[] xdata1 = bundle.getIntArray("xdata1");
        float[] ydata1 = bundle.getFloatArray("ydata1");
        int[] xdata2 = bundle.getIntArray("xdata2");
        float[] ydata2 = bundle.getFloatArray("ydata2");
        int[] xdata3 = bundle.getIntArray("xdata3");
        float[] ydata3 = bundle.getFloatArray("ydata3");
        boolean tripleParm = ydata3 != null && ydata3.length > 0;

        setContentView(R.layout.activity_linechart);
        TextView titleView = findViewById(R.id.lineTitleView);

        // build the line chart
        LineChart lChart = findViewById(R.id.lineChart);
        lChart.setOnChartGestureListener(this);
        lChart.setOnChartValueSelectedListener(this);
        lChart.setDrawGridBackground(false);
        lChart.getDescription().setEnabled(false);
        lChart.setTouchEnabled(true);
        lChart.setDragEnabled(true);
        lChart.setScaleEnabled(true);
        lChart.setPinchZoom(true);
        lChart.setBackgroundColor(Color.WHITE);
        lChart.getAxisRight().setEnabled(true);
        lChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        lChart.setDrawBorders(true);

        LineMarkerView lmv = new LineMarkerView(this, R.layout.plot_marker,
                bundle.getBoolean("xNumberFormat"), bundle.getBoolean("yNumber1Format"),
                bundle.getBoolean("yNumber2Format"));
        lmv.setChartView(lChart);
        lChart.setMarker(lmv);

        // set up x axis
        int xlimit = (xlimit1 < xlimit2)? xlimit2: xlimit1;
        int xorigin = (xorigin1 < xorigin2)? xorigin2: xorigin1;
        XAxis bottomAxis = lChart.getXAxis();
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setDrawLabels(true);
        bottomAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
        bottomAxis.setAxisMaximum(xlimit);
        bottomAxis.setAxisMinimum(xorigin);
        bottomAxis.enableGridDashedLine(10f, 10f, 0f);
        bottomAxis.setDrawAxisLine(true);
        bottomAxis.setValueFormatter(bundle.getBoolean("xNumberFormat")? numberFormatter: timeFormatter);

        int yleftMin, yleftMax, yrightMin, yrightMax;
        if (singleParm) {                               // compare single parameters for different days
            yleftMax = (ylimit1 < ylimit2)? ylimit2: ylimit1;
            yleftMin = (yorigin1 < yorigin2)? yorigin1: yorigin2;
            yrightMin = yleftMin;
            yrightMax = yleftMax;
        } else if (ylimit3 == Constants.DATA_INVALID) { // 2 parameters
            yleftMin = yorigin1;
            yleftMax = ylimit1;
            yrightMin = yorigin2;
            yrightMax = ylimit2;
        } else {    // 3 parameters
            yleftMax = (ylimit1 < ylimit2)? ylimit2: ylimit1;
            yleftMin = (yorigin1 < yorigin2)? yorigin1: yorigin2;
            yrightMin = yorigin3;
            yrightMax = ylimit3;
        }

        // set up left axis
        YAxis leftAxis = lChart.getAxisLeft();
        leftAxis.setTextColor(Color.BLUE);
        leftAxis.removeAllLimitLines();
        leftAxis.setDrawLabels(true);
        leftAxis.setAxisMaximum(yleftMax);
        leftAxis.setAxisMinimum(yleftMin);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);
        leftAxis.setValueFormatter(bundle.getBoolean("yNumber1Format")? numberFormatter: timeFormatter);

        // build the first dataset y1Data
        LineDataSet y1Data = null;
        final int Y1_COLOR = Color.BLUE;
        if (xdata1 != null && ydata1 != null) {
            int len = xdata1.length;
            ArrayList<Entry> values = new ArrayList<>();
            for (int i = 0; i < len; i++) values.add(new Entry(xdata1[i], ydata1[i]));
            y1Data = new LineDataSet(values, ytitle1);
            y1Data.setAxisDependency(YAxis.AxisDependency.LEFT);
            y1Data.setDrawCircles(false);
            y1Data.setDrawValues(false);
            y1Data.setColor(Y1_COLOR);
            y1Data.setLineWidth(2f);
            //y1Data.enableDashedLine(10f, 5f, 0f);
            //y1Data.enableDashedHighlightLine(10f, 5f, 0f);
            if (Utils.getSDKInt() >= 18 && !tripleParm) { // fill drawable only supported on api level 18 and above
                Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_blue);
                y1Data.setFillDrawable(drawable);
                y1Data.setDrawFilled(true);
            } else {
                y1Data.setFillColor(Y1_COLOR);
            }
        }

        // set up right axis
        final int Y2_COLOR = Color.RED;
        YAxis rightAxis = lChart.getAxisRight();
        rightAxis.setTextColor(Y2_COLOR);
        rightAxis.removeAllLimitLines();
        rightAxis.setDrawLabels(true);
        rightAxis.setAxisMaximum(yrightMax);
        rightAxis.setAxisMinimum(yrightMin);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawZeroLine(true);
        rightAxis.setGranularityEnabled(false);
        rightAxis.setValueFormatter(bundle.getBoolean("yNumber2Format")? numberFormatter: timeFormatter);

        // build the second dataset y2Data
        LineDataSet y2Data = null;
        if (xdata2 != null && ydata2 != null) {
            int len = xdata2.length;
            ArrayList<Entry> values = new ArrayList<>();
            for (int i = 0; i < len; i++) values.add(new Entry(xdata2[i], ydata2[i]));
            y2Data = new LineDataSet(values, ytitle2);
            y2Data.setAxisDependency(tripleParm ? YAxis.AxisDependency.LEFT : YAxis.AxisDependency.RIGHT);
            y2Data.setDrawCircles(false);
            y2Data.setDrawValues(false);
            y2Data.setColor(Y2_COLOR);
            y2Data.setLineWidth(2f);
            //y2Data.enableDashedLine(10f, 5f, 0f);
            //y2Data.enableDashedHighlightLine(10f, 5f, 0f);
            if (Utils.getSDKInt() >= 18 && !tripleParm) { // fill drawable only supported on api level 18 and above
                Drawable drawable = ContextCompat.getDrawable(this, R.drawable.fade_red);
                y2Data.setFillDrawable(drawable);
                y2Data.setDrawFilled(true);
            } else {
                y2Data.setFillColor(Y2_COLOR);
            }
        }

        // build the optional 3 line
        LineDataSet y3Data = null;
        if (xdata3 != null && ydata3 != null && tripleParm) {
            int len = xdata3.length;
            ArrayList<Entry> values = new ArrayList<>();
            for (int i = 0; i < len; i++) values.add(new Entry(xdata3[i], ydata3[i]));
            y3Data = new LineDataSet(values, ytitle3);
            y3Data.setAxisDependency(YAxis.AxisDependency.RIGHT);
            y3Data.setDrawCircles(false);
            y3Data.setDrawValues(false);
            y3Data.setColor(R.color.DarkGreen);
            y3Data.setLineWidth(2f);
            y3Data.setFillColor(R.color.DarkGreen);
        }

        // create a data object with the datasets
        if (y3Data != null)
            lChart.setData(new LineData(y1Data, y2Data, y3Data));
        else
            lChart.setData(new LineData(y1Data, y2Data));

        titleView.setText(plotTitle);
        final int ANIMATE_TIME = 1500; // milliseconds
        lChart.animateX(ANIMATE_TIME);
        Legend l = lChart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
    }

    /*
    private void onPlotClicked(PointF point, Bundle bundle) {
        Intent intent = new Intent(this, PlotInfoActivity.class);
        intent.putExtra("raw_line", bundle.getString("lineTitle1"));
        intent.putExtra("smoothed_line", bundle.getString("lineTitle2"));
        intent.putExtra("average_line", bundle.getString("lineTitle3"));
        intent.putExtra("num_readings", bundle.getInt("num_readings"));
        intent.putExtra("min_battery", bundle.getString("min_battery"));
        intent.putExtra("max_battery", bundle.getString("max_battery"));
        intent.putExtra("used_battery", bundle.getString("used_battery"));
        intent.putExtra("battery_data", bundle.getBoolean("battery_data", false));

        startActivity(intent);
    }
*/
    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
        android.util.Log.i("Gesture", "START, x: " + me.getX() + ", y: " + me.getY());
    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
        android.util.Log.i("Gesture", "END, lastGesture: " + lastPerformedGesture);
    }

    @Override
    public void onChartLongPressed(MotionEvent me) {
        android.util.Log.i("LongPress", "Chart longpressed.");
        showLegend();
    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {
        android.util.Log.i("DoubleTap", "Chart double-tapped.");
    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {
        android.util.Log.i("SingleTap", "Chart single-tapped.");
    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
        android.util.Log.i("Fling", "Chart flinged. VeloX: " + velocityX + ", VeloY: " + velocityY);
    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
        android.util.Log.i("Scale / Zoom", "ScaleX: " + scaleX + ", ScaleY: " + scaleY);
    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {
        android.util.Log.i("Translate / Move", "dX: " + dX + ", dY: " + dY);
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
        android.util.Log.i("Entry selected", e.toString());
        //   Log.i("LOWHIGH", "low: " + lChart.getLowestVisibleX() + ", high: " + lChart.getHighestVisibleX());
        //   Log.i("MIN MAX", "xmin: " + lChart.getXChartMin() + ", xmax: " + lChart.getXChartMax() + ", ymin: " + lChart.getYChartMin() + ", ymax: " + lChart.getYChartMax());
    }

    @Override
    public void onNothingSelected() {
        android.util.Log.i("Nothing selected", "Nothing selected.");
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
/*
        tvX.setText("" + (mSeekBarX.getProgress() + 1));
        tvY.setText("" + (mSeekBarY.getProgress()));

        setLineData(mSeekBarX.getProgress() + 1, mSeekBarY.getProgress());
*/
        // redraw
        //lChart.invalidate();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
    }

    private void showLegend() {
    }

}