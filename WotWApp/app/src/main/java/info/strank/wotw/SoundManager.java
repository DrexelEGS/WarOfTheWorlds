package info.strank.wotw;

import android.content.Context;
import android.nfc.NfcAdapter;
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

    int node = 1001;
    final double MAX_DISTANCE = 100;
    final double MIN_DISTANCE = 10;

    public String currentParamStr = "";
    public ISuperCollider.Stub superCollider;
    public String[] soundFiles = {"1_Chapel_Story.aif", "2_Curio_Story.aif","3_Welcome_Story.aif","4_Bathroom_Story.aif","5_Synagogue.aif", "6_Rich.aif", "7_Maleka.aif", "8_Solo_Alisha.aif"};
    private String synthName;
    private int bufferIndex;

    public SoundManager() {
        bufferIndex = 1;
        synthName = "bufSticker";
    }

    public void startUp(Context context) throws RemoteException {
        try {
            // deliver all wav files:
            String soundsDirStr = ScService.getSoundsDirStr(context);
            String[] filesToDeliver = context.getAssets().list("");
            StringBuilder sb = new StringBuilder();
            ScService.initDataDir(soundsDirStr);
            for (String fileTD : filesToDeliver) {
                if (fileTD.toLowerCase().endsWith(".wav")  ||  fileTD.toLowerCase().endsWith(".aif")) {
                    ScService.deliverDataFile(context, fileTD, soundsDirStr);
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
        // basic communication setting for messages to the SC server:
        superCollider.sendMessage(OscMessage.createErrorModeMessage());
        superCollider.sendMessage(OscMessage.createNotifyMessage());
        printMessages();
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

    public void freeSynths() throws RemoteException {
        superCollider.sendMessage(OscMessage.createNodeFreeMessage(node));
        superCollider.sendMessage(OscMessage.createNodeFreeMessage(node + 1));
        printMessages();
    }

    public void updateBuffer(int newbufIndex) throws RemoteException{
        this.bufferIndex = newbufIndex;
        superCollider.sendMessage(OscMessage.createNodeFreeMessage(node + 1));
        superCollider.sendMessage(OscMessage.createSynthMessage(synthName, node + 1).add("bufnum").add(bufferIndex)); //start with first buffer
        superCollider.sendMessage(OscMessage.createSetControlMessage(node + 1, "dist", 1f));

    }
    // TODO: should probably split out the allocRead setup of the buffer, as that will be
    // re-done when moving targets, and maybe we should request a synced response and wait
    // for it before we restart the synth that uses the buffer
    // (but hopefully that will not be necessary)

    public void setupSynths(Context context) throws RemoteException {
        //String soundFile = soundFiles[0];
        superCollider.sendMessage(OscMessage.createSynthMessage("sonar", node));
        for(int i = 0; i < soundFiles.length; i++) {
            superCollider.sendMessage(OscMessage.createAllocReadMessage(i+1, ScService.getSoundsDirStr(context) + "/" + soundFiles[i]));
        }
        superCollider.sendMessage(OscMessage.createSynthMessage(synthName, node + 1).add("bufnum").add(bufferIndex)); //start with first buffer
        superCollider.sendMessage(OscMessage.createSetControlMessage(node + 1, "dist", 1f));
        printMessages();
    }

    public boolean changeSynth(double distance) throws RemoteException {
        boolean result = false;
        // TODO: need to make this more robust and maybe also take the direction for the sonar?
        // and maybe the synth param calculation and the are-we-close calculation should be split
        double amp = 0.1;
        double freq = 200; //setting up minimum frequency so we always know that the synth is working.
        double scale = 1;
        if(distance < MAX_DISTANCE){
            if(distance < MIN_DISTANCE) {
                freq = 800;
                scale = 0;
                result = true;
            }
            else {
                freq = 800 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE)*200; //a negative slope fuction to ensure smooth increase
                scale = 1.1 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
                amp = 1.1 - (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE);
            }
        }
        superCollider.sendMessage(OscMessage.createSetControlMessage(node, "freq", (float) freq));
        superCollider.sendMessage(OscMessage.createSetControlMessage(node, "amp", (float) amp));
        superCollider.sendMessage(OscMessage.createSetControlMessage(node + 1, "dist", (float) scale));
        // string for debugging display:
        // TODO: check what is actually used so we display something useful here!
        currentParamStr = "Dist " + (float) scale;
        Log.d(LOG_LABEL, currentParamStr);
        printMessages();
        return result;
    }

    public String getSynthName() {
        return synthName;
    }

    public void setSynthName(String synthName) {
        this.synthName = synthName;
    }
}
