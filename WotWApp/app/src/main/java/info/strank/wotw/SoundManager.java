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

    public final double MAX_DISTANCE = 100;
    public final double MIN_DISTANCE = 10;
    private final String BKGND_SYNTH_NAME = "sonar";
    private final int BKGND_NODE_ID = 1001;
    private final String STORY_SYNTH_NAME = "bufSticker";
    private final int STORY_NODE_ID = 1002;

    public String currentParamStr = "";
    public ISuperCollider.Stub superCollider;
    public String[] soundFiles = {"1_Chapel_Story.aiff", "2_Curio_Story.aiff","3_Welcome_Story.aiff","4_Bathroom_Story.aiff","5_Synagogue.aiff", "6_Rich.aiff", "7_Maleka.aiff", "8_SoJo_Alisha.aiff"};
    private String soundsDirStr;

    private int bufferIndex = 1;
    private boolean synthsStarted = false;

    public int getCurrentStoryIndex() {
        return bufferIndex;
    }

    public int getStoryCount() {
        return soundFiles.length;
    }

    public void saveStateToPrefs(SharedPreferences.Editor editor) {
        editor.putString("soundsDirStr", soundsDirStr);
        editor.putInt("bufferIndex", bufferIndex);
        editor.putBoolean("synthsStarted", synthsStarted);
    }

    public void setStateFromPrefs(SharedPreferences prefs) {
        synthsStarted = prefs.getBoolean("synthsStarted", false);
        bufferIndex = prefs.getInt("bufferIndex", 1);
        soundsDirStr = prefs.getString("soundsDirStr", "");
    }

    public void startUp(Context context) throws RemoteException {
        // deliver all audio files:
        this.soundsDirStr = ScService.getSoundsDirStr(context);
        StringBuilder sb = new StringBuilder();
        try {
            ScService.initDataDir(this.soundsDirStr);
            String[] filesToDeliver = context.getAssets().list("");
            for (String fileTD : filesToDeliver) {
                if (fileTD.toLowerCase().endsWith(".wav")
                        || fileTD.toLowerCase().endsWith(".aiff")
                        || fileTD.toLowerCase().endsWith(".aif")) {
                    ScService.deliverDataFile(context, fileTD, this.soundsDirStr);
                    sb.append(fileTD + " ");
                }
            }
            Log.i(LOG_LABEL, "delivered audio files: " + sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // basic communication setting for messages to the SC server:
        superCollider.sendMessage(OscMessage.createErrorModeMessage());
        superCollider.sendMessage(OscMessage.createNotifyMessage());
        printMessages();
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

    public void setupSynths() throws RemoteException {
        superCollider.sendMessage(OscMessage.createSynthMessage(BKGND_SYNTH_NAME, BKGND_NODE_ID));
        setupStorySynth();
        printMessages();
        synthsStarted = true;
        this.setSynthControls(MAX_DISTANCE);
    }

    private void setupStorySynth() throws RemoteException {
        String soundFile = soundFiles[bufferIndex - 1]; // from 1-based to 0-based as we want bufnum > 0 to be safe
        superCollider.sendMessage(OscMessage.createAllocReadMessage(bufferIndex, this.soundsDirStr + "/" + soundFile));
        // TODO: maybe we should request a synced response and wait
        // for it before we restart the synth that uses the buffer
        // (but hopefully that will not be necessary)
        superCollider.sendMessage(OscMessage.createSynthMessage(STORY_SYNTH_NAME, STORY_NODE_ID).add("bufnum").add(bufferIndex));
    }

    public boolean setSynthControls(double distance) throws RemoteException {
        boolean result = false;
        if (synthsStarted) {
            // TODO: need to make this more robust and maybe also take the direction for the sonar?
            // and maybe the synth param calculation and the are-we-close calculation should be split
            double amp = 0.1;
            double freq = 200; //setting up minimum frequency so we always know that the synth is working.
            double scale = 1;
            if (distance < MAX_DISTANCE) {
                if (distance < MIN_DISTANCE) {
                    freq = 800;
                    scale = 0;
                    result = true;
                } else {
                    freq = 800 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE) * 200; //a negative slope fuction to ensure smooth increase
                    scale = 1.1 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
                    amp = 1.1 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
                }
            }
            superCollider.sendMessage(OscMessage.createSetControlMessage(BKGND_NODE_ID, "freq", (float) freq));
            superCollider.sendMessage(OscMessage.createSetControlMessage(BKGND_NODE_ID, "amp", (float) amp));
            superCollider.sendMessage(OscMessage.createSetControlMessage(STORY_NODE_ID, "dist", (float) scale));
            // string for debugging display:
            // TODO: check what is actually used so we display something useful here!
            currentParamStr = "Dist " + (float) scale;
            Log.d(LOG_LABEL, currentParamStr);
            printMessages();
        }
        return result;
    }

    /**
     * Switch to the next story buffer, loop back on reaching the end of known stories.
     */
    public void switchSynthBuffer() throws RemoteException{
        if (synthsStarted) {
            // clean up previous buffer:
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(STORY_NODE_ID));
            superCollider.sendMessage(OscMessage.createBufferFreeMessage(bufferIndex));
            printMessages();
            // switch to new buffer (1-based, while the soundFiles array is 0-based):
            this.bufferIndex++;
            if (this.bufferIndex > soundFiles.length) {
                this.bufferIndex = 1;
            }
            setupStorySynth();
            this.setSynthControls(MAX_DISTANCE);
        }
    }

    public void freeSynths() throws RemoteException {
        if (synthsStarted) {
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(BKGND_NODE_ID));
            superCollider.sendMessage(OscMessage.createNodeFreeMessage(STORY_NODE_ID));
            superCollider.sendMessage(OscMessage.createBufferFreeMessage(bufferIndex));
            printMessages();
            synthsStarted = false;
        }
    }
}
