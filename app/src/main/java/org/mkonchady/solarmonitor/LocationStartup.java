package org.mkonchady.solarmonitor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;


// get the first location as fast as possible
public class LocationStartup implements
        com.google.android.gms.location.LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private boolean freshLocation = false;
    private static final long FASTEST_INTERVAL = 1000;         // 1 second
    private static final long INTERVAL = 1000 * 10;            // 10 seconds
    private static final long FRESH_LOC_PERIOD = Constants.MILLISECONDS_PER_MINUTE * 2;     // 2 minutes
    int localLog = 0;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    Context context = null;
    private Activity mainActivity = null;
    private Location mCurrentLocation = null;
    public final String TAG = "LocationStartup";

    LocationStartup(Context context, Activity mainActivity) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        localLog = Integer.parseInt(sharedPreferences.getString(Constants.DEBUG_MODE, "0"));
        Log.d(TAG, "Create LocationStartup ...", localLog);
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        this.context = context;
        this.mainActivity = mainActivity;

        // set the parameters for the first location request
        mLocationRequest = new LocationRequest()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(0)
                .setInterval(INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

    }

    void startConnection() {
        Log.d(TAG, "start fired ...", localLog);
        mGoogleApiClient.connect();
    }

    void stopConnection() {
        Log.d(TAG, "stop fired ...", localLog);
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
            mGoogleApiClient.disconnect();
        }
        Log.d(TAG, "isConnected : " + mGoogleApiClient.isConnected(), localLog);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected - isConnected : " + mGoogleApiClient.isConnected(), localLog);
        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectedSuspended - isConnected : " + " i " +
                mGoogleApiClient.isConnected(), localLog);
    }

    @Override
    public void onConnectionFailed(@NonNull  ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed: " + connectionResult.toString(), localLog);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Firing onLocationChanged...", localLog);
        long locationTime = System.currentTimeMillis() -
             ((location == null) ? 0L : location.getTime());
        freshLocation = (locationTime < FRESH_LOC_PERIOD);
        mCurrentLocation = location;
    }

    protected void startLocationUpdates() {
        final int MY_PERMISSIONS_FINE_LOCATION = 10;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mainActivity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_FINE_LOCATION);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        Log.d(TAG, "Location update started ... ", localLog);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this);
        Log.d(TAG, "Location update stopped ...", localLog);
    }

    boolean noInitialCoordinates() {
        return (mCurrentLocation == null);
    }

    void setmCurrentLocation(Location location) {
        mCurrentLocation = location;
    }

    public Location getLocation() {
        return  mCurrentLocation;
    }

    boolean isFreshLocation() {
        return freshLocation;
    }
}