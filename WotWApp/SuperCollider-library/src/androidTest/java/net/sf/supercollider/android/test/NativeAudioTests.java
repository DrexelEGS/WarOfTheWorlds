package net.sf.supercollider.android.test;

import java.io.File;
import java.util.Arrays;

import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.Manifest;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.v4.content.ContextCompat;

/**
 * Essentially these are smoketests, there is no validation step for a lot
 * of the code here, barring "are we still alive?"  Look at the tests in the
 * suite, run them, listen to the audio.  You should hear a series of beeps
 * and squeaks corresponding to the tests, if they sound loosely melodic and
 * there are no crashes, then good.
 * 
 * You can also use this code as an example when instantiating your own synthdefs
 * 
 * @TODO: make validation automatic
 * 
 * @author alex
 *
 */

@RunWith(AndroidJUnit4.class)
public class NativeAudioTests {

	protected static final String TAG = "NativeAudioTests";
	final int numInChans = 0;
	final int numOutChans = 1; 
	final int shortsPerSample = 1; 
	final int bufSizeFrames = 64*64;
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 
	int sampleRateInHz = 44100;
	short[] audioBuf = new short[bufSizeShorts];

	// This would be needed, if we would use external storage. However, external storage is
	// available by default in later android, and when using the app-specific external folders
	// and doesn't seem to work at all for generic external folders, so not necessary anymore.
	//@Rule
	//public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

	@Test
	public void testSynthDefs() {
		int permissionCheck = ContextCompat.checkSelfPermission(getContext(),
				Manifest.permission.WRITE_EXTERNAL_STORAGE);
		Log.d(TAG, "permission external write (not actually needed above sdk 23): "
				+ (permissionCheck == PackageManager.PERMISSION_GRANTED));
		//assertEquals("Permission not granted!", permissionCheck, PackageManager.PERMISSION_GRANTED);
		boolean retValBool = initFiles();
		assertTrue("initFiles failed!", retValBool);
    	System.loadLibrary("sndfile");
    	System.loadLibrary("scsynth"); 
    	SCAudio.scsynth_android_initlogging();
		int retVal = SCAudio.scsynth_android_start(sampleRateInHz, bufSizeFrames, numInChans, numOutChans, shortsPerSample,
				ScService.getPluginsDirStr(getContext()), ScService.getSynthDefsDirStr(getContext()));
    	assertEquals(retVal, 0); // SC started, have a biscuit
    	///////////////////////////////////////////////////////////////////////
    	// Silence is golden
		assertEquals(0, SCAudio.scsynth_android_genaudio(audioBuf));
		for (short s : audioBuf) { assertEquals(s, 0); }
		int buffersPerSecond = (sampleRateInHz*shortsPerSample)/(bufSizeShorts*numOutChans);
		AudioTrack audioTrack = createAudioOut(); // audible testing
		try {
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"error", 1});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"notify", 1});
			Thread.yield(); printMessages();
	    	///////////////////////////////////////////////////////////////////////
	    	// test default.scsyndef
			//SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "default", OscMessage.defaultNodeId});

			//for(int i=0; i<buffersPerSecond; ++i) {
			//	SCAudio.scsynth_android_genaudio(audioBuf);
			//	audioTrack.write(audioBuf, 0, bufSizeShorts);
			//}
			//SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			SCAudio.scsynth_android_doOsc(new Object[] {"g_new", 1, 0, 0});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"sync"});
			Thread.yield(); printMessages();

			///////////////////////////////////////////////////////////////////////
	    	// Test buffers
			int bufferIndex = 10;
			//SCAudio.scsynth_android_doOsc(new Object[] {"b_allocRead", bufferIndex, ScService.getSoundsDirStr(getContext()) + "/aaa.wav"});
			SCAudio.scsynth_android_doOsc(new Object[] {"b_alloc", bufferIndex, 44100 * 3});
			Thread.yield(); printMessages();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//SCAudio.scsynth_android_doOsc(new Object[] {"b_gen", bufferIndex, "sine1", 1, 1.0f, 0.5f, 0.3f});
			SCAudio.scsynth_android_doOsc(new Object[] {"b_read", bufferIndex, ScService.getSoundsDirStr(getContext()) + "/aaa.au", 0, 10000});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"sync"});
			Thread.yield(); printMessages();

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			Thread.yield(); printMessages();

			SCAudio.scsynth_android_doOsc(new Object[] {"b_query", bufferIndex});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"b_getn", bufferIndex, 100, 100});
			Thread.yield(); printMessages();

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			Thread.yield(); printMessages();

			SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "PlayABuffer", OscMessage.defaultNodeId, 0, 1, "bufnum", bufferIndex});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"sync"});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"n_run", OscMessage.defaultNodeId, 1});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"sync"});
			Thread.yield(); printMessages();

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			Thread.yield(); printMessages();

			SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"b_free", bufferIndex});
			Thread.yield(); printMessages();
			SCAudio.scsynth_android_doOsc(new Object[] {"sync"});
			Thread.yield(); printMessages();

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			Thread.yield(); printMessages();

		} finally {
		    audioTrack.stop();
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
	
	protected AudioTrack createAudioOut() {
		@SuppressWarnings("all") // the ternary operator does not contain dead code
		int channelConfiguration = numOutChans==2?
				AudioFormat.CHANNEL_CONFIGURATION_STEREO
				:AudioFormat.CHANNEL_CONFIGURATION_MONO;
		int minSize = AudioTrack.getMinBufferSize(
				sampleRateInHz, 
				channelConfiguration, 
				AudioFormat.ENCODING_PCM_16BIT);
		AudioTrack audioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC, 
				sampleRateInHz, 
				channelConfiguration, 
				AudioFormat.ENCODING_PCM_16BIT, 
				minSize, 
				AudioTrack.MODE_STREAM);
		audioTrack.play();
		return audioTrack;
	}
	
	// For a fresh install, make sure we have our test synthdefs and samples to hand.
	protected boolean initFiles() {
		try {
			String fileToDeliver = "default.scsyndef";
			String synthDefsDirStr = ScService.getSynthDefsDirStr(getContext());
			ScService.initDataDir(synthDefsDirStr);
			ScService.deliverDataFile(getContext(), fileToDeliver, synthDefsDirStr);
			assertTrue("Failed copying " + fileToDeliver,
					Arrays.asList(new File(synthDefsDirStr).list()).contains(fileToDeliver));
			Log.d("initFiles", "copied " + fileToDeliver + " to " + synthDefsDirStr);
			fileToDeliver = "PlayABuffer.scsyndef";
			ScService.deliverDataFile(getContext(), fileToDeliver, synthDefsDirStr);
			assertTrue("Failed copying " + fileToDeliver,
					Arrays.asList(new File(synthDefsDirStr).list()).contains(fileToDeliver));
			Log.d("initFiles", "copied " + fileToDeliver + " to " + synthDefsDirStr);
			fileToDeliver = "a11wlk01.wav";
			String soundsDirStr = ScService.getSoundsDirStr(getContext());
			ScService.initDataDir(soundsDirStr);
			ScService.deliverDataFile(getContext(), fileToDeliver, soundsDirStr);
			assertTrue("Failed copying " + fileToDeliver,
					Arrays.asList(new File(soundsDirStr).list()).contains(fileToDeliver));
			Log.d("initFiles", "copied " + fileToDeliver + " to " + soundsDirStr);
			fileToDeliver = "aaa.wav";
			ScService.deliverDataFile(getContext(), fileToDeliver, soundsDirStr);
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