package org.mkonchady.solarmonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

// miscellaneous utilities
public final class UtilsMisc {

    // Date constants
    //final static private DecimalFormat floatFormat = new DecimalFormat("0.###");
    final static private DecimalFormat doubleFormat = new DecimalFormat("0.######");

    /**
     * no default constructor
     */
    private UtilsMisc() {
        throw new AssertionError();
    }

    //  calculate the percentage given the min. and max.
    static double calcPercentage(float min, float max, float value) {
        double numerator = value - min;
        double denominator = max - min;
        if (denominator <= 0.0) return 0.0;
        return ((100.0 * numerator) / denominator);
    }

    // round down a number based on the number of digits
    // roundToNumber(13256789, 2) = 13256800
    // roundToNumber(13256449, 2) = 13256400
    static int roundToNumber(int num, int digits, boolean ceil) {
        int factor = (int) Math.pow(10, digits);
        num /= factor;
        if (ceil) return (num + 1)* factor;
        return (num * factor);
    }

    // remove outliers
    // limit is 3 times standard deviation to detect outliers
    static int[] removeOutliers(int[] raw_data) {
        double[] data = new double[raw_data.length];
        for (int i = 0; i < raw_data.length; i++) data[i] = raw_data[i];
        double avg_data = getAverage(data);
        double LIMIT = avg_data + 3 * calcStandardDeviation(data);
        for (int i = 0; i < data.length; i++) {
            if (data[i] > LIMIT || data[i] < -LIMIT)
                data[i] = avg_data;
        }
        for (int i = 0; i < raw_data.length; i++) raw_data[i] = (int) Math.round(data[i]);
        return (raw_data);
    }

    static float getValueinRange(float x, float min, float max) {
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    // normalize the float array to a percentage
    static float[] normalize(float[] inValues) {
        if (inValues.length == 0) return inValues;

        // find the min. and max. in array
        float smallest = inValues[0];
        float largest = inValues[0];
        for(int i = 1; i < inValues.length; i++)
            if(inValues[i] > largest) largest = inValues[i];
            else if (inValues[i] < smallest) smallest = inValues[i];

        float range = largest - smallest;
        float[] outValues = new float[inValues.length];
        for (int i = 0; i < inValues.length; i++)
            outValues[i] = 100.0f * ( (inValues[i] - smallest) / range);
        return outValues;
    }


    static int convertToInteger(double dval) {
       Double d = dval;
       return d.intValue();
    }

    static long[] convertLongs(ArrayList<Long> longs) {
        long[] longArray = new long[longs.size()];
        for (int i = 0; i < longArray.length; i++) longArray[i] = longs.get(i).intValue();
        return longArray;
    }

    static int[] convertIntegers(ArrayList<Integer> integers) {
        int[] intArray = new int[integers.size()];
        for (int i = 0; i < intArray.length; i++) intArray[i] = integers.get(i);
        return intArray;
    }

    static float[] convertFloats(ArrayList<Float> floats) {
        float[] floatArray = new float[floats.size()];
        for (int i = 0; i < floatArray.length; i++) floatArray[i] = floats.get(i);
        return floatArray;
    }

    // format a floating point number to x decimal place
    static String formatFloat(float num, int places) {
        return String.format("%." + places + "f", num);
    }

    static String getDecimalFormat(Number n) {
        if (n.intValue() < 10 && n.intValue() > -10)
            return getDecimalFormat(n, 1, 1);
        else
            return getDecimalFormat(n, 0, 1);
    }

    static String getDecimalFormat(Number n, int maxFractionDigits, int minIntegerDigits) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setRoundingMode(RoundingMode.CEILING);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(maxFractionDigits);
        format.setMinimumIntegerDigits(minIntegerDigits);
        return format.format(n);
    }

    static String getDecimalFormat(Number n, int minNumberDigits) {
        NumberFormat format = DecimalFormat.getInstance();
        format.setRoundingMode(RoundingMode.CEILING);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        format.setMinimumIntegerDigits(minNumberDigits);
        return format.format(n).replaceAll(",", "");
    }

    // format a double number to 6 decimal places
    public static String formatDouble(double num, int places) {
        return doubleFormat.format(round(num, places));
    }

