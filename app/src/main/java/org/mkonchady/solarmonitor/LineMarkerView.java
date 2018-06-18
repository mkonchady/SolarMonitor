package org.mkonchady.solarmonitor;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

/**
 * Custom implementation of the MarkerView.
 *
 * @author Philipp Jahoda
 */
public class LineMarkerView extends com.github.mikephil.charting.components.MarkerView {

    private TextView tvContent;
    private boolean xNumberFormat, yNumber1Format, yNumber2Format;

    public LineMarkerView(Context context, int layoutResource, boolean xNumberFormat,
                          boolean yNumber1Format, boolean yNumber2Format) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent);
        this.xNumberFormat = xNumberFormat;
        this.yNumber1Format = yNumber1Format;
        this.yNumber2Format = yNumber2Format;
    }

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String xvalue = xNumberFormat? UtilsMisc.getDecimalFormat(e.getX(), 1, 1):
                UtilsDate.getTimeDurationHHMMSS( (long) (e.getX()*1000), false);
        String yvalue;
        if (highlight.getAxis().name().equalsIgnoreCase("LEFT"))
            yvalue = yNumber1Format? UtilsMisc.getDecimalFormat(e.getY(), 1, 1):
                                     UtilsDate.getTimeDurationHHMMSS( (long) (e.getY()*1000), false);
        else
            yvalue = yNumber2Format? UtilsMisc.getDecimalFormat(e.getY(), 1, 1):
                                     UtilsDate.getTimeDurationHHMMSS( (long) (e.getY()*1000), false);
        String outline = "X: " + xvalue + " Y: " + yvalue;
        tvContent.setText(outline);
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}
