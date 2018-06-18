package org.mkonchady.solarmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static org.mkonchady.solarmonitor.Constants.OPENWEATHER_URL;

final class UtilsNetwork {

    private static final String NO_RESPONSE = "no_response";
    private final static String TAG = "UtilsNetwork";
    /**
     * no default constructor
     */
    private UtilsNetwork() {
        throw new AssertionError();
    }

    // check network connection
    private static boolean isConnected(Context context){
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connMgr != null) {
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            return (networkInfo.isConnected());
        }
        return false;
    }

    // get a list of the web servers on port 80 on the lan
    static void get_lan_servers(Context context, WifiInfo connectionInfo) {
        if (connectionInfo == null) return;
        // get the lan ip address, e.g. 192.168.1.*
        final byte[] ip = toByteIP(connectionInfo.getIpAddress());

        // set up the range of ip addresses to search in parallel
        int start_ip = 2; int end_ip = 254;
        final ArrayList<String> serverIPs = new ArrayList<>();
        final CountDownLatch countDownLatch = new CountDownLatch(end_ip-start_ip);
        for(int i = start_ip; i < end_ip; i++) {
            final int j = i;
            new Thread(new Runnable() {
                public void run() {
                    try {
                        ip[3] = (byte) j;
                        InetAddress address = InetAddress.getByAddress(ip);
                        String stringIP = address.toString().substring(1);
                        //Log.d(TAG, "Checking IP: " + stringIP);
                        if (isPortOpen(stringIP, Constants.PING_TIMEOUT_MILLSECONDS)) {
                            serverIPs.add(stringIP);
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Error in checking IP: " + e.getMessage());
                    }
                    finally {
                        countDownLatch.countDown();
                    }
                } // end of run
            }).start();
        } // end of for


        try {
            countDownLatch.await(Constants.NET_TIMEOUT_MILLSECONDS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Log.d(TAG, "Interrupt exception: " + ie.getMessage());
        } finally {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = pref.edit();
            String []servers = new String[serverIPs.size()];
            serverIPs.toArray(servers);
            String listIPs = TextUtils.join(Constants.DELIMITER, servers);
            Log.d(TAG, "Servers: " + listIPs);
            editor.putString(Constants.LAN_SERVERS, listIPs);
            editor.apply();
        }
    }

    private static boolean isPortOpen(final String ip, final int timeout) {
        final int PORT = 80;
        boolean connected = false;
        Socket socket;
        try {
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(ip, PORT);
            socket.connect(socketAddress, timeout);
            if (socket.isConnected()) {
                connected = true;
                socket.close();
            }
        } catch (IOException e) {
            //Log.d(TAG, " IO Error: " + e.getMessage());
        }
        return connected;
    }

    // check if then envoy server is available at the ip address
    static void isEnvoyAlive(final String ip, final SharedPreferences sp) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                    envoyCheck(ip, sp);
            }
        });
    }

    private static void envoyCheck(final String ENVOY_IP, final SharedPreferences sp) {
        final SharedPreferences.Editor editor = sp.edit();
        Request request1 = new Request.Builder().url("http://" + ENVOY_IP + Constants.ENVOY_PARMS).build();
        final OkHttpClient okClient = new OkHttpClient();
        final OkHttpClient client = okClient.newBuilder()
                .connectTimeout(Constants.NET_TIMEOUT_MILLSECONDS, TimeUnit.MILLISECONDS)
                .readTimeout(Constants.NET_TIMEOUT_MILLSECONDS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build();
        client.newCall(request1).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                int localLog = Integer.parseInt(sp.getString(Constants.DEBUG_MODE, "1"));
                Log.e(TAG, "Call to Envoy cancelled: " + e.getMessage(), localLog);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Envoy Unexpected response: " + response);
                }
                editor.putString(Constants.ENVOY_STATUS, Constants.ENVOY_ALIVE);
                editor.putString(Constants.ENVOY_IP, ENVOY_IP);
                editor.apply();
            }
        });
    }
