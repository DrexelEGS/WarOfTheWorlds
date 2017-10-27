package info.strank.wotw;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;

import net.sf.supercollider.android.ISuperCollider;
import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import java.io.IOException;

/**
 * Responsible for starting stopping and parameterizing the sound generation with SuperCollider.
 * Keep track of wave files used set appropriate parameter values for the synths.
 *
 * Created by strank on 2017-10-19.
 */

public class SoundManager {

    final String LOG_LABEL = "WotW.SoundManager";

    public final double MAX_DISTANCE = 50; // meters
    public final double MIN_DISTANCE = 7.5;
    private final String BKGND_SYNTH_NAME = "sonar";
    private final int BKGND_NODE_ID = 1001;
    private final double BKGND_MIN_AMP = 0.1;
    private final double BKGND_MAX_AMP = 1.3;
    private final String STORY_SYNTH_NAME = "bufSticker";
    private final int STORY_NODE_ID = 1002;
    private final double STORY_MIN_AMP = 1.0;
    private final double STORY_MAX_AMP = 2.3;

    public String currentParamStr = "";
    public ISuperCollider.Stub superCollider;
    public String[] soundFiles = {"1_Chapel_Story.aiff", "2_Curio_Story.aiff","3_Welcome_Story.aiff","4_Bathroom_Story.aiff","5_Synagogue.aiff", "6_Rich.aiff", "7_Maleka.aiff", "8_SoJo_Alisha.aiff"};

    private int bufferIndex = 1;
    private boolean synthsStarted = false;

    public int getCurrentStoryIndex() {
        return bufferIndex;
    }

    public int getStoryCount() {
        return soundFiles.length;
    }

    public void saveStateToPrefs(SharedPreferences.Editor editor) {
        editor.putInt("bufferIndex", bufferIndex);
        editor.putBoolean("synthsStarted", synthsStarted);
    }

    public void setStateFromPrefs(SharedPreferences prefs) {
        synthsStarted = prefs.getBoolean("synthsStarted", false);
        bufferIndex = prefs.getInt("bufferIndex", 1);
    }

