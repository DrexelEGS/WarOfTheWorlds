
// the dist argument should be distance from goal, normalized from 0-1
// you can manipulate pan based on angle, for instance (range -1.0, 1.0)
// rate is automtically scaled by distance, but you can change the base rate from 1 to something else
// amp can also be manipulated, typically 0-1, though it could go slightly higher in some cases
// make sure that bufnum is set when the synth is launched - use a mono buf
(
SynthDef(\bufSticker, {|out = 0, bufnum, amp = 1, rate = 1, pan = 0, dist = 0|
    var read, sig, holdTrig, holdTime, posGate, repRate, smear, resetPos, phaseOut, trigem,
    trig, modenv, pitchenv, timescale = 1, forgettor;
    rate = rate * dist.linlin(0, 1.0, 1.0, 0.75);
    holdTrig = Dust.kr(dist.linexp(0, 1.0, 0.1, 0.4));
    resetPos = A2K.kr(LocalIn.ar(1));
    posGate = Trig.kr(holdTrig, TRand.kr(0.2, dist.linlin(0, 1.0, 0.3, 3), holdTrig));
    resetPos = Latch.kr(resetPos, posGate);
    repRate = TRand.kr(3.0, 12.0, posGate);
    trigem = Impulse.kr(repRate * posGate);
    read = Phasor.ar(trigem, BufRateScale.kr(bufnum) * rate, 0, BufFrames.kr(bufnum), resetPos);
    LocalOut.ar(read);
    sig=DelayC.ar(BufRd.ar(1, bufnum, read, 1, 4), 0.067, 0.067) * (1 - trigem).lag(0.067);
    smear = if(LFNoise1.kr(0.6).range(0, 1.0).min(dist), Mix(FreqShift.ar(sig, { ExpRand(40, 440.0) * TChoose.kr(Impulse.kr(0), [-1, 1]) }.dup(4), mul: 0.4)), sig);
    // "forgetting" sound
    trig = CoinGate.kr(dist.linlin(0, 1.0, 0, 0.6), Dust.kr(dist.linexp(0, 1.0, 0.05, 0.2)));
    timescale = TRand.kr(1.0, dist.linlin(0, 1.0, 2.0, 5), trig);
    pitchenv = EnvGen.kr(Env([1, 1, 0.5], [0, 3], [0, \exp]), gate: trig, timeScale: timescale);
    modenv = EnvGen.kr(Env([0,0,1,0.8,0.8,0],[0,0.8,0.2,0.5,1.5]), gate: trig, timeScale: timescale);
    forgettor = PMOsc.ar(TExpRand.kr(55, 100.0, trig.dup), TExpRand.kr(30.0, 50, trig.dup) * pitchenv, modenv, mul: modenv.squared * 0.2).softclip + LPF.ar(BrownNoise.ar(0.4.dup), TExpRand.kr(80, 140.0, trig), modenv);
    Out.ar(out,
        modenv.squared * forgettor + ((1 - modenv) *
        Pan2.ar(
            if(LFNoise2.kr(LFNoise0.kr(0.3).exprange(0.125, 0.4), 0.5, 0.35).max(0).round.lag(0.2), LPF.ar(LeakDC.ar(Squiz.ar(sig, LFNoise1.kr(0.4).exprange(0.9, 1.6).squared.max(1), 1)), 10000), smear),
            pan,
            amp
        ))
    )
}).store;
)

(
s = Server.local;
b = Buffer.read(s, Document.current.dir ++ "/testing-synths/aaa.wav");
)

(
a = Synth("bufSticker", [\bufnum, b.bufnum]);
)

(
a.set(\dist, 0.5);
)

