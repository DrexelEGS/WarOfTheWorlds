package info.strank.wotw.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.sf.supercollider.android.ISuperCollider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.sensors2.common.dispatch.DataDispatcher;
import org.sensors2.common.sensors.Parameters;
import org.sensors2.common.sensors.SensorActivity;
import org.sensors2.common.sensors.SensorCommunication;
import info.strank.wotw.R;
import info.strank.wotw.dispatch.Bundling;
import info.strank.wotw.dispatch.OscConfiguration;
import info.strank.wotw.dispatch.OscDispatcher;
import info.strank.wotw.dispatch.SensorConfiguration;
import info.strank.wotw.sensors.SensorDimensions;
import info.strank.wotw.sensors.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.strank.wotw.SensorTracking;
import info.strank.wotw.SoundManager;

public class StartUpActivity extends FragmentActivity implements OnMapReadyCallback, SensorActivity, CompoundButton.OnCheckedChangeListener, LocationListener {

    final String LOG_LABEL = "StartUpActivity";
    final String STATUS_TEXT_FORMAT = "%s\t(Story %d out of %d)";
    private static final String PREFS_NAME = "WotWStatePrefs";
    final int MARKER_HEIGHT = 75;
    final int MARKER_WIDTH = 75;
    // WotW specific tracking and sound generation:
    private SensorTracking sensorTracking = new SensorTracking();
    private SoundManager soundManager = new SoundManager();

    private enum State {
        Paused, // initial state, no sensor tracking
        Tracking, // tracking location and updating sound
        Listening, // waiting for shake
        SWITCHING_STATES, // in between states
    }

    private Handler stateSwitchHandler = new Handler();
    private final long SWITCH_DELAY_MS = 500;
    private State state = State.Paused;
    private boolean stopSCService = false;

    private Marker targetMarker;
    private Marker currentMarker;
    private LocationManager locationManager;
    private Settings settings;
    private SensorCommunication sensorFactory;
    private OscDispatcher dispatcher;
    private SensorManager sensorManager;
    private PowerManager.WakeLock wakeLock;
    private GoogleMap map;
    private SupportMapFragment mapFragment;
    private CompoundButton activeButton;
    private TextView statusTextView;
    private ServiceConnection conn = new ScServiceConnection();

    public ArrayList<String> availableSensors = new ArrayList<>();
    public String[] desiredSensors = {"Orientation", "Accelerometer", "Gyroscope", "Light", "Proximity"};

    /**
     * Supercollider service connection handling:
     **/

    private class ScServiceConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(LOG_LABEL, "onServiceConnected: component " + name);
            StartUpActivity.this.soundManager.superCollider = (ISuperCollider.Stub) service;
            try {
                StartUpActivity.this.soundManager.startUp(StartUpActivity.this);
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.d(LOG_LABEL, "onServiceDisconnected: component " + name);
            try {
                StartUpActivity.this.soundManager.shutDown();
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        }
    }

