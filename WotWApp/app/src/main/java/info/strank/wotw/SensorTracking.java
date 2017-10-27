package info.strank.wotw;

import android.hardware.Sensor;
import android.content.SharedPreferences;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.view.Surface;

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
    // specific locations for debugging:
    private static final LatLng exciteLocation = new LatLng(39.9561986, -75.1916809);
    private static final LatLng drexelGymLocation = new LatLng(39.955796, -75.189654);
    private static final LatLng cornerOfTheatreLocation = new LatLng(39.948306, -75.218923);
    private static final LatLng curioTheatreLocation = new LatLng(39.948211, -75.218528);
    /**
     * this is a loop around Calvary:
     type    latitude    longitude    name
     W    39.9483212    -75.2189925    Story 1
     W    39.9480961    -75.2196363    2
     W    39.9475676    -75.2203926    3
     W    39.9470371    -75.2197972    4
     W    39.9474463    -75.2191374    5
     W    39.9477959    -75.2186626    6
     W    39.9481269    -75.2187592    7
     W    39.9483922    -75.2183408    8
     */
    private static final LatLng[] targetLocations = {
            new LatLng(39.9483212,    -75.2189925),
            new LatLng(39.9480961,    -75.2196363),
            new LatLng(39.9475676,    -75.2203926),
            new LatLng(39.9470371,    -75.2197972),
            new LatLng(39.9474463,    -75.2191374),
            new LatLng(39.9477959,    -75.2186626),
            new LatLng(39.9481269,    -75.2187592),
            new LatLng(39.9483922,    -75.2183408),
            drexelGymLocation, // for testing TODO: comment out in final build
    };

    float[] mGravity;
    float[] mGeomagnetic;

    private int location_no = 0;
    public LatLng currentLocation = exciteLocation;
    public float currentBearing = 0; // north, in degrees -180 to +180
    private float idealBearingToTarget = 0; // initial bearing from north in degrees on direct route to target

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
        idealBearingToTarget = current.bearingTo(target);
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

    public void updateBearing(SensorEvent event, int surfaceRotation){
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }
        if (mGravity != null && mGeomagnetic != null) {
            float azimut = 0;
            float rotationMatrix[] = new float[9];
            if (SensorManager.getRotationMatrix(rotationMatrix, null, mGravity, mGeomagnetic)) {
                /* Compensate device orientation */
                // http://android-developers.blogspot.de/2010/09/one-screen-turn-deserves-another.html
                float[] remappedR = new float[9];
                switch (surfaceRotation) {
                    case Surface.ROTATION_0:
                        SensorManager.remapCoordinateSystem(rotationMatrix,
                                SensorManager.AXIS_X, SensorManager.AXIS_Y,
                                remappedR);
                        break;
                    case Surface.ROTATION_90:
                        SensorManager.remapCoordinateSystem(rotationMatrix,
                                SensorManager.AXIS_Y,
                                SensorManager.AXIS_MINUS_X,
                                remappedR);
                        break;
                    case Surface.ROTATION_180:
                        SensorManager.remapCoordinateSystem(rotationMatrix,
                                SensorManager.AXIS_MINUS_X,
                                SensorManager.AXIS_MINUS_Y,
                                remappedR);
                        break;
                    case Surface.ROTATION_270:
                        SensorManager.remapCoordinateSystem(rotationMatrix,
                                SensorManager.AXIS_MINUS_Y,
                                SensorManager.AXIS_X, remappedR);
                        break;
                }
                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(remappedR, orientation);
                azimut = orientation[0]; // from -pi to +pi, 0 is north
            }
            this.currentBearing = azimut * 180 / 3.14159f; // from radians to degrees
        }
    }

    public float getTargetBearing() {
        // degrees to target -180 to +180, TODO: actually clamp to that range
        // 0 we are heading right there, -90 it's to the left
        return idealBearingToTarget - currentBearing;
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
