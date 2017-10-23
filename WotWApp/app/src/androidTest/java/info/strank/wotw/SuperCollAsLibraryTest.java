package info.strank.wotw;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import net.sf.supercollider.android.ISuperCollider;
import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

/**
 * Quick test to make sure using the ScService gives the same results as
 * tested for in NativeAudioTest.
 *
 * You can also use this code as an example for interacting with the ScService
 *
 * @author strank
 *
 */

@RunWith(AndroidJUnit4.class)
public class SuperCollAsLibraryTest {

    protected static final String TAG = "SuperCollAsLibraryTest";

    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    private Context context;

    @Test
    public void testScServiceFromApp() throws TimeoutException {
        context = InstrumentationRegistry.getTargetContext();
        // Create the service Intent.
        Intent serviceIntent = new Intent(context, ScService.class);
        // Bind the service and grab a reference to the binder.
        IBinder binder = serviceRule.bindService(serviceIntent);
        Log.d(TAG, "Service bound: " + binder);
        ISuperCollider.Stub servStub = (ISuperCollider.Stub) binder;

        String soundFile = "aaa.wav";
        initWavFile(soundFile);
        // Check the scsyndef file was copied:
        String synthName = "PlayABuffer";
        String fileToCheck = synthName + ".scsyndef";
        String synthDefsDirStr = ScService.getSynthDefsDirStr(context);
        assertTrue("Failed to find default file copied: " + fileToCheck,
                Arrays.asList(new File(synthDefsDirStr).list()).contains(fileToCheck));

        try {
            servStub.sendMessage(OscMessage.createErrorModeMessage());
            servStub.sendMessage(OscMessage.createNotifyMessage());
            printMessages();
            // Check it's working:
            servStub.sendMessage(OscMessage.createSynthMessage("default", OscMessage.defaultNodeId));
            Thread.sleep(1000);
            servStub.sendMessage(OscMessage.createNodeFreeMessage(OscMessage.defaultNodeId));
            servStub.sendMessage(OscMessage.createSyncMessage());
            int bufferIndex = 10;
            servStub.sendMessage(OscMessage.createAllocReadMessage(bufferIndex, ScService.getSoundsDirStr(context) + "/" + soundFile));
            //servStub.sendMessage(new OscMessage(new Object[] {"/b_alloc", bufferIndex, 44100 * 3}));
            //servStub.sendMessage(new OscMessage(new Object[] {"/b_read", bufferIndex, ScService.getSoundsDirStr(context) + "/" + soundFile, 0, 10000}));

            servStub.sendMessage(OscMessage.createSyncMessage());
            printMessages();
            Thread.sleep(1000);
            printMessages();
            servStub.sendMessage(new OscMessage(new Object[] {"/b_query", bufferIndex}));
            servStub.sendMessage(new OscMessage(new Object[] {"/b_getn", bufferIndex, 100, 100}));
            Thread.sleep(1000);
            printMessages();
            servStub.sendMessage(OscMessage.createSynthMessage(synthName, OscMessage.defaultNodeId).add("bufnum").add(bufferIndex));
            servStub.sendMessage(OscMessage.createSyncMessage());
            Thread.sleep(3000);
            printMessages();
            servStub.sendMessage(OscMessage.createNodeFreeMessage(OscMessage.defaultNodeId));
            servStub.sendMessage(OscMessage.createSyncMessage());
            printMessages();
            servStub.sendMessage(OscMessage.createBufferFreeMessage(bufferIndex));
            servStub.sendMessage(OscMessage.createSyncMessage());
        } catch (RemoteException e) {
            e.printStackTrace();
            assertTrue("RemoteException caught!", false);
        } catch (InterruptedException e) {
            e.printStackTrace();
            assertTrue("InterruptedException caught!", false);
        }
        //assertTrue("SCAudio has no messages!", SCAudio.hasMessages());
    }

    protected void printMessages() {
        Thread.yield();
        if (SCAudio.hasMessages()) {
            while (SCAudio.hasMessages()) {
                OscMessage msgFromServer = SCAudio.getMessage();
                Log.d(TAG, "OSCMessage: " + msgFromServer.toString());
            }
        } else {
            Log.d(TAG, "No messages in SCAudio");
        }
    }

    protected boolean initWavFile(String fileToDeliver) {
        try {
            String soundsDirStr = ScService.getSoundsDirStr(context);
            ScService.initDataDir(soundsDirStr);
            ScService.deliverDataFile(context, fileToDeliver, soundsDirStr);
            assertTrue("Failed copying " + fileToDeliver,
                    Arrays.asList(new File(soundsDirStr).list()).contains(fileToDeliver));
            Log.d("initFiles", "copied " + fileToDeliver + " to " + soundsDirStr);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}