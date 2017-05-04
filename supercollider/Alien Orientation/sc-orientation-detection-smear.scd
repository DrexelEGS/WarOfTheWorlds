(
s.waitForBoot{
var run = true, sensor = 'orientation', ox = 0, ax, ay, av, getAngle, getDistance, getAngleDiff, aSynth, height, width, playerLoc, b;

// load buffer (adjust path as necessary)
b.free;
s.sync;
b = Buffer.read(s, Document.current.dir ++ "/Sound Files/02 Mozart.wav",
 action: {|buf|
	 // smeared song
	aSynth = {|smear = 30, amp = 0.5|
	Ê Ê var in, chain, out;
	Ê Ê in = PlayBuf.ar(2, buf.bufnum, BufRateScale.kr(buf.bufnum) * 0.5, loop: 1);
	Ê Ê chain = FFT({ LocalBuf(2048, 1) }.dup, in);
	Ê Ê chain = PV_MagSmear(chain, smear);
	    out = IFFT(chain);
	Ê Ê out = Splay.ar(PitchShift.ar(out, 0.2, smear.explin(0.33333, 50, 1, [0.6667, 1.18, 0.5, 1.25]), timeDispersion: { exprand(0.001, 0.05) }.dup(4)), levelComp: false);
	    GVerb.ar((out * amp * 8).softclip, 80, 4.85, 0.41, 0.19, -3.dbamp, -9.dbamp, -11.dbamp);
	}.play;
});
s.sync;

// the canvas
height = 600;
width = 800;
// single alien position
ax = width.rand;
ay = height.rand;
// alien movement vector
av = { rrand(-0.5, 0.5) }.dup;

// player location
playerLoc = [width * 0.5, height * 0.5];
// function to calculate alien angle relative to user
getAngle = {|ax, ay|
	var ab, bc, ang;
	ab = ax - playerLoc[0];
	bc = ay - playerLoc[1];
	ang = bc.atan2(ab);
	ang;
};

// function to calculate distance to alien
getDistance = {|ax, ay|
	((ax - playerLoc[0]).squared + (ay - playerLoc[1]).squared).sqrt;
};

// function to calculate difference between angles
getAngleDiff = {|a1, a2|
	var diff;
	a1 = a1 / 0.01745329252;
	a2 = a2 / 0.01745329252;
	diff = (a1 - a2 + 180 + 360) % 360.0 - 180;
	diff * 0.01745329252;
};

// play background noise
//{ LPF.ar(Splay.ar(Crackle.ar([1.99,1.98777, 1.995]).cubed), 1000, 1.5).softclip + Splay.arFill(3, { SinOsc.ar(TExpRand.kr(220, 1880, Dust.kr(0.3)).lag(1.5), mul: 0.25).ring1(LFCub.ar(TExpRand.kr(110, 360, Dust.kr(0.8)).lag(1), mul: 0.5)) * LFNoise2.kr(0.8).range(0, 0.28).squared }, 0.5) * Line.kr(0, 0.3, 1) }.play;

// wait for the server to do its thing
s.sync;

// open the port(s) for OSC from Android
thisProcess.openUDPPort(9000); 
thisProcess.openPorts;

Routine{
	inf.do({
		var userAngle = ox * 0.01745329252, alienAngle, diff, dist;
		0.05.wait; 
		ax = ax + av[0];
		((ax > width) || (ax < 0)).if({
			av[0] = av[0].neg;
		});  
		ay = ay + av[1]; 
		((ay > height) || (ay < 0)).if({
			av[1] = av[1].neg;
		}); 
		av = av + { rrand(-0.2, 0.2) }.dup;
		av = av * 0.99;
		alienAngle = getAngle.value(ax, ay);
	
		// set synth values (amp and click rate) based on angle and distance
		diff = pi - getAngleDiff.value(userAngle, alienAngle).abs;
		dist = getDistance.value(ax, ay);
		aSynth.set(\smear, diff.linexp(0, pi, 0.3333, 50), \amp, (dist * 0.2 + 1).reciprocal);
	});
}.play;

// handle incoming OSC messages
OSCFunc.newMatching({|msg, time, addr, recvPort| 
	ox = msg[1] - 180;
	// ox.postln;
}, sensor ++ '/X');
}
)