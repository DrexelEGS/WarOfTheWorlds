package net.sf.supercollider.android.test;

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
public class ScServiceAudioTests {

	protected static final String TAG = "ScServiceAudioTests";

	@Rule
	public final ServiceTestRule serviceRule = new ServiceTestRule();

	private Context context;

	@Test
	public void testScServiceUsage() throws TimeoutException {
		context = InstrumentationRegistry.getTargetContext();
		// Create the service Intent.
		Intent serviceIntent = new Intent(context, ScService.class);
		// Bind the service and grab a reference to the binder.
		IBinder binder = serviceRule.bindService(serviceIntent);
		Log.d(TAG, "Service bound: " + binder);
		ISuperCollider.Stub servStub = (ISuperCollider.Stub) binder;
		// Check the default file was copied:
		String fileToCheck = "default.scsyndef";
		String synthDefsDirStr = ScService.getSynthDefsDirStr(context);
		assertTrue("Failed to find default file copied: " + fileToCheck,
				Arrays.asList(new File(synthDefsDirStr).list()).contains(fileToCheck));
		try {
			// start it manually (which is unusual for bound services, but that seems to be necessary:
			servStub.start();
			// Check it's working:
			servStub.sendMessage(new OscMessage(new Object[] {"/s_new", "default", OscMessage.defaultNodeId}));
			Thread.sleep(1000);
			servStub.sendMessage(new OscMessage(new Object[] {"/n_free", OscMessage.defaultNodeId}));
			initWavFile();
			int bufferIndex = 10;
			servStub.sendMessage(new OscMessage(new Object[] {"/b_allocRead", bufferIndex, ScService.getSoundsDirStr(context) + "/a11wlk01.wav"}));
			Thread.sleep(1000);
			servStub.sendMessage(new OscMessage(new Object[] {"/n_free", OscMessage.defaultNodeId}));
			//servStub.sendMessage(new OscMessage(new Object[] {"/b_free", bufferIndex}));
		} catch (RemoteException e) {
			e.printStackTrace();
		    assertTrue("RemoteException caught!", false);
		} catch (InterruptedException e) {
			e.printStackTrace();
			assertTrue("InterruptedException caught!", false);
		}
		assertTrue("SCAudio has no messages!", SCAudio.hasMessages());
	}

	protected boolean initWavFile() {
		try {
			String fileToDeliver = "a11wlk01.wav";
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
