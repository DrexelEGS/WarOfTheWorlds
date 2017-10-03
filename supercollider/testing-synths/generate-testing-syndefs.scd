(
SynthDef(\sine, { |amp = 0.5, freq = 440|
	var data = SinOsc.ar(freq, 0, amp);
	Out.ar(0, data ! 2);
}).store;
)

(
SynthDef(\sine, { |amp = 0.5, freq = 440|
	var data = SinOsc.ar(freq, 0, amp);
	Out.ar(0, data ! 2);
}).play;
)

(
a = Synth("sine");
)

(
s = Server.local;
b = Buffer.read(s, "C:/Users/sr876/acad/devel/wotw-app/Sensors2OSC/SuperCollider-library/src/main/assets/a11wlk01.wav");
)

(
SynthDef("PlayABuffer",{ arg bufnum = 0;
	Out.ar( 0,
	PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), loop: 1.0)
	)
}).store;
)

(
a = Synth("PlayABuffer", [\bufnum, b.bufnum]);
)

(
SynthDef("PlayABuffer",{ arg bufnum = 0;
	Out.ar( 0,
	PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum), loop: 1.0)
	)
}).play(s, [\bufnum, b.bufnum]);
)

(
s = Server.local;
s.boot;
b = Buffer.alloc(s, s.sampleRate * 3.0, 1); // 3 seconds buffer
//b.setn(0, Array.fill(b.numFrames, { 1.0.rand } ));
)

(
a = Array.fill(44100, {1.0.rand2}); // 1 second of noise (in an array in the language)
b = Buffer.loadCollection(s, a); // this line loads the array into the buffer (on the server)
b.play // and now we have a beautiful noise!
)

(
s = Server.local;
b = Buffer.read(s, "C:/Users/sr876/acad/devel/wotw-app/supercollider/Alien Orientation/02 Mozart.wav");
)

(
SynthDef("SmearedSong",
 { arg bufnum = 2, smear = 0, amp = 1;
	 // smeared song
	var in, chain, out;
	in = PlayBuf.ar(1, bufnum, BufRateScale.kr(bufnum) * 0.5, loop: 1);
	chain = FFT({ LocalBuf(2048, 1) }.dup, in);
	chain = PV_MagSmear(chain, smear);
	out = IFFT(chain);
	out = Splay.ar(PitchShift.ar(out, 0.2, smear.explin(0.33333, 50, 1, [0.6667, 1.18, 0.5, 1.25]), timeDispersion: { exprand(0.001, 0.05) }.dup(4)), levelComp: false);
	    GVerb.ar((out * amp * 8).softclip, 80, 4.85, 0.41, 0.19, -3.dbamp, -9.dbamp, -11.dbamp);
//}).store
}).play(s, [\bufnum, b.bufnum]);
)

(
a = Synth("SmearedSong", [\bufnum, b.bufnum]);
)

(
a.set(\smear, 10, \amp, 100);
)

(
b.free()
)



(
SynthDef ( "StereoPlayer" , { 
	arg outbus=0, bufnum=0, amp=1, pan=0, mute=1, start=0, end=1, rate=1, fps=12, index=0; 
    	var phasor, length, right, left, env, clock;

    	//length = (end - start) * BufFrames.kr(bufnum); // scale from range 0 - 1 to 0 - buflength
    	//clock = LFPulse.kr(rate.abs / length, 0); // loop
    	//length = Latch.kr(length, clock); //again
	//env = EnvGen.kr( Env .new([0,1,1,0], [0.01, 0.98,0.01]), clock, timeScale:length /(rate.abs)) * amp * mute;
	phasor = Phasor.ar( 0, rate, start*BufFrames.kr(bufnum), end*BufFrames.kr(bufnum));
    	#left, right = BufRd.ar( 2, bufnum, phasor, 1 ) * amp * mute; // * env;
    	Out .ar(outbus, Balance2.ar(left, right, pan));
	
	SendTrig .kr( LFPulse.kr(fps, 0), index, phasor/BufRateScale.kr(bufnum)); //12 times per sec playhead.
}).load(s);
)



//author: Karishma 
//(note, synth 0, amplitude and frequency are the same syndefs with different names)

(
SynthDef("synth0", {arg freq=800, amp=0.1;
	var osc;
	osc = SinOsc.ar(freq, 0, amp); // 800 Hz sine oscillator
	Out.ar(0, osc); // send output to audio bus zero.
}).load; // write the def to disk in the default directory synthdefs/
)

b = Buffer.read(s, Platform.resourceDir +/+ "sounds" +/+ "a11wlk01.wav");	
(
x = SynthDef("synth1",{ arg out = 0, bufnum;
	Out.ar( out,
		PlayBuf.ar(1, b.bufnum, BufRateScale.kr(b.bufnum) )
	)
}).load; //play(s,[\bufnum, b.bufnum ]);
)
