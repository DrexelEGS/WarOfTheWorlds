package org.sensors2.osc.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import net.sf.supercollider.android.ISuperCollider;
import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLng;


import org.sensors2.common.dispatch.DataDispatcher;
import org.sensors2.common.dispatch.Measurement;
import org.sensors2.common.nfc.NfcActivity;
import org.sensors2.common.sensors.Parameters;
import org.sensors2.common.sensors.SensorActivity;
import org.sensors2.common.sensors.SensorCommunication;
import org.sensors2.osc.R;
import org.sensors2.osc.dispatch.Bundling;
import org.sensors2.osc.dispatch.OscConfiguration;
import org.sensors2.osc.dispatch.OscDispatcher;
import org.sensors2.osc.dispatch.SensorConfiguration;
import org.sensors2.osc.fragments.MultiTouchFragment;
import org.sensors2.osc.fragments.SensorFragment;
import org.sensors2.osc.fragments.StartupFragment;
import org.sensors2.osc.sensors.SensorDimensions;
import org.sensors2.osc.sensors.Settings;
import org.w3c.dom.Text;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StartUpActivity extends FragmentActivity implements OnMapReadyCallback, SensorActivity, NfcActivity, CompoundButton.OnCheckedChangeListener, View.OnTouchListener, LocationListener {

    final String LOG_LABEL = "Location Listener>>";
    int node = 1001;
    double curr_frequency = 400;
    final double MAX_DISTANCE = 100;
    final double MIN_DISTANCE = 7;
    private static final float SHAKE_THRESHOLD = 3.25f; // m/S**2
    private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
    private long mLastShakeTime;
    private boolean listeningForShake = false;
    private LocationManager locationManager;
    private Settings settings;
    private SensorCommunication sensorFactory;
    private OscDispatcher dispatcher;
    private SensorManager sensorManager;
    private PowerManager.WakeLock wakeLock;
    private boolean active;
    private GoogleMap map;
    private Marker marker;
    private StartupFragment startupFragment;
    private SupportMapFragment mapFragment;
    private LatLng exciteLocation = new LatLng(39.9561986, -75.1916809);
    private LatLng currentLocation = exciteLocation;
    private LatLng cornerOfTheatreLocation = new LatLng(39.948306, -75.218923);
    private LatLng curioTheatreLocation = new LatLng(39.948211, -75.218528);
    private LatLng[] testTargetLocations = {new LatLng(39.955796, -75.189654), new LatLng(39.955574, -75.188323), new LatLng(39.953778, -75.187547), new LatLng(39.954079, -75.189731), new LatLng(39.954354, -75.191753)};
    int location_no = 0;
    private LatLng targetLocation  = testTargetLocations[0];
    private ISuperCollider.Stub superCollider;
    private TextView mainWidget = null;
    private ServiceConnection conn = new ScServiceConnection();

    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private NdefMessage mNdefPushMessage;

    public Settings getSettings() {
        return this.settings;
    }

    public ArrayList<String> availableSensors = new ArrayList<>();
    public String[] desiredSensors = {"Orientation", "Accelerometer", "Gyroscope", "Light", "Proximity"};

    @Override
    public void onLocationChanged(Location location) {
        TextView view = (TextView) this.findViewById(R.id.DisplayText);

        Log.d("GPS", LOG_LABEL + "Location Changed");
        if (location != null) {
            view.append(Double.toString(location.getLatitude()));
            double longitude = location.getLongitude();
            Log.d("GPS", LOG_LABEL + "Longitude:" + longitude);
            double latitude = location.getLatitude();
            Toast.makeText(getApplicationContext(), "Freq: "+ curr_frequency, Toast.LENGTH_SHORT).show();
            Log.d("GPS", LOG_LABEL + "Latitude:" + latitude);
            Log.d("GPS", LOG_LABEL + "Latitude:" + latitude);
            if((latitude > 39 && latitude < 41) && (longitude > -76 && longitude < -74))
                currentLocation = new LatLng(latitude, longitude);
        }
        changeSynthFreq();
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public Location LatLngTOLocation(LatLng locationcoords){
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(locationcoords.latitude);
        location.setLongitude(locationcoords.longitude);
        return location;
    }

    private class ScServiceConnection implements ServiceConnection {
        //@Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            StartUpActivity.this.superCollider = (ISuperCollider.Stub) service;
            try {
                //transfer files
                try {
                    // deliver all wav files:
                    String soundsDirStr = ScService.getSoundsDirStr(StartUpActivity.this);
                    String[] filesToDeliver = StartUpActivity.this.getAssets().list("");
                    StringBuilder sb = new StringBuilder();
                    for (String fileTD : filesToDeliver) {
                        if (fileTD.toLowerCase().endsWith(".wav")) {
                            ScService.deliverDataFile(StartUpActivity.this, fileTD, soundsDirStr);
                            sb.append(fileTD + " ");
                        }
                    }
                    Log.i(LOG_LABEL, "delivered wave files: " + sb.toString());
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                // Kick off the supercollider playback routine
                superCollider.start();
                // Start a synth that increases amplitude based on GPS location distance
                //superCollider.sendMessage(new OscMessage( new Object[] {"/s_new", "frequency", OscMessage.defaultNodeId, 0, 1, "freq", 400}));
                if(SCAudio.hasMessages()){
                    OscMessage receivedMessage = SCAudio.getMessage();
                    Log.d(receivedMessage.get(0).toString(), "scydef message");
                }
            } catch (RemoteException re) {
                re.printStackTrace();
            }
        }
        //@Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    /**
     * Provide the glue between the user's greasy fingers and the supercollider's shiny metal body
     * Fix how this gets osc messages
     */
    public void setUpControls() {
        if (mainWidget!=null) mainWidget.setOnTouchListener(new View.OnTouchListener() {
            //@Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction()==MotionEvent.ACTION_UP) {
                    // OSC message right here!
                    OscMessage noteMessage = new OscMessage( new Object[] {
                            "/n_set", OscMessage.defaultNodeId, "amp", 0f
                    });
                    try {
                        // Now send it over the interprocess link to SuperCollider running as a Service
                        superCollider.sendMessage(noteMessage);
                    } catch (RemoteException e) {
                        Toast.makeText(
                                StartUpActivity.this,
                                "Failed to communicate with SuperCollider!",
                                Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                } else if ((event.getAction()==MotionEvent.ACTION_DOWN) || (event.getAction()==MotionEvent.ACTION_MOVE)) {
                    float vol = 1f - event.getY()/mainWidget.getHeight();
                    OscMessage noteMessage = new OscMessage( new Object[] {
                            "/n_set", OscMessage.defaultNodeId, "amp", vol
                    });
                    //float freq = 150+event.getX();
                    //0 to mainWidget.getWidth() becomes sane-ish range of midinotes:
                    float midinote = event.getX() * (70.f / mainWidget.getWidth()) + 28.f;
                    float freq = sc_midicps(Math.round(midinote));
                    OscMessage pitchMessage = new OscMessage( new Object[] {
                            "/n_set", OscMessage.defaultNodeId, "freq", freq
                    });
                    try {
                        superCollider.sendMessage(noteMessage);
                        superCollider.sendMessage(pitchMessage);
                    } catch (RemoteException e) {
                        Toast.makeText(
                                StartUpActivity.this,
                                "Failed to communicate with SuperCollider!",
                                Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                }
                return true;
            }
        });
        try {
            superCollider.openUDP(57110);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    float sc_midicps(float note) {
        return (float) (440.0 * Math.pow((float) 2., (note - 69.0) * (float) 0.083333333333));
    }

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

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BODY_SENSORS};

        if(!hasPermissions(this, PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        setContentView(R.layout.activity_main);
        //mainWidget = new TextView(this); //TODO: Find a way to get rid of this
        bindService(new Intent(this, net.sf.supercollider.android.ScService.class),conn,BIND_AUTO_CREATE);
        this.settings = this.loadSettings();
        this.dispatcher = new OscDispatcher();
        this.sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        this.sensorFactory = new SensorCommunication(this);
        this.wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, this.getLocalClassName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            resolveIntent(getIntent());
            mAdapter = NfcAdapter.getDefaultAdapter(this);
            mPendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            mNdefPushMessage = new NdefMessage(new NdefRecord[]{newTextRecord(
                    "Message from NFC Reader :-)", Locale.ENGLISH, true)});
        }

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        startupFragment = new StartupFragment();
        transaction.add(R.id.container, startupFragment);
        transaction.commit();

        mapFragment = SupportMapFragment.newInstance();
        android.support.v4.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.add(R.id.map, mapFragment);
        fragmentTransaction.commit();

    }

    public List<Parameters> GetSensors(SensorManager sensorManager) {
        List<Parameters> parameters = new ArrayList<>();

        // add Nfc sensor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            mAdapter = NfcAdapter.getDefaultAdapter(this);
            if (mAdapter != null && mAdapter.isEnabled()) {
                parameters.add(new org.sensors2.osc.sensors.Parameters(mAdapter, this.getApplicationContext()));
            }
        }

        // add device sensors
        for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            parameters.add(new org.sensors2.osc.sensors.Parameters(sensor, this.getApplicationContext()));
        }
        return parameters;
    }

    public NfcAdapter getNfcAdapter() {
        return this.mAdapter;
    }

    @TargetApi(10)
    private NdefRecord newTextRecord(String text, Locale locale, boolean encodeInUtf8) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));

        Charset utfEncoding = encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
        byte[] textBytes = text.getBytes(utfEncoding);

        int utfBit = encodeInUtf8 ? 0 : (1 << 7);
        char status = (char) (utfBit + langBytes.length);

        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte) status;
        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }

    @TargetApi(10)
    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;
            if (rawMsgs != null) {
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                byte[] payload = new byte[0];
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
                msgs = new NdefMessage[]{msg};
//                msgs = new NdefMessage[rawMsgs.length];
//                for (int i = 1; i <= rawMsgs.length; i++) {
//                    msgs[i] = (NdefMessage) rawMsgs[i-1];
//                }
            } else {
                // Unknown tag type
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                Parcelable tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                byte[] payload = dumpTagData(tag).getBytes();
                NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
                NdefMessage msg = new NdefMessage(new NdefRecord[]{record});
                msgs = new NdefMessage[]{msg};
            }
            // Setup the views
            for (NdefMessage msg : msgs) {
                if (active) {
                    this.sensorFactory.dispatch(msg);
                }
            }
        }
    }

    @TargetApi(10)
    private String dumpTagData(Parcelable p) {
        StringBuilder sb = new StringBuilder();
        Tag tag = (Tag) p;
        byte[] id = tag.getId();
        sb.append("Tag ID (hex): ").append(getHex(id)).append("\n");
        sb.append("Tag ID (dec): ").append(getDec(id)).append("\n");
        sb.append("ID (reversed): ").append(getReversed(id)).append("\n");

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                MifareClassic mifareTag = MifareClassic.get(tag);
                String type = "Unknown";
                switch (mifareTag.getType()) {
                    case MifareClassic.TYPE_CLASSIC:
                        type = "Classic";
                        break;
                    case MifareClassic.TYPE_PLUS:
                        type = "Plus";
                        break;
                    case MifareClassic.TYPE_PRO:
                        type = "Pro";
                        break;
                }
                sb.append("Mifare Classic type: ");
                sb.append(type);
                sb.append('\n');

                sb.append("Mifare size: ");
                sb.append(mifareTag.getSize());
                sb.append(" bytes");
                sb.append('\n');

                sb.append("Mifare sectors: ");
                sb.append(mifareTag.getSectorCount());
                sb.append('\n');

                sb.append("Mifare blocks: ");
                sb.append(mifareTag.getBlockCount());
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }

        return sb.toString();
    }

    private String getHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append("-");
            }
        }
        return sb.toString();
    }

    private long getDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private long getReversed(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    @Override
    public DataDispatcher getDispatcher() {
        return this.dispatcher;
    }

    @Override
    public SensorManager getSensorManager() {
        return this.sensorManager;
    }

    private Settings loadSettings() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Settings settings = new Settings(preferences);
        OscConfiguration oscConfiguration = OscConfiguration.getInstance();
        oscConfiguration.setHost(settings.getHost());
        oscConfiguration.setPort(settings.getPort());
        return settings;
    }

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


    @Override
    @SuppressLint("NewApi")
    protected void onResume() {
        super.onResume();
        this.loadSettings();
        this.sensorFactory.onResume();
        if (active && !this.wakeLock.isHeld()) {
            this.wakeLock.acquire();
        }

        /**
         * NFC
         */
        if (mAdapter != null) {
            if (mAdapter.isEnabled()) {
                mAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
                mAdapter.enableForegroundNdefPush(this, mNdefPushMessage);
            }

        }
    }

    @Override
    @SuppressLint("NewApi")
    protected void onPause() {
        super.onPause();
        this.sensorFactory.onPause();
        if (this.wakeLock.isHeld()) {
            this.wakeLock.release();
        }

        /**
         * NFC
         */
        if (mAdapter != null) {
            mAdapter.disableForegroundDispatch(this);
            mAdapter.disableForegroundNdefPush(this);
        }
    }

    @Override
    @SuppressLint("NewApi")
    protected void onStop() {
        super.onStop();
        try {
            // Free up audio when the activity is not in the foreground
            // if (superCollider!=null) superCollider.stop();
            this.finish();
        } catch (Exception re) {
            re.printStackTrace();
        }
    }

    public void addSensorFragment(SensorFragment sensorFragment) {
        this.dispatcher.addSensorConfiguration(sensorFragment.getSensorConfiguration());
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && listeningForShake) {
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
                    updateTarget();
                    mLastShakeTime = curTime;
                    Log.d("Shake", "Shake, Rattle, and Roll");
                }
            }
        }
        if (active && !listeningForShake) {
            this.sensorFactory.dispatch(sensorEvent);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // We do not care about that
    }

    /**/
    public void changeSynthAmp(){
        try {
            superCollider.sendMessage(new OscMessage( new Object[] {"/n_free", OscMessage.defaultNodeId}));
            Location current = LatLngTOLocation(currentLocation);
            Location target = LatLngTOLocation(targetLocation);

            double distanceFt = current.distanceTo(target);
            double amp = 0; //setting up minimum aplititude so we always know that the synth is working.
            Log.d(Double.toString(distanceFt), "Amplitude synth");
            if(distanceFt < MAX_DISTANCE){
                if(distanceFt < MIN_DISTANCE)
                    amp = 1;
                else
                    amp = 1 / distanceFt; //a negative slope fuction to ensure smooth increase
            }

            Log.d(Double.toString(amp), "Amplitude synth");
            superCollider.sendMessage(new OscMessage( new Object[] {"/s_new", "amplitude", OscMessage.defaultNodeId, 0, 1, "amp", amp}));
            setUpControls(); // now we have an audio engine, let the activity hook up its controls
            if(SCAudio.hasMessages()){
                OscMessage receivedMessage = SCAudio.getMessage();
                Log.d(receivedMessage.get(0).toString(), "scydef message");
            }
        } catch (RemoteException e){
            e.printStackTrace();
        }
    }

    public void changeSynthFreq(){
        try {
            //ScService.deliverDataFile(StartUpActivity.this, "frequency.scsyndef", ScService.getSynthDefsDirStr(StartUpActivity.this));
            //superCollider.sendMessage(new OscMessage( new Object[] {"/n_free", node}));
            Location current = LatLngTOLocation(currentLocation);
            Location target = LatLngTOLocation(targetLocation);
            double distance = current.distanceTo(target);
            double freq = 200; //setting up minimum aplititude so we always know that the synth is working.
            double scale = 0.1;
            Log.d(Double.toString(distance), "Frequency synth");
            if(distance < MAX_DISTANCE){
                if(distance < MIN_DISTANCE) {
                    freq = 800;
                    scale = 1;
                    initiateShakePopup();
                }
                else {
                    freq = 800 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE)*200; //a negative slope fuction to ensure smooth increase
                    scale = 1.1 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
                }
            }
            Toast.makeText(getApplicationContext(), "Scale: "+ scale, Toast.LENGTH_SHORT).show();
            curr_frequency = freq;

            Log.d(Double.toString(freq), "Frequency synth");
            //superCollider.sendMessage(new OscMessage( new Object[] {"/s_new", "synth1", nodes[node], 0, 1, "freq", (float)freq}));
            superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node, "freq", (float)freq}));
            superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node + 1, "scale", (float)scale}));

            setUpControls(); // now we have an audio engine, let the activity hook up its controls
            if(SCAudio.hasMessages()){
                OscMessage receivedMessage = SCAudio.getMessage();
                Log.d(receivedMessage.get(0).toString(), "scydef message");
            }
        } catch (RemoteException e){
            e.printStackTrace();
        }
    }

    private void updateTarget() {
        location_no++;
        if (location_no > testTargetLocations.length)
            location_no = 0;
        targetLocation = testTargetLocations[location_no];
        addMarkers();
        closeShakePopup();
    }

    private PopupWindow pwindo;

    private void initiateShakePopup() {
        try {
// We need to get the instance of the LayoutInflater
            LayoutInflater inflater = (LayoutInflater) StartUpActivity.this
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.shake_popup,
                    (ViewGroup) findViewById(R.id.popup_element));

            pwindo = new PopupWindow(layout, 300, 370, true);
            pwindo.showAtLocation(layout, Gravity.CENTER, 0, 0);
            // Listen for shakes (For now this is a hacky way of doing it)

            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                listeningForShake = true;
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }

            Button btnClosePopup = (Button) layout.findViewById(R.id.btn_close_popup);
            btnClosePopup.setOnClickListener(cancel_button_click_listener);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeShakePopup(){
        pwindo.dismiss();
    }

    private View.OnClickListener cancel_button_click_listener = new View.OnClickListener() {
        public void onClick(View v) {
            pwindo.dismiss();

        }
    };


      /* New version of onCheckedChanged event listener
    @author: Karishma Changlani
     */

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        View view = compoundButton.getRootView();
        TextView tv = (TextView) view.findViewById(R.id.DisplayText);
        node = 1001;

        for(SensorConfiguration sc : this.dispatcher.getSensorConfigurations()){
            sc.setSend(isChecked);
        }
        if(isChecked){
            tv.setText("Desired Sensors:\n");
            for(String s:desiredSensors){
                tv.append(s + "\n");
            }
            tv.append("\n Available Senors: \n");
            for(String s:availableSensors){
                tv.append(s + "\n");
            }
            mapFragment.getMapAsync(this);
            //MediaPlayer mp = MediaPlayer.create(this, );
            try {

                superCollider.sendMessage(new OscMessage( new Object[] {"s_new", "synth0", node, 0, 1}));
                String soundFile = "a11wlk01.wav";
                String synthName = "PlayABufferScaled";
                int bufferIndex = 10;
                superCollider.sendMessage(new OscMessage( new Object[] {"b_allocRead", bufferIndex, ScService.getSoundsDirStr(StartUpActivity.this) + "/" + soundFile}));
                superCollider.sendMessage(new OscMessage( new Object[] {"s_new", synthName, node + 1, 0, 1, "bufnum", bufferIndex}));
                superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node + 1, "scale", 0.1f}));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        else{
            tv.setText("");
            try {
                superCollider.sendMessage(new OscMessage( new Object[] {"n_free", node}));
                superCollider.sendMessage(new OscMessage( new Object[] {"n_free", node + 1}));
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
        active = isChecked;
    }

    public void addMarkers(){
        map.clear();
        map.addMarker(new MarkerOptions().position(targetLocation).title("Target Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        map.addMarker(new MarkerOptions().position(currentLocation).title("Current Location").draggable(true));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
    }
    public void onMapReady(GoogleMap map){
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        if(this.map == null) {
            this.map = map;
            addMarkers();
        }
        else {
            addMarkers();
        }
    }

    public List<Parameters> getSensors() {
        return sensorFactory.getSensors();
    }

    public void onStartMultiTouch(View view) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.add(R.id.container, new MultiTouchFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(active) {
            int width = v.getWidth();
            int height = v.getHeight();
            for(Measurement measurement : Measurement.measurements(event, width, height)) {
                dispatcher.dispatch(measurement);
            }
        }

        return false;
    }
}
