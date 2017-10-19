package info.strank.wotw;

import android.content.Context;
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

    final String LOG_LABEL = "WotW.SensorTracking";

    int node = 1001;
    final double MAX_DISTANCE = 100;
    final double MIN_DISTANCE = 10;

    public String currentParamStr = "";
    public ISuperCollider.Stub superCollider;

    public void startUp(Context context) throws RemoteException {
        try {
            // deliver all wav files:
            String soundsDirStr = ScService.getSoundsDirStr(context);
            String[] filesToDeliver = context.getAssets().list("");
            StringBuilder sb = new StringBuilder();
            ScService.initDataDir(soundsDirStr);
            for (String fileTD : filesToDeliver) {
                if (fileTD.toLowerCase().endsWith(".wav")   ) {
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
        printMessages();
    }

    public void printMessages() {
        if(SCAudio.hasMessages()){
            OscMessage receivedMessage = SCAudio.getMessage();
            Log.d(LOG_LABEL, "SC message: " + receivedMessage.get(0).toString());
        }
    }

    public void freeSynths() throws RemoteException {
        superCollider.sendMessage(new OscMessage( new Object[] {"n_free", node}));
        superCollider.sendMessage(new OscMessage( new Object[] {"n_free", node + 1}));
    }

    public void setupSynths(Context context) throws RemoteException {
        //superCollider.sendMessage(new OscMessage( new Object[] {"s_new", "sonar", node, 0, 1}));
        String soundFile = "a11wlk01.wav";
        String synthName = "bufSticker";
        int bufferIndex = 10;
        superCollider.sendMessage(new OscMessage( new Object[] {"b_allocRead", bufferIndex, ScService.getSoundsDirStr(context) + "/" + soundFile}));
        superCollider.sendMessage(new OscMessage( new Object[] {"s_new", synthName, node + 1, 0, 1, "bufnum", bufferIndex}));
        superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node + 1, "dist", 1f}));
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
        superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node, "freq", (float)freq}));
        superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node, "amp", (float)amp}));
        superCollider.sendMessage(new OscMessage( new Object[] {"n_set", node + 1, "dist", (float)scale}));
        // string for debugging display:
        // TODO: check what is actually used so we display something useful here!
        currentParamStr = "Dist " + (float) scale;
        Log.d(LOG_LABEL, currentParamStr);
        printMessages();
        return result;
    }
}
