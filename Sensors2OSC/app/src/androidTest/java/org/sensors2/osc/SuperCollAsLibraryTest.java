package org.sensors2.osc;

import android.app.Application;
import android.test.ApplicationTestCase;

import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.test.AndroidTestCase;
import android.util.Log;


/**
 * Trying to replicate a minimal test from the Supercollider NativeAudioTests, so that
 * we can make sure that importing and using SC code from the main app will work.
 */
public class SuperCollAsLibraryTest extends ApplicationTestCase<Application> {
    public SuperCollAsLibraryTest() {
        super(Application.class);
    }

    protected static final String TAG = "SuperCollAsLibraryTest";
    final int numInChans = 1;
    final int numOutChans = 1;
    final int shortsPerSample = 1;
    final int bufSizeFrames = 64*16;
    final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample;
    int sampleRateInHz = 11025;
    short[] audioBuf = new short[bufSizeShorts];

    public void testSynthDefs() {
        initFiles();
        System.loadLibrary("sndfile");
        System.loadLibrary("scsynth");
        SCAudio.scsynth_android_initlogging();
        SCAudio.scsynth_android_start(sampleRateInHz, bufSizeFrames, numInChans, numOutChans, shortsPerSample,
                ScService.dllDirStr, ScService.dataDirStr);
        assert(true); // SC started, have a biscuit

        // Silence is golden
        assert(SCAudio.scsynth_android_genaudio(audioBuf)==0);
        for(short s : audioBuf){
            if (BuildConfig.DEBUG && !(s == 0)){
                throw new AssertionError();
            }
        };

        assert(true);

        int buffersPerSecond = (sampleRateInHz*shortsPerSample)/(bufSizeShorts*numOutChans);

        AudioTrack audioTrack = createAudioOut(); // audible testing
        try {

            // test test.scsyndef
            SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "test", OscMessage.defaultNodeId});

            for(int i=0; i<buffersPerSecond; ++i) {
                SCAudio.scsynth_android_genaudio(audioBuf);
                audioTrack.write(audioBuf, 0, bufSizeShorts);
            }

            assert(true);

            // Test buffers
            SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
            int bufferIndex = 10;
            SCAudio.scsynth_android_doOsc(new Object[] {"b_allocRead", bufferIndex, ScService.soundsDirStr + "/a11wlk01.wav"});
            //SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "tutor", OscMessage.defaultNodeId});

            for(int i=0; i<buffersPerSecond; ++i) {
                SCAudio.scsynth_android_genaudio(audioBuf);
                audioTrack.write(audioBuf, 0, bufSizeShorts);
            }

            SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
            //SCAudio.scsynth_android_doOsc(new Object[] {"b_free", bufferIndex});
            assert(true);
        } finally {
            audioTrack.stop();
        }
        if (!SCAudio.hasMessages()) assert(false);
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
            ScService.initDataDir(ScService.dataDirStr);
            ScService.deliverDataFile(getContext(), "default.scsyndef", ScService.dataDirStr);
            ScService.deliverDataFile(getContext(), "test.scsyndef", ScService.dataDirStr);
            ScService.initDataDir(ScService.soundsDirStr);
            ScService.deliverDataFile(getContext(), "a11wlk01.wav", ScService.soundsDirStr);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
