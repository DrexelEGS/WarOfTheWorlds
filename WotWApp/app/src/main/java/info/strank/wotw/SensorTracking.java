package info.strank.wotw;

import android.hardware.Sensor;
import android.content.SharedPreferences;
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

    private static final float SHAKE_THRESHOLD = 6 * 3.25f; // m/S**2
    private static final LatLng exciteLocation = new LatLng(39.9561986, -75.1916809);
    private static final LatLng cornerOfTheatreLocation = new LatLng(39.948306, -75.218923);
    private static final LatLng curioTheatreLocation = new LatLng(39.948211, -75.218528);
    private static final LatLng[] targetLocations = {new LatLng(39.955796, -75.189654), new LatLng(39.955574, -75.188323), new LatLng(39.953778, -75.187547), new LatLng(39.954079, -75.189731), new LatLng(39.954354, -75.191753)};

    float[] mGravity;
    float[] mGeomagnetic;

    private int location_no = 0;
    public LatLng currentLocation = exciteLocation;
    public float currentBearing = 0;

    private KalmanLatLong kalmanLatLong = new KalmanLatLong(3f); // for filtering GPS, moving 3m/s


    public void saveStateToPrefs(SharedPreferences.Editor editor) {
        editor.putInt("location_no", location_no);
    }

    public void setStateFromPrefs(SharedPreferences prefs) {
        location_no = prefs.getInt("location_no", 0);
    }

    private Location LatLngTOLocation(LatLng locationcoords){
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(locationcoords.latitude);
        location.setLongitude(locationcoords.longitude);
        return location;
    }

    public double updateDistance(Location location) {
        kalmanLatLong.Process(location.getLatitude(), location.getLongitude(),
                location.getAccuracy(), location.getTime());
        double latitude = kalmanLatLong.get_lat();
        double longitude = kalmanLatLong.get_lng();
        String debugStr = "GPS Location Changed. Lat: " + latitude + " Lng: " + longitude;
        if ((latitude > MIN_LAT && latitude < MAX_LAT) && (longitude > MIN_LNG && longitude < MAX_LNG)) {
            currentLocation = new LatLng(latitude, longitude);
        } else {
            debugStr = "OUTSIDE target area: " + debugStr;
        }
        Log.d(LOG_LABEL, debugStr);
        Location current = LatLngTOLocation(currentLocation);
        Location target = LatLngTOLocation(getTargetLocation());
        return current.distanceTo(target);
    }

    public boolean checkAccelEvent(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        double acceleration = Math.sqrt(Math.pow(x, 2) +
                Math.pow(y, 2) +
                Math.pow(z, 2)) - SensorManager.GRAVITY_EARTH;
        if (acceleration > SHAKE_THRESHOLD) {
            Log.d(LOG_LABEL, "Shake Acceleration is " + acceleration + "m/s^2");
            return true;
        }
        return false;
    }

    public void updateBearing(SensorEvent event){
        float azimut = 0;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0];
            }
        }
        float bearing = azimut * 360 / (2 * 3.14159f);
        this.currentBearing = bearing;
    }

    public void switchTargetLocation() {
        location_no++;
        if (location_no >= targetLocations.length) {
            location_no = 0;
        }
    }

    public LatLng getTargetLocation() {
        return targetLocations[location_no];
    }

    /**
     * Simplified Kalman filtering for latitude/longitude values with timestamp and accuracy
     * for filtering/smoothing GPS data. Courtesy of
     * https://stackoverflow.com/questions/1134579/smooth-gps-data
     */
    public class KalmanLatLong {
        private final float MinAccuracy = 1;

        private float Q_metres_per_second;
        private long TimeStamp_milliseconds;
        private double lat;
        private double lng;
        private float variance; // P matrix.  Negative means object uninitialised.  NB: units irrelevant, as long as same units used throughout

        public KalmanLatLong(float Q_metres_per_second) {
            this.Q_metres_per_second = Q_metres_per_second;
            variance = -1;
        }

        public long get_TimeStamp() {
            return TimeStamp_milliseconds;
        }

        public double get_lat() {
            return lat;
        }

        public double get_lng() {
            return lng;
        }

        public float get_accuracy() {
            return (float) Math.sqrt(variance);
        }

        public void SetState(double lat, double lng, float accuracy, long TimeStamp_milliseconds) {
            this.lat = lat;
            this.lng = lng;
            variance = accuracy * accuracy;
            this.TimeStamp_milliseconds = TimeStamp_milliseconds;
        }

        /// <summary>
        /// Kalman filter processing for lattitude and longitude
        /// </summary>
        /// <param name="lat_measurement_degrees">new measurement of lattidude</param>
        /// <param name="lng_measurement">new measurement of longitude</param>
        /// <param name="accuracy">measurement of 1 standard deviation error in metres</param>
        /// <param name="TimeStamp_milliseconds">time of measurement</param>
        /// <returns>new state</returns>
        public void Process(double lat_measurement, double lng_measurement, float accuracy, long TimeStamp_milliseconds) {
            if (accuracy < MinAccuracy) accuracy = MinAccuracy;
            if (variance < 0) {
                // if variance < 0, object is unitialised, so initialise with current values
                this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                lat = lat_measurement;
                lng = lng_measurement;
                variance = accuracy * accuracy;
            } else {
                // else apply Kalman filter methodology

                long TimeInc_milliseconds = TimeStamp_milliseconds - this.TimeStamp_milliseconds;
                if (TimeInc_milliseconds > 0) {
                    // time has moved on, so the uncertainty in the current position increases
                    variance += TimeInc_milliseconds * Q_metres_per_second * Q_metres_per_second / 1000;
                    this.TimeStamp_milliseconds = TimeStamp_milliseconds;
                    // TO DO: USE VELOCITY INFORMATION HERE TO GET A BETTER ESTIMATE OF CURRENT POSITION
                }

                // Kalman gain matrix K = Covarariance * Inverse(Covariance + MeasurementVariance)
                // NB: because K is dimensionless, it doesn't matter that variance has different units to lat and lng
                float K = variance / (variance + accuracy * accuracy);
                // apply K
                lat += K * (lat_measurement - lat);
                lng += K * (lng_measurement - lng);
                // new Covarariance  matrix is (IdentityMatrix - K) * Covarariance
                variance = (1 - K) * variance;
            }
        }
    }
}