    public static double round(double number, int places) {
        double factor = Math.pow(10, places);
        number = Math.round(number * factor);
        number = number / factor;
        return number;
    }

    // return a string of specified length
    public static String fixedLengthString(String string, int length) {
        return String.format("%1$"+length+ "s", string);
    }


    // ---------------Min Max Values ---------------------------------------------------------------

    static float[] getMinMaxGaps(ArrayList<Long> longList) {
        float retFloats[] = {0, 0, 0};
        if (longList.size() < 3) return retFloats;

        long[] nums = convertLongs(longList);
        Arrays.sort(nums);
        long min_gap = Constants.LARGE_INT; long max_gap = 0;
        long sum_gap = 0;
        for (int i = 1; i < nums.length; i++) {
            long gap = nums[i] - nums[i-1];
            sum_gap += gap;
            if (gap < min_gap) min_gap = gap;
            if (gap > max_gap) max_gap = gap;
        }
        float avg_gap = sum_gap / nums.length;
        retFloats[0] = min_gap; retFloats[1] = max_gap; retFloats[2] = avg_gap;
        return retFloats;
    }


    // return the minimum and maximum values of a double array
    public static double[] getMinMax(double[] doubles) {
        double max_value = 0.0f;
        double min_value = Constants.LARGE_FLOAT;
        for (double value: doubles) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new double[] {min_value, max_value};
    }

    static float[] getMinMaxFloat(ArrayList<Float> floatList) {
        float[] nums = convertFloats(floatList);
        return getMinMax(nums);
    }

    // return the minimum and maximum values of a float array
    static float[] getMinMax(float[] floats) {
        float max_value = 0.0f;
        float min_value = Constants.LARGE_FLOAT;
        for (float value: floats) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new float[] {min_value, max_value};
    }

    static float[] getMinMax(float[] floats, float exclude) {
        float max_value = 0.0f;
        float min_value = Constants.LARGE_FLOAT;
        for (float value: floats) {
            if (Float.compare(value, exclude) != 0) {
                if (value > max_value) max_value = value;
                if (value < min_value) min_value = value;
            }
        }
        return new float[] {min_value, max_value};
    }


    static long[] getMinMaxLong(ArrayList<Long> longList) {
        long[] nums = convertLongs(longList);
        return getMinMax(nums);
    }

    // return the minimum and maximum values of an long array
    static long[] getMinMax(long[] longs) {
        long max_value = 0;
        long min_value = Constants.LARGE_INT;
        for (long value: longs) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new long[] {min_value, max_value};
    }

    static int[] getMinMaxInt(ArrayList<Integer> intList) {
        int[] nums = convertIntegers(intList);
        return getMinMax(nums);
    }

    public static int getMin(int[] ints) {
        int[] minMax = getMinMax(ints);
        return minMax[0];
    }

    public static int getMax(int[] ints) {
        int[] minMax = getMinMax(ints);
        return minMax[1];
    }

    static int[] getMinMax(int[] ints, int exclude) {
        int max_value = 0;
        int min_value = Constants.LARGE_INT;
        for (int value: ints) {
            if (value != exclude) { // exclude invalid data
                if (value > max_value) max_value = value;
                if (value < min_value) min_value = value;
            }
        }
        return new int[] {min_value, max_value};
    }

    // return the minimum and maximum values of an int array
    static int[] getMinMax(int[] ints) {
        int max_value = 0;
        int min_value = Constants.LARGE_INT;
        for (int value: ints) {
            if (value > max_value) max_value = value;
            if (value < min_value) min_value = value;
        }
        return new int[] {min_value, max_value};
    }

    //------------------------------------------------------------

    // return the sum of values of a float array
    public static float getSum(float[] floats) {
        float sum = 0.0f;
        for (float value: floats) sum += value;
        return sum;
    }

    // return the sum of values of a float array
    public static float getSum(int[] ints) {
        int sum = 0;
        for (int value: ints) sum += value;
        return sum;
    }

    // calculate the additional range for the plot axes
    public static int calcRange(int val, float factor) {
        if (val <= 0) return 0;
        return (int) Math.ceil(factor * val);
    }