    public void startUp(Context context) throws RemoteException {
        // deliver all audio files:
        String soundsDirStr = ScService.getSoundsDirStr(context);
        StringBuilder sb = new StringBuilder();
        try {
            ScService.initDataDir(soundsDirStr);
            String[] filesToDeliver = context.getAssets().list("");
            for (String fileTD : filesToDeliver) {
                if (fileTD.toLowerCase().endsWith(".wav")
                        || fileTD.toLowerCase().endsWith(".aiff")
                        || fileTD.toLowerCase().endsWith(".aif")) {
                    ScService.deliverDataFile(context, fileTD, soundsDirStr);
                    sb.append(fileTD + " ");
                }
            }
            Log.i(LOG_LABEL, "delivered audio files: " + sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (superCollider != null) {
            // basic communication setting for messages to the SC server:
            superCollider.sendMessage(OscMessage.createErrorModeMessage());
            superCollider.sendMessage(OscMessage.createNotifyMessage());
            printMessages();
        }
    }

    public void shutDown() throws RemoteException {
        this.freeSynths();
    }

    protected void printMessages() {
        Thread.yield();
        if (SCAudio.hasMessages()) {
            while (SCAudio.hasMessages()) {
                OscMessage msgFromServer = SCAudio.getMessage();
                Log.d(LOG_LABEL, "OSCMessage received: " + msgFromServer.toString());
            }
        } else {
            Log.d(LOG_LABEL, "No OSCMessages received by SCAudio.");
        }
    }

    public void setupSynths(Context context) throws RemoteException {
        if (superCollider != null) {
            superCollider.sendMessage(OscMessage.createSynthMessage(BKGND_SYNTH_NAME, BKGND_NODE_ID));
            setupStorySynth(context);
            printMessages();
            synthsStarted = true;
            this.setSynthControls(MAX_DISTANCE, 0);
        }
    }

    private void setupStorySynth(Context context) throws RemoteException {
        if (superCollider != null) {
            String soundsDirStr = ScService.getSoundsDirStr(context);
            String soundFile = soundFiles[bufferIndex - 1]; // from 1-based to 0-based as we want bufnum > 0 to be safe
            superCollider.sendMessage(OscMessage.createAllocReadMessage(bufferIndex, soundsDirStr + "/" + soundFile));
            // TODO: maybe we should request a synced response and wait
            // for it before we restart the synth that uses the buffer
            // (but hopefully that will not be necessary)
            superCollider.sendMessage(OscMessage.createSynthMessage(STORY_SYNTH_NAME, STORY_NODE_ID).add("bufnum").add(bufferIndex));
        }
    }

    // return a value between min and max based on amp from 0 to 1
    private double getInterpolated(double rate, double min, double max) {
        return min + rate * (max - min);
    }

    private final long PAN_UPDATE_INTERVAL = 300; // ms
    private long lastTimePanUpdated = 0;
    public double setSynthPan(double targetBearing) throws RemoteException {
        double pan = targetBearing / 180; // -1 left to 1 right
        pan = pan / 2.0; // full left/right seems like too much
        long now = System.currentTimeMillis();
        long timeSinceLastUpdate = now - lastTimePanUpdated;
        if (synthsStarted && superCollider != null && timeSinceLastUpdate > PAN_UPDATE_INTERVAL) {
            //Log.d(LOG_LABEL, "Setting pan " + pan + " for bearing " + targetBearing);
            superCollider.sendMessage(OscMessage.createSetControlMessage(BKGND_NODE_ID, "pan", (float) pan));
            superCollider.sendMessage(OscMessage.createSetControlMessage(STORY_NODE_ID, "pan", (float) pan));
            lastTimePanUpdated = now;
        }
        return pan;
    }

    public boolean setSynthControls(double distance, double targetBearing) throws RemoteException {
        boolean result = false; // did we get close enough?
        if (synthsStarted && superCollider != null) {
            // parameters we want for the used synths:
            // dist : distance to goal, normalized to 0..1 (0 being at goal)
            // amp : volume (default for sonar is 0.2, for story 1)
            //       target a higher amp overall (1.5 for story), amp based on distance,
            //       reverse amp for sonar so it fades when getting closer
            // pan : stereo panorama, between -1 (left ear) and 1 (right ear)
            double dist = 1; // 0 (in goal circle) to 1 (max distance)
            double amp = 0; // 0 to 1 for selecting from a fixed range
            double pan = setSynthPan(targetBearing);
            if (distance < MAX_DISTANCE) {
                if (distance < MIN_DISTANCE) {
                    dist = 0;
                    result = true;
                } else {
                    dist = (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
                }
                amp = 1 - dist;
            }
            superCollider.sendMessage(OscMessage.createSetControlMessage(BKGND_NODE_ID, "dist", (float) dist));
            superCollider.sendMessage(OscMessage.createSetControlMessage(BKGND_NODE_ID, "amp",
                    (float) getInterpolated((1 - amp), BKGND_MIN_AMP, BKGND_MAX_AMP)));
            superCollider.sendMessage(OscMessage.createSetControlMessage(STORY_NODE_ID, "dist", (float) dist));
            superCollider.sendMessage(OscMessage.createSetControlMessage(STORY_NODE_ID, "amp",
                    (float) getInterpolated(amp, STORY_MIN_AMP, STORY_MAX_AMP)));
            // string for debugging display:
            currentParamStr = String.format("Distance %.1fm (%.2f)", distance, dist);
            Log.d(LOG_LABEL, currentParamStr + " pan " + pan + " amp " + amp);
            printMessages();
        }
        return result;
    }

    /**
     * Switch to the next story buffer, loop back on reaching the end of known stories.
     */
    public void switchSynthBuffer(Context context) throws RemoteException{
        if (synthsStarted && superCollider != null) {
            // clean up previous buffer:
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(STORY_NODE_ID));
            superCollider.sendMessage(OscMessage.createBufferFreeMessage(bufferIndex));
            printMessages();
        }
        // switch to new buffer (1-based, while the soundFiles array is 0-based):
        this.bufferIndex++;
        if (this.bufferIndex > soundFiles.length) {
            this.bufferIndex = 1;
        }
        if (synthsStarted && superCollider != null) {
            setupStorySynth(context);
            this.setSynthControls(MAX_DISTANCE, 0);
        }
    }

    public void freeSynths() throws RemoteException {
        if (synthsStarted && superCollider != null) {
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(BKGND_NODE_ID));
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(STORY_NODE_ID));
            superCollider.sendMessage(OscMessage.createBufferFreeMessage(bufferIndex));
            printMessages();
            synthsStarted = false;
        }
    }
}