/*
        try {
            SocketAddress sockaddr = new InetSocketAddress(ip, 80);
            Socket sock = new Socket();
            int timeoutMs = 5000;
            sock.connect(sockaddr, timeoutMs);
            return true;
        } catch(IOException e) {
            return false;
        }
    }
*/

    private static byte[] toByteIP(int addr){
        return new byte[]{(byte)addr,(byte)(addr>>>8),(byte)(addr>>>16),(byte)(addr>>>24)};
    }

    static String buildInitialExtras() {
        String extras = "";
        try {
            JSONObject obj = new JSONObject();
            obj.put("edited", false);
            obj.put("solarmonitor", Constants.VERSION);
            extras = obj.toString();
        } catch (JSONException je)  {
            Log.e(TAG, "JSON formatting error in building initial extras " + je.getMessage());
        }
        return extras;
    }

    // if the extras field has a key solarmonitor, then it is not an external file
    static boolean isImported(String extras) {
        try {
            if (extras.length() > 0) {
                JSONObject mainObject = new JSONObject(extras);
                Iterator<String> keys = mainObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.startsWith("solarmonitor"))
                        return false;
                }
            }
        } catch (JSONException je) {
            Log.d(TAG, "JSON error checking extras for import " + je.getMessage());
        }
        return true;
    }

    static Map<String, String> parseEnvoyJSON(String jsonString) {
        Map<String, String> map = new HashMap<>();
        try {
            // extract the watthours from the Envoy and build the output line
            JSONObject jsonObj = new JSONObject(jsonString);
            String wattHours = jsonObj.get("wattHoursToday") + "";
            map.put("watt_hours", wattHours);
            String wattsNow = jsonObj.get("wattsNow") + "";
            map.put("watts_now", wattsNow);
            String unixTime = Long.toString(System.currentTimeMillis() / 1000L);
            map.put("envoy_timestamp", unixTime);
        } catch (JSONException je) {
            return map;
        }
        return map;
    }

    static Map<String, String> parseOpenWeatherJSON(String jsonString) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);

            // get the weather and description
            JSONArray weather_data = jsonObject.getJSONArray("weather");
            JSONObject jsonObj = weather_data.getJSONObject(0);
            String weather_main = get_value(jsonObj, "main");
            map.put("weather_main", weather_main);
            String weather_description = get_value(jsonObj, "description");
            map.put("weather_description", weather_description);

            // get the temperature
            jsonObj = new JSONObject(jsonObject.getString("main"));
            String temp = get_value(jsonObj, "temp");
            map.put("temp", temp);
            String temp_min = get_value(jsonObj,"temp_min");
            map.put("temp_min", temp_min);
            String temp_max = get_value(jsonObj, "temp_max");
            map.put("temp_max", temp_max);
            String humidity = get_value(jsonObj, "humidity");
            map.put("humidity", humidity);

            // get the wind
            jsonObj = new JSONObject(jsonObject.getString("wind"));
            String wind_speed = get_value(jsonObj, "speed");
            map.put("wind_speed", wind_speed);
            String wind_deg = get_value(jsonObj, "deg");
            map.put("wind_deg", wind_deg);

            // get the clouds
            jsonObj = new JSONObject(jsonObject.getString("clouds"));
            String clouds_all = get_value(jsonObj, "all");
            map.put("clouds_all", clouds_all);

            // get the timestamp
            String unix_timestamp = jsonObject.getString("dt");
            map.put("open_timestamp", unix_timestamp);

            // get the sunset and sunrise
            jsonObj = new JSONObject(jsonObject.getString("sys"));
            int sunrise = Integer.parseInt(get_value(jsonObj, "sunrise"));
            map.put("sunrise_timestamp", sunrise + "");
            int sunset = Integer.parseInt(get_value(jsonObj, "sunset"));
            map.put("sunset_timestamp", sunset + "");

            String name = jsonObject.getString("name");
            map.put("name", name);

        }catch (JSONException je) {
            return map;
        }

        return map;
    }

    // get the current sunrise and sunset from openweather, current lat and lon is known
    // otherwise, place some default values
    static void setSunriseSunset(SharedPreferences sharedPreferences, Context context) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        float f_lat = sharedPreferences.getFloat(Constants.LATITUDE_FLOAT, 0.0f);
        float f_lon = sharedPreferences.getFloat(Constants.LONGITUDE_FLOAT, 0.0f);
        int i_lat = UtilsMisc.convertToInteger(f_lat * Constants.MILLION);
        int i_lon = UtilsMisc.convertToInteger(f_lon * Constants.MILLION);
        editor.putString(Constants.LATITUDE_INT, i_lat + "");
        editor.putString(Constants.LONGITUDE_INT, i_lon + "");
        String LOCATION_PARMS = "&lat=" + f_lat + "&lon=" + f_lon;

        // make a call to openweather to get the sunrise and sunset
        String jsonResponse = getJSON(OPENWEATHER_URL + LOCATION_PARMS, context);
        int localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
        Log.d(TAG, OPENWEATHER_URL + LOCATION_PARMS, localLog);
        long sunrise, sunset;
        if (jsonResponse.equals(UtilsNetwork.NO_RESPONSE)) {
            Log.e(TAG, "Could not get a response from Openweather for request", localLog);
            sunrise = System.currentTimeMillis();
            sunset = System.currentTimeMillis() + 43200L;
        } else {
            Log.d(TAG, "Received response from Openweather", localLog);
            Map<String, String> openWeather_map = UtilsNetwork.parseOpenWeatherJSON(jsonResponse);
            sunrise = Long.parseLong(openWeather_map.get("sunrise_timestamp")) * 1000L;
            sunset = Long.parseLong(openWeather_map.get("sunset_timestamp")) * 1000L;
        }
        editor.putLong(Constants.SUNRISE, sunrise);
        editor.putLong(Constants.SUNSET, sunset);
        editor.apply();
    }


    private static String get_value(JSONObject jsonObject, String key) throws JSONException {
        if (jsonObject.isNull(key))
            return null;
        return jsonObject.getString(key);
    }

    // make a synchronous network call
    private static String getJSON(String url, Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
        if (!isConnected(context)) {
            Log.e(TAG, "No internet connection", localLog);
            return NO_RESPONSE;
        }
        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        builder.url(url);
        Request request = builder.build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        }catch (IOException ie){
            Log.e(TAG, "IO Exception: " + ie.getMessage(), localLog);
        }
        return NO_RESPONSE;
    }

    // submit the request and get a JSON response
    static String oldgetJSON(String address, Context context) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        int localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "1"));
        StringBuilder sb = new StringBuilder();
        int tries = 0;
        HttpURLConnection con = null;
        while (tries < Constants.NET_NUMBER_TRIES) {
            try {
                //HttpURLConnection.setFollowRedirects(false);
                con = (HttpURLConnection) new URL(address).openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout((int) Constants.NET_TIMEOUT_MILLSECONDS); //set timeout
                con.setReadTimeout((int) Constants.NET_TIMEOUT_MILLSECONDS);
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String nextLine;
                    while ((nextLine = reader.readLine()) != null) {
                        sb.append(nextLine);
                    }
                    tries = Constants.NET_NUMBER_TRIES + 1;
                }
            } catch (java.net.SocketTimeoutException se) {
                Log.d(TAG, "Socket error in getting a response " + se.getMessage(), localLog);
            } catch (java.io.IOException ie) {
                Log.d(TAG, "IO error in getting a response " + ie.getMessage(), localLog);
            } finally {
                if (con != null) {
                    con.disconnect();
                    Log.d(TAG, "Disconnect connection", localLog);
                }
                tries = tries + 1;
            }
        }
        if (sb.length() == 0) sb.append(NO_RESPONSE);
        return sb.toString();
    }

}