    /**
     * Helper method for checking the permissions we need.
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public Bitmap resizeIcon(String iconName, int width, int height) {
        Bitmap imageBitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return resizedBitmap;
    }

    /**
     * Activity lifecycle management: create - start / restart - resume --- pause - stop - destroy
     **/

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_LABEL, "onCreate ACTIVITY LIFECYCLE");
        super.onCreate(savedInstanceState);
        // make sure we have all permissions needed:
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS};
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
        this.settings = this.loadSettings();
        // setup of location and sensor listening:
        this.locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        this.dispatcher = new OscDispatcher();
        this.sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        this.sensorFactory = new SensorCommunication(this);
        setupSensors();
        // supercollider sound service, first start it and then bind
        // to prevent destroy after unbind:
        Intent serviceIntent = new Intent(this, net.sf.supercollider.android.ScService.class);
        startService(serviceIntent);
        bindService(serviceIntent, conn, BIND_AUTO_CREATE);

        // TODO: review the CPU wake lock:
        this.wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getLocalClassName());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // setup of visuals, request to keep the screen on:
        setContentView(R.layout.activity_start_up);
        activeButton = (ToggleButton) findViewById(R.id.togglewotwButton);
        if (mapFragment == null) {
            FragmentManager fm = getSupportFragmentManager();
            mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.google_map_fragment);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 1, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10, 1, this);
        }
        mapFragment.getMapAsync(this);
        this.restorePrefs();
        activeButton.setOnCheckedChangeListener(this);
        statusTextView = findViewById(R.id.status_text);
        setStatusTextViewText();
    }

    void setStatusTextViewText() {
        if (statusTextView != null) {
            String result = String.format(STATUS_TEXT_FORMAT, this.state,
                    soundManager.getCurrentStoryIndex(),
                    soundManager.getStoryCount());
            statusTextView.setText(result);
        }
    }

    private void restorePrefs() {
        Log.d(LOG_LABEL, "restorePrefs");
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sensorTracking.setStateFromPrefs(settings);
        soundManager.setStateFromPrefs(settings);
        this.state = State.values()[settings.getInt("state", State.Paused.ordinal())];
        activeButton.setChecked(settings.getBoolean("activeChecked", false));
        stopSCService = settings.getBoolean("stopSCService", false);
    }

    private void storePrefs() {
        Log.d(LOG_LABEL, "storePrefs");
        // We need an Editor object to make preference changes.
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("activeChecked", activeButton.isChecked());
        editor.putBoolean("stopSCService", stopSCService);
        editor.putInt("state", this.state.ordinal());
        soundManager.saveStateToPrefs(editor);
        sensorTracking.saveStateToPrefs(editor);
        editor.commit();
    }

    private void resetPrefs() {
        // used to reset the state of the app
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear();
        editor.commit();
    }

    @Override
    @SuppressLint("NewApi")
    protected void onStart() {
        Log.d(LOG_LABEL, "onStart ACTIVITY LIFECYCLE");
        super.onStart();
    }

    @Override
    @SuppressLint("NewApi")
    protected void onRestart() {
        Log.d(LOG_LABEL, "onRestart ACTIVITY LIFECYCLE");
        super.onRestart();
    }

    @Override
    @SuppressLint("NewApi")
    protected void onResume() {
        Log.d(LOG_LABEL, "onResume ACTIVITY LIFECYCLE");
        super.onResume();
        this.sensorFactory.onResume();
        if (this.state != State.Paused && !this.wakeLock.isHeld()) {
            this.wakeLock.acquire();
        }
    }

    @Override
    @SuppressLint("NewApi")
    protected void onPause() {
        Log.d(LOG_LABEL, "onPause ACTIVITY LIFECYCLE");
        super.onPause();
        this.sensorFactory.onPause();
        if (this.wakeLock.isHeld()) {
            this.wakeLock.release();
        }
    }

    @Override
    @SuppressLint("NewApi")
    protected void onStop() {
        Log.d(LOG_LABEL, "onStop ACTIVITY LIFECYCLE");
        super.onStop();
        storePrefs();
    }

    @Override
    @SuppressLint("NewApi")
    protected void onDestroy() {
        Log.d(LOG_LABEL, "onDestroy ACTIVITY LIFECYCLE");
        super.onDestroy();
        this.locationManager.removeUpdates(this);
        unbindService(conn);
        if (stopSCService) {
            stopService(new Intent(this, net.sf.supercollider.android.ScService.class));
        }
    }

    /**
     * State changes:
     */

    private Runnable switchingRunnable = null;
    private void setState(final State nextState) {
        if (switchingRunnable != null) {
            this.stateSwitchHandler.removeCallbacks(switchingRunnable);
        }
        if (this.state == State.Paused || nextState == State.Paused) {
            // switch immediately:
            Log.d(LOG_LABEL, "STATE switching from: " + this.state + " to: " + nextState);
            this.state = nextState;
            setStatusTextViewText();
        } else {
            Log.d(LOG_LABEL, "STATE switching from: " + this.state + " to: " + State.SWITCHING_STATES);
            this.state = State.SWITCHING_STATES;
            switchingRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d(LOG_LABEL, "STATE switching from: " + StartUpActivity.this.state + " to: " + nextState);
                    StartUpActivity.this.state = nextState;
                    StartUpActivity.this.setStatusTextViewText();
                }
            };
            long switchDelay = SWITCH_DELAY_MS;
            if (nextState == State.Listening) {
                switchDelay *= 6; // always wait longer when switching to listening mode
            }
            this.stateSwitchHandler.postDelayed(switchingRunnable, switchDelay);
        }
    }

    private void switchToListening() {
        showShakePopup();
        // Listen for shakes:
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        setState(State.Listening);
    }

    private void switchToNextTarget() {
        this.sensorTracking.switchTargetLocation();
        try {
            this.soundManager.switchSynthBuffer(this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        closeShakePopup();
        setStatusTextViewText();
    }

    private void switchToNextTargetTracking() {
        this.switchToNextTarget();
        setState(State.Tracking);
    }

    private void startStory() {
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        try {
            this.soundManager.setupSynths(this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        stopSCService = false;
        setState(State.Tracking);
    }

    private void stopStory() {
        try {
            this.soundManager.freeSynths();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        setState(State.Paused);
    }

    private void resetStories() {
        stopStory();
        resetPrefs();
        restorePrefs();
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    /**
     *  Mapping interface:
     **/

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && this.state == State.Tracking) {
            double distance = this.sensorTracking.updateDistance(location);
            try {
                if (this.soundManager.setSynthControls(distance)) {
                    switchToListening();
                }
            } catch (RemoteException e){
                e.printStackTrace();
            }
            Toast.makeText(getApplicationContext(), this.soundManager.currentParamStr, Toast.LENGTH_SHORT).show();
        }
        if (this.mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(LOG_LABEL, "onStatusChanged: mapping provider " + provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(LOG_LABEL, "onProviderEnabled: mapping provider " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(LOG_LABEL, "onProviderDisabled: mapping provider " + provider);
    }

    public void addMarkers(){
        float zoom = 16.5f;
        float stroke_width = (new CircleOptions()).getStrokeWidth()/2;
        float bearing = this.sensorTracking.currentBearing;

        map.clear();
        targetMarker = map.addMarker(new MarkerOptions().position(
                this.sensorTracking.getTargetLocation()).title("Target Location").icon(BitmapDescriptorFactory.fromBitmap(resizeIcon("wotw_header", MARKER_WIDTH, MARKER_HEIGHT))));
        currentMarker = map.addMarker(new MarkerOptions().position(
                this.sensorTracking.currentLocation).title("Current Location").flat(true).icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_navigation_black_24dp)).rotation(bearing));

        map.addCircle(new CircleOptions()
                .center(this.sensorTracking.getTargetLocation())
                .radius(this.soundManager.MIN_DISTANCE)
                .strokeColor(Color.RED)
                .fillColor(Color.TRANSPARENT).strokeWidth(stroke_width));
        map.addCircle(new CircleOptions()
                .center(this.sensorTracking.getTargetLocation())
                .radius(this.soundManager.MAX_DISTANCE)
                .strokeColor(Color.BLACK)
                .fillColor(Color.TRANSPARENT).strokeWidth(stroke_width));

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                this.sensorTracking.currentLocation, zoom));
    }

    public void onMapReady(GoogleMap map){
        if(this.map == null) {
            this.map = map;
        }
        addMarkers();
    }

    /**
     *  Management of other sensors:
     **/

    private void setupSensors() {
        for (Parameters parameters : this.getSensors()) {
            info.strank.wotw.sensors.Parameters newParameters = (info.strank.wotw.sensors.Parameters) parameters;
            String name = newParameters.getName();
            this.availableSensors.add(name);
            for (String s : this.desiredSensors) {
                if (name.toLowerCase().contains(s.toLowerCase())) {
                    Bundle args = new Bundle();
                    int dimensions = newParameters.getDimensions();
                    String oscPrefix = newParameters.getOscPrefix();
                    for (Map.Entry<Integer, String> oscSuffix : SensorDimensions.GetOscSuffixes(dimensions).entrySet()) {
                        String direction = oscSuffix.getValue();
                        int i = oscSuffix.getKey();
                        args.putInt(Bundling.SENSOR_TYPE, newParameters.getSensorType());
                        args.putString(Bundling.NAME, direction);
                        args.putString(Bundling.OSC_PREFIX, oscPrefix + direction);
                        args.putInt(Bundling.INDEX, i);
                        args.putString(Bundling.NAME, newParameters.getName());
                        SensorConfiguration sc = new SensorConfiguration();
                        sc.setIndex(args.getInt(Bundling.INDEX, 0));
                        sc.setSensorType(args.getInt(Bundling.SENSOR_TYPE));
                        sc.setOscParam(args.getString(Bundling.OSC_PREFIX));
                        this.dispatcher.addSensorConfiguration(sc);
                    }
                }
            }
        }
    }

    public List<Parameters> GetSensors(SensorManager sensorManager) {
        List<Parameters> parameters = new ArrayList<>();
        // add device sensors
        for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            parameters.add(new info.strank.wotw.sensors.Parameters(sensor, this.getApplicationContext()));
        }
        return parameters;
    }

    @Override
    public DataDispatcher getDispatcher() {
        return this.dispatcher;
    }

    @Override
    public SensorManager getSensorManager() {
        return this.sensorManager;
    }

    public Settings getSettings() {
        return this.settings;
    }

    private Settings loadSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Settings settings = new Settings(preferences);
        OscConfiguration oscConfiguration = OscConfiguration.getInstance();
        oscConfiguration.setHost(settings.getHost());
        oscConfiguration.setPort(settings.getPort());
        return settings;
    }

    public List<Parameters> getSensors() {
        return sensorFactory.getSensors();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (this.state == State.Listening) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                if (this.sensorTracking.checkAccelEvent(sensorEvent)) {
                    switchToNextTargetTracking();
                }
            }
        } else if (this.state == State.Tracking) {
            //just to make sure that updateBearing isn't called for no reason
            if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER || sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                this.sensorTracking.updateBearing(sensorEvent);
                currentMarker.setRotation(this.sensorTracking.currentBearing);
            }
            this.sensorFactory.dispatch(sensorEvent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // We do not care about that
    }

    /**
     * Shake popup handling
     */

    private PopupWindow pwindo;

    private void showShakePopup() {
        try {
            // We need to get the instance of the LayoutInflater
            LayoutInflater inflater = (LayoutInflater) StartUpActivity.this
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.shake_popup,
                    (ViewGroup) findViewById(R.id.popup_element));
            pwindo = new PopupWindow(layout,
                    mapFragment.getView().getWidth() * 3 / 4,
                    mapFragment.getView().getHeight() * 3 / 4, false);
            pwindo.showAtLocation(layout, Gravity.CENTER, 0, 0);
            Button btnClosePopup = (Button) layout.findViewById(R.id.btn_close_popup);
            btnClosePopup.setOnClickListener(cancel_button_click_listener);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeShakePopup() {
        if (pwindo != null) {
            pwindo.dismiss();
        }
    }

    private View.OnClickListener cancel_button_click_listener = new View.OnClickListener() {
        public void onClick(View v) {
            switchToNextTargetTracking();
        }
    };

    /**
     * menu and button interaction:
     **/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.start_up, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_reset: {
                resetStories();
                return true;
            }
            case R.id.action_next_story: {
                switchToNextTarget();
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_guide: {
                Intent intent = new Intent(this, GuideActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.action_about: {
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * New version of onCheckedChanged event listener
     * @author: Karishma Changlani
     */
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        Log.d(LOG_LABEL, "onCheckedChanged " + isChecked);
        for (SensorConfiguration sc : this.dispatcher.getSensorConfigurations()) {
            sc.setSend(isChecked);
        }
        if (isChecked) {
            startStory();
        } else {
            stopStory();
            // only stop the service after tha story has been explicitly stopped:
            stopSCService = true;
        }
    }

}
