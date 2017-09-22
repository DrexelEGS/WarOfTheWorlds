//booting server 
s = Server.local; // assign it to interpreter variable 's'
s.boot;

//creating synth defs 
(
SynthDef("synth0", {arg freq=800, amp=0.1;
	var osc;
	osc = SinOsc.ar(freq, 0, amp); // 800 Hz sine oscillator
	Out.ar(0, osc); // send output to audio bus zero.
}).load; // write the def to disk in the default directory synthdefs/
)

s.sendMsg("/s_new", "synth0", x = s.nextNodeID, 0, 1, "freq", 900);
s.sendMsg("/n_set", x, "amp", 0.5);
s.sendMsg("/n_set", x, "freq", 400);
s.sendMsg("/n_free", x);

//testing new stuff
(
{ arg freq=500;
	var osc;
	osc = SinOsc.ar(freq, 0, 0.1); // 800 Hz sine oscillator
	Out.ar(0, osc); // send output to audio bus zero.
}.play).stop
b = Buffer.read(s, Platform.resourceDir +/+ "sounds" +/+ "a11wlk01.wav");	
(
x = SynthDef("synth1",{ arg out = 0;
	var b = Buffer.read(s, "sounds" +/+ "a11wlk01.wav");
	Out.ar( out,
		PlayBuf.ar(1, b.bufnum, BufRateScale.kr(b.bufnum) )
	)
}).load; //play(s,[\bufnum, b.bufnum ]);
)

s.sendMsg("/s_new", "synth1", x = s.nextNodeID, 0, 1); \bufnum, b.bufnum);

