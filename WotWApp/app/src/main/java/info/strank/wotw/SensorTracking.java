package info.strank.wotw;

import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

/**
 * Responsible for tracking sensor data as relevant for WotW including
 * smoothing it, limiting the allowed ranges and transforming it to needed
 * values such as target orientation for panning, and distance to target.
 *
 * Created by strank on 2017-10-19.
 */

public class SensorTracking {

    final String LOG_LABEL = "WotW.SensorTracking";

    private static final double MIN_LAT = 39.944933;
    private static final double MAX_LAT = 39.95785;
    private static final double MIN_LNG = -75.227379;
    private static final double MAX_LNG = -75.18528;

    private static final float SHAKE_THRESHOLD = 3.25f; // m/S**2
    private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
    private static final LatLng exciteLocation = new LatLng(39.9561986, -75.1916809);
    private static final LatLng cornerOfTheatreLocation = new LatLng(39.948306, -75.218923);
    private static final LatLng curioTheatreLocation = new LatLng(39.948211, -75.218528);
    private static final LatLng[] testTargetLocations = {new LatLng(39.955796, -75.189654), new LatLng(39.955574, -75.188323), new LatLng(39.953778, -75.187547), new LatLng(39.954079, -75.189731), new LatLng(39.954354, -75.191753)};

    public LatLng currentLocation = exciteLocation;
    int location_no = 0;
    public LatLng targetLocation  = testTargetLocations[0];

    private long mLastShakeTime;
    public boolean listeningForShake = false;

    private Location LatLngTOLocation(LatLng locationcoords){
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(locationcoords.latitude);
        location.setLongitude(locationcoords.longitude);
        return location;
    }

    public double updateDistance(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        String debugStr = "GPS Location Changed. Lat: " + latitude + " Lng: " + longitude;
        if ((latitude > MIN_LAT && latitude < MAX_LAT) && (longitude > MIN_LNG && longitude < MAX_LNG)) {
            currentLocation = new LatLng(latitude, longitude);
        } else {
            debugStr = "OUTSIDE target area: " + debugStr;
        }
        Log.d(LOG_LABEL, debugStr);
        Location current = LatLngTOLocation(currentLocation);
        Location target = LatLngTOLocation(targetLocation);
        return current.distanceTo(target);
    }

    public boolean checkAccelEvent(SensorEvent sensorEvent) {
        if (listeningForShake) {
            long curTime = System.currentTimeMillis();
            if ((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLISECS) {

                float x = sensorEvent.values[0];
                float y = sensorEvent.values[1];
                float z = sensorEvent.values[2];

                double acceleration = Math.sqrt(Math.pow(x, 2) +
                        Math.pow(y, 2) +
                        Math.pow(z, 2)) - SensorManager.GRAVITY_EARTH;
                Log.d("Shake", "Acceleration is " + acceleration + "m/s^2");

                if (acceleration > SHAKE_THRESHOLD) {
                    listeningForShake = false;
                    mLastShakeTime = curTime;
                    Log.d("Shake", "Shake, Rattle, and Roll");
                    location_no++;
                    if (location_no > testTargetLocations.length)
                        location_no = 0;
                    targetLocation = testTargetLocations[location_no];
                    return true;
                }
            }
        }
        return false;
    }

}