    // calculate the additional range for the plot axes
    public static int calcRange(float val, float factor) {
        if (val <= 0) return 0;
        return (int) Math.ceil(factor * val);
    }

    // return a list of string phone numbers
    public static String[] getPhoneNumbers(String phoneNumString) {
        String[] phoneNums = phoneNumString.split(",");
        for (int i = 0; i < phoneNums.length; i++) phoneNums[i] = phoneNums[i].trim();
        return phoneNums;
    }

    // return an index to implement a circular array
    private static int fix_index(int index, int limit) {
        if (limit < 3) return index;
        if (index == -3) return (limit - 3);
        if (index == -2) return (limit - 2);
        if (index == -1) return (limit - 1);
        if (index == (limit + 1)) return 0;
        if (index == (limit + 2)) return 1;
        if (index == (limit + 3)) return 2;
        return index;
    }

    // return the standard deviation
    public static double calcStandardDeviation(double[] doubles) {

        // calculate the mean
        double sum = 0;
        for (double value: doubles)
            sum += value;
        double mean = (doubles.length > 0)? sum / doubles.length: 0.0;

        // calculate the sum of squares of deviations
        sum = 0;
        for (double value: doubles)
            sum += (value - mean) * (value - mean);

        // calculate the sd
        int denominator = (doubles.length > 1)? (doubles.length - 1): 1;
        return ( Math.sqrt(sum / denominator) ) ;
    }


