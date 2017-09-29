(
SynthDef(\sine, { |amp = 0.5, freq = 440|
	var data = SinOsc.ar(freq, 0, amp);
	Out.ar(0, data ! 2);
}).store;
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
