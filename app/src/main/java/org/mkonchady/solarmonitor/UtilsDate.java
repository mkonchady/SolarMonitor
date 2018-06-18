package org.mkonchady.solarmonitor;

import android.support.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

// Date utilities
public final class UtilsDate {

    final private static Locale locale = Locale.getDefault();
    final static Calendar calendar = Calendar.getInstance();
    final private static String logfileDateFormat = "yyyy-MM-dd";
    final private static String dateFormat = "MMM dd, yyyy";
    final private static String dateTimeFormat = "MMM dd, HH:mm";
    final private static String dateTimeSecFormat = "MMM dd, yyyy HH:mm:ss";
    final private static String hhmmssFormat = "HH:mm:ss";
    final private static String mmssFormat = "mm:ss";
    final private static String hhmmFormat = "HH:mm";
    final private static SimpleDateFormat sdf_logfile_date      = new SimpleDateFormat(logfileDateFormat, locale);
    final private static SimpleDateFormat sdf_date_time = new SimpleDateFormat(dateTimeFormat, locale);
    final private static SimpleDateFormat sdf_date_time_sec = new SimpleDateFormat(dateTimeSecFormat, locale);
    final private static SimpleDateFormat sdf_date      = new SimpleDateFormat(dateFormat, locale);
    final private static SimpleDateFormat sdf_hhmmss = new SimpleDateFormat(hhmmssFormat, locale);
    final private static SimpleDateFormat sdf_mmss = new SimpleDateFormat(mmssFormat, locale);
    final private static SimpleDateFormat sdf_hhmm = new SimpleDateFormat(hhmmFormat, locale);
    final private static SimpleDateFormat detailDateFormat =    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", locale);
    final private static SimpleDateFormat zuluDateFormat =     new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", locale);
    final private static SimpleDateFormat detailDateFormatGMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", locale);

    /**
     * no default constructor
     */
    private UtilsDate() {
        throw new AssertionError();
    }

    static boolean isDate(String dateToValidate) {
        if(dateToValidate == null || dateToValidate.isEmpty()) return false;
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
        sdf.setLenient(false);
        try {
            sdf.parse(dateToValidate);
        } catch (ParseException e) {
            return false;
        }

        return true;
    }

    static Date parseDate(@NonNull String date) {
        try {
            //return new SimpleDateFormat(dateFormat, Locale.getDefault()).parse(date);
            SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            return sdf.parse(date);
        } catch (ParseException e) {
            return null;
        }
    }


    static long getDetailTimeStamp(String text) {
        try {
            Date date = detailDateFormat.parse(text);
            return date.getTime();
        } catch (ParseException pe1) {
            detailDateFormatGMT.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            try {
                Date date = detailDateFormatGMT.parse(text);
                return date.getTime();
            } catch (ParseException pe2) {
                return System.currentTimeMillis();
            }
        }
    }

    static String getZuluDateTimeSec(long millis) {
        zuluDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return (zuluDateFormat.format(millis) + "Z");
    }

    static String getLogfileDateTime(long millis) {
        return (sdf_logfile_date.format(millis));
    }

    static String getLogfileDateTime(String filename, String prefix, String suffix) {
        String str = filename.replaceFirst("^" + prefix, "");
        str = str.replace(suffix, "");
        return (str);
    }

    public static String getDetailDateTimeSec(long millis, int lat, int lon) {
        detailDateFormat.setTimeZone(getTZ(lat, lon));
        return (detailDateFormat.format(millis));
    }

    static String getDateTimeSec(long millis, int lat, int lon) {
        sdf_date_time_sec.setTimeZone(getTZ(lat, lon));
        return (sdf_date_time_sec.format(millis));
    }

    static String getDateTime(long millis, int lat, int lon) {
        sdf_date_time.setTimeZone(getTZ(lat, lon));
        return (sdf_date_time.format(millis));
    }

    static String getDate(long millis, int lat, int lon) {
        sdf_date.setTimeZone(getTZ(lat, lon));
        return (sdf_date.format(millis));
    }