    static String[] fixNulls(String[] in, String replaceString) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++)
            out[i] = (in[i] == null || in[i].equals("null"))? replaceString: in[i];
        return out;
    }

    // return the average of a int list
    public static float getAverage(int[] nums) {
        if (nums.length == 0) return 0.0f;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (float num: nums) sum += num;
        return (float) (sum / nums.length);
    }

    public static float getAverageInt(ArrayList<Integer> intList) {
        int[] nums = convertIntegers(intList);
        return getAverage(nums);
    }

    // return the average of a long list
    public static float getAverage(long[] nums) {
        if (nums.length == 0) return 0.0f;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (float num: nums) sum += num;
        return (float) (sum / nums.length);
    }

    public static float getAverageLong(ArrayList<Long> longList) {
        long[] nums = convertLongs(longList);
        return getAverage(nums);
    }

    // return the average of a float list
    public static float getAverage(float[] nums) {
        if (nums.length == 0) return 0.0f;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (float num: nums) sum += num;
        return (float) (sum / nums.length);
    }

    public static float getAverageFloat(ArrayList<Float> floatList) {
       float[] nums = convertFloats(floatList);
       return getAverage(nums);
    }

    // return the average of a double list
    private static double getAverage(double[] nums) {
        if (nums.length == 0) return 0.0;
        if (nums.length == 1) return nums[0];
        double sum = 0.0;
        for (double num: nums) sum += num;
        return (sum / nums.length);
    }

    static String[] removeDups(String[] in) {
        return new HashSet<String>(Arrays.asList(in)).toArray(new String[0]);
    }

    // round a long number -- unix timestamp to seconds
    //public static long roundLong(long i) {
    //    i = i + 500L; i = i / 1000L; i = i * 1000L;
    //    return i;
    //}

    // return the center of two points
    public static XYPoint center(XYPoint p1, XYPoint p2) {
        return new XYPoint( (p1.getX() + p2.getX()) / 2.0,
                (p1.getY() + p2.getY()) / 2.0, p1.getColor());
    }


    static Number[] toNumber(double[] x) {
        return toNumber(x, 0);
    }

    static Number[] toNumber(double[] x, double correction) {
        Number[] numberList = new Number[x.length];
        for (int i = 0; i < x.length; i++)
            numberList[i] = (x[i] == Constants.DATA_INVALID)? null: x[i] + correction;
        return numberList;
    }

    static double[] toDouble(Number[] x) {
        double[] d = new double[x.length];
        for (int i = 0; i < x.length; i++) d[i] = (x[i] != null)? x[i].doubleValue(): Constants.DATA_INVALID;
        return d;
    }

    // return an array of partition sizes
    // getPartitions(190, 90, 10) returns [90. 100]
    public static int[] getPartitions(int numElements, int maxSize, int minSize) {
        if (numElements <= maxSize)
            return new int[] {numElements};

        int numPartitions = (int) Math.ceil(1.0 * numElements / maxSize);
        int remainder = numElements % maxSize;
        if (remainder != 0 && remainder <= minSize) numPartitions--;
        int[] partitions = new int[numPartitions];
        int start = 0;
        for (int i = 0; i < numPartitions; i++) {
            // if this is the last partition
            if (i == numPartitions-1)
                partitions[i] = numElements - start;
            else
                partitions[i] = maxSize;
            start += maxSize;
        }

        return partitions;
    }

    // fix the Number array
    // set the first set of zero entries to the first non-zero entry
    // set the last set of zero entries to the last non-zero entry
    // pass:   <20.0, 20.0, 0.0, -10.0, 30.0, null, 20.0, 20.0>,
    // return: <20.0, 20.0, 0.0, -10.0, 30.0, 30.0, 20.0, 20.0>
    static Number[] fix_mid_numbers(Number[] nums) {
        Number last_nonNull = 0.0;
        for (int i = 0; i < nums.length; i++)
            if (nums[i] != null) last_nonNull = nums[i];
            else nums[i] = last_nonNull;
        return nums;
    }

    // fix the Number array
    // set the first set of zero entries to the first non-zero entry
    // set the last set of zero entries to the last non-zero entry
    // pass:   <null, 20.0, 0.0, -10.0, 30.0, null, 20.0, null>,
    // return: <20.0, 20.0, 0.0, -10.0, 30.0, null, 20.0, 20.0>
    static Number[] fix_end_numbers(Number[] nums, boolean positive) {

        // find the first valid non-null value
        float first_num = 0.0f;
        for (Number num : nums) {
            // must be positive
            if ((num != null) && positive && (num.floatValue() > 0.0f)) {
                first_num = num.floatValue();
                break;
            }
            //must be non-zero
            if ((num != null) && !positive && (Float.compare( num.floatValue(), 0.0f) != 0) ) {
                first_num = num.floatValue();
                break;
            }
        }

        // set the first null numbers to the first valid non-null value
        int i = 0;
        while (i < nums.length) {
            if (nums[i] == null) nums[i++] = first_num;
            else i = nums.length;
        }

        // find the last valid non-null number
        float last_num = 0.0f;
        for (i = nums.length - 1; i >= 0; i--) {

            // must be positive
            if ((nums[i] != null) && positive && (nums[i].floatValue() > 0.0f)) {
                last_num = nums[i].floatValue();
                break;
            }
            //must be non-zero
            if ((nums[i] != null) && !positive && (Float.compare( nums[i].floatValue(), 0.0f) != 0) ) {
                last_num = nums[i].floatValue();
                break;
            }
        }

        // set the last null values to the last non-null value
        i = nums.length - 1;
        while (i >= 0) {
            if (nums[i] == null) nums[i--] = last_num;
            else i = -1;
        }

        return nums;
    }

    /*
     * return a linearly interpolated array
     * pass:   <10,0, 20.0, 0.0, -1.0, -1.0, 30.0, -1.0, -1.0, 20.0>, true
     * return: <10.0, 20.0, 22.5, 25.0, 27.5, 30.0, 26.66, 23.33, 20.0>
     *     positive flag to interpolate all non-positive numbers
     */
    static Number[] Interpolate(Number[] numbers, boolean positive) {

        // Verify that there are sufficient elements
        if (numbers.length < 2) {
            numbers[0] = 0;
            return numbers;
        }

        // nullify numbers that are invalid
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] != null) {
                // all numbers must be positive or 0
                if (positive && numbers[i].doubleValue() <= 0.0) numbers[i] = null;
                // all numbers must be positive or negative (i.e. non-zero)
                if (!positive && Double.compare(numbers[i].doubleValue(), 0.0) == 0) numbers[i] = null;
            }
        }

        // verify that the leading and trailing elements of the array contain valid elements
        numbers = fix_end_numbers(numbers, positive);

        // keep track of the ranges where elements are null
        ArrayList<Integer> ranges = new ArrayList<>();
        int step_cnt = 0;
        for (Number number : numbers) {
            if (number == null)  {
                step_cnt++;
            } else {
                if (step_cnt != 0)
                    ranges.add(step_cnt);
                step_cnt = 0;
            }
        }

        int step_index = 0;
        int i = 0;
        ArrayList<Number> interpolated = new ArrayList<>();
        while(i < numbers.length) {
            // add the original elements if non-null
            if (numbers[i] != null)  {
                interpolated.add(numbers[i++]);
            } else {  // otherwise, interpolate between the start and end elements
                int start = i - 1;
                int range = ranges.get(step_index++);
                int end = i + range;
                interpolated.addAll(LinearInterpolate(numbers[start].doubleValue(), numbers[end].doubleValue(), range));
                i += range;
            }
        }

        // convert to a Number array and return
        return interpolated.toArray(new Number[interpolated.size()]);
    }

    /* Linear interpolation using the start & end points and the range
        pass  : start 20.0, end 30.0, range 3 return: <22.5, 25.0, 27.5>
        pass  : start 30.0, end 20.0, range 2 return: <26.6666, 23.3333>
        */
    public static ArrayList<Number> LinearInterpolate(double start, double end, int range) {
        double diff = (end - start) / (range + 1);
        ArrayList<Number> result = new ArrayList<>();
        for (int i = 0; i < range; i++)
            result.add(start + diff * (i + 1));
        return result;
    }

    /*
        p1 : start point p2/p3: control points p4: end point
        B(t) = (1 - t)^3 * P0 + 3(1 - t)^2 * t * P1 + 3(1-t) * t^2 * P2 + t^3 * P3
     */
    public static ArrayList<XYPoint> BezierInterpolate(XYPoint p1, XYPoint p2, XYPoint p3, XYPoint p4) {
        double t;	//the time interval
        double k = 0.025f;	//time step value for drawing curve
        double x1 = p1.x;
        double y1 = p1.y;
        ArrayList<XYPoint> points = new ArrayList<>();
        points.add(new XYPoint(x1, y1 , p1.getColor()));
        for(t = k; t <= 1 + k; t += k) {
            x1 = (p1.x + t * (-p1.x * 3 + t * (3 * p1.x - t * p1.x))) +     // ((1-t)^3 * P1
                    t * (3 * p2.x + t * (-6 * p2.x + p2.x * 3 * t)) +  // 3(1-t)^2 * P2 * t
                    t * t * (p3.x * 3 - p3.x * 3 * t) +                // 3(1-t) * P3 * t^2
                    t * t * t * (p4.x);                                // t^3 * P4
            y1 = (p1.y + t * (-p1.y * 3 + t * (3 * p1.y - t * p1.y))) +
                    t * (3 * p2.y + t * (-6 * p2.y + p2.y * 3 * t)) +
                    t * t * (p3.y * 3 - p3.y * 3 * t) +
                    t * t * t * (p4.y);
            points.add(new XYPoint(x1, y1, p1.getColor()));
        }
        return points;
    }

    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    // use a double quote
    public static String escapeQuotes(String in ) {
        final char quote = '\"';
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            if (in.charAt(i) == quote) {
                stringBuilder.append("\"\"");
            } else {
                stringBuilder.append(in.charAt(i));
            }
        }
        return stringBuilder.toString();
    }

    public static int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return Math.round(100.0f * (level / (float) scale));
        }
        return 100;
    }


    public final static class XYPoint {

        private double x;
        private double y;
        private int color;

        public XYPoint(double x, double y) {
            this(x,y,0);
        }

        public XYPoint(double x, double y, int color) {
            this.x = x; this.y = y; this.color = color;
        }

        public double getX() {
            return x;
        }
        public double getY() {
            return y;
        }
        public int getColor() {
            return color;
        }
        public void setX(double x) {
            this.x = x;
        }
        public void setY(double y) {
            this.y = y;
        }
        public void setXY(double x, double y) {
            this.x = x; this.y = y;
        }
        public void setColor(int color) {
            this.color = color;
        }
        public String toString() {
            return ("X: " + x + " Y: " + y);
        }
    }
}
