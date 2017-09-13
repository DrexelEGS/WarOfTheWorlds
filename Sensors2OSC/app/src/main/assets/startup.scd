s = Server.local; // assign it to interpreter variable 's'
s.boot;
(
SynthDef("not_default", {arg freq=800;
	var osc;
	osc = SinOsc.ar(freq, 0, 0.1); // 800 Hz sine oscillator
	Out.ar(0, osc); // send output to audio bus zero.
}).load; // write the def to disk in the default directory synthdefs/
)

s.sendMsg("/s_new", "not_default", x = s.nextNodeID, 0, 1, "freq", 900);
s.sendMsg("/n_free", x);
(
{ arg freq=500;
	var osc;
	osc = SinOsc.ar(freq, 0, 0.1); // 800 Hz sine oscillator
	Out.ar(0, osc); // send output to audio bus zero.
}.play).stop