    static String getTime(long millis, int lat, int lon) {
        String dateTime = getDateTime(millis, lat, lon);
        return dateTime.replaceAll("^.*, ", "");
    }

    static int[] getYearMonth(long millis) {
        int[] results = new int[2];
        calendar.setTimeInMillis(millis);
        results[0] = calendar.get(Calendar.YEAR);
        results[1] = calendar.get(Calendar.MONTH);
        return results;
    }

    static int getNumMonthDays(int year, int month) {
        Calendar calendar = new GregorianCalendar(year, month, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    static TimeZone getTZ(int lat, int lon) {
        return (lat == Constants.LARGE_INT)? TimeZone.getDefault():
                TimeZone.getTimeZone(TimezoneMapper.latLngToTimezoneString(lat / Constants.MILLION, lon / Constants.MILLION));
    }

    static TimeZone getTZ(float lat, float lon) {
        return (lat == Constants.LARGE_FLOAT)? TimeZone.getDefault():
                TimeZone.getTimeZone(TimezoneMapper.latLngToTimezoneString(lat, lon));
    }

    // pure time duration must be in GMT timezone
    static String getTimeDurationHHMMSS(long milliseconds, boolean local) {
        return getTimeDurationHHMMSS(milliseconds, local, Constants.LARGE_INT, Constants.LARGE_INT);
    }

    static String getTimeDurationHHMMSS(long milliseconds, boolean local, int lat, int lon) {
        // round up the milliseconds to the nearest second
        long millis = 1000 * ((milliseconds + 500) / 1000);

        // use the shorter format for less than an hour
        if (millis < Constants.MILLISECONDS_PER_MINUTE * 60) {
            if (local) sdf_mmss.setTimeZone(getTZ(lat, lon));
            else       sdf_mmss.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            return (sdf_mmss.format(millis));
        }

        if (local) sdf_hhmmss.setTimeZone(getTZ(lat, lon));
        else       sdf_hhmmss.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
        return (sdf_hhmmss.format(millis));
    }

    // pure time duration must be in GMT timezone
    static String getTimeDurationHHMM(long milliseconds) {
        return getTimeDurationHHMM(milliseconds,false, Constants.LARGE_INT, Constants.LARGE_INT);
    }

    private static String getTimeDurationHHMM(long milliseconds, boolean local, int lat, int lon) {
        // round up the milliseconds to the nearest second
        long millis = 1000 * ((milliseconds + 500) / 1000);

        // use the shorter format for less than an hour
        if (millis < Constants.MILLISECONDS_PER_MINUTE * 60) {
            if (local) sdf_mmss.setTimeZone(getTZ(lat, lon));
            else       sdf_mmss.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
            return (sdf_mmss.format(millis));
        }

        if (local) sdf_hhmm.setTimeZone(getTZ(lat, lon));
        else       sdf_hhmm.setTimeZone(TimeZone.getTimeZone(Constants.UTC_TIMEZONE));
        return (sdf_hhmm.format(millis));
    }

    // convert milliseconds to seconds
    static int getTimeSeconds(long millis) {
        double x = millis / 1000.0;
        return (int) Math.round(x);
    }

    static long get_midNightTimestamp(long millis, double lat, double lon) {
        calendar.setTimeZone(TimeZone.getTimeZone(TimezoneMapper.latLngToTimezoneString(lat, lon)));
        calendar.setTimeInMillis(millis);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH); //here is what you need
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.set(year, month, day, 0, 0, 0);
        long z = calendar.getTimeInMillis();
        return calendar.getTimeInMillis();
    }


    static String getTitle(int month, int year) {
        String title;
        switch (month) {
            case 0: title = "January " + year; break;
            case 1: title = "February " + year; break;
            case 2: title = "March " + year; break;
            case 3: title = "April " + year; break;
            case 4: title = "May " + year; break;
            case 5: title = "June " + year; break;
            case 6: title = "July " + year; break;
            case 7: title = "August " + year; break;
            case 8: title = "September " + year; break;
            case 9: title = "October " + year; break;
            case 10: title = "November "  + year; break;
            default: title = "December "  + year;
        }
        return title;
    }
}
