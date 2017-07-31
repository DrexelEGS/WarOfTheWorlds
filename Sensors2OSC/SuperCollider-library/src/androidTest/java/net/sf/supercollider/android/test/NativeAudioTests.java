package net.sf.supercollider.android.test;

import java.io.File;

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
	final int numInChans = 1; 
	final int numOutChans = 1; 
	final int shortsPerSample = 1; 
	final int bufSizeFrames = 64*16;  
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 
	int sampleRateInHz = 11025;
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
		Log.d("initFiles", "permission external write: " + (permissionCheck == PackageManager.PERMISSION_GRANTED));
		//assertEquals("Permission not granted!", permissionCheck, PackageManager.PERMISSION_GRANTED);
		boolean retValBool = initFiles();
		assertTrue("initFiles failed!", retValBool);
        Log.d("initFiles", "completed");
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
	    	///////////////////////////////////////////////////////////////////////
	    	// test default.scsyndef
			SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "default", OscMessage.defaultNodeId});

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}

	    	///////////////////////////////////////////////////////////////////////
	    	// Test buffers
			SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			int bufferIndex = 10;
			SCAudio.scsynth_android_doOsc(new Object[] {"b_allocRead", bufferIndex, ScService.getSoundsDirStr(getContext()) + "/a11wlk01.wav"});
			//SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "tutor", OscMessage.defaultNodeId});
			
			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			
			SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			//SCAudio.scsynth_android_doOsc(new Object[] {"b_free", bufferIndex});
		} finally {
		    audioTrack.stop();
		}
		assertTrue("SCAudio has no messages!", SCAudio.hasMessages());
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
			File other;
			String[] filesInDir;
			other = getContext().getFilesDir();
			Log.d("initFiles", "checking getFilesDir " + other + " exists: " + other.exists());
			Log.d("initFiles", "listing dir contents " + other.list());
			Log.d("initFiles", "test create: " + new File(other, "testing").createNewFile());
			Log.d("initFiles", "test delete: " + new File(other, "testing").delete());
			// Note: using a non-bound Service (such as a hand-created ScService) as context
			// fails when calling getFilesDir()

			String synthDefsDirStr = ScService.getSynthDefsDirStr(getContext());
			File dir = new File(synthDefsDirStr);
			String dataDir = dir.getAbsolutePath();
            Log.d("initFiles", "creating " + dataDir + " exists: " + dir.exists());
			Log.d("initFiles", "parent " + dir.getParent() + " exists: " + dir.getParentFile().exists());
			dir.getParentFile().mkdir();
			dir.getParentFile().createNewFile();
			Log.d("initFiles", "paparent " + dir.getParentFile().getParent() + " exists: " + dir.getParentFile().getParentFile().exists());
            ScService.initDataDir(dataDir);
			ScService.deliverDataFile(getContext(), "default.scsyndef", synthDefsDirStr);
            Log.d("initFiles", "copied default.scsyndef to " + synthDefsDirStr);
            filesInDir = new File(synthDefsDirStr).list();
            Log.d("initFiles", "listing dir contents " + filesInDir);

			String soundsDirStr = ScService.getSoundsDirStr(getContext());
            Log.d("initFiles", "creating " + soundsDirStr);
			ScService.initDataDir(soundsDirStr);
			ScService.deliverDataFile(getContext(), "a11wlk01.wav", soundsDirStr);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}
