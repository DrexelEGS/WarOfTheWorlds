

// the dist argument should be distance from goal, normalized from 0-1
// you can manipulate pan based on angle, for instance (range -1.0, 1.0)
// rate is automtically scaled by distance, but you can change the base rate from 1 to something else
// amp can also be manipulated, typically 0-1, though it could go slightly higher in some cases
// make sure that bufnum is set when the synth is launched - use a mono buf
(
SynthDef(\bufSticker, {|out = 0, bufnum, amp = 1, rate = 1, pan = 0, dist = 0|
    var read, sig, holdTrig, holdTime, posGate, repRate, smear, resetPos, phaseOut, trigem;
    rate = rate * dist.linlin(0, 1.0, 1.0, 0.6);
    holdTrig = Dust.kr(dist.linexp(0, 1.0, 0.1, 0.4));
    resetPos = A2K.kr(LocalIn.ar(1));
    posGate = Trig.kr(holdTrig, TRand.kr(0.2, dist.linlin(0, 1.0, 0.3, 3), holdTrig));
    resetPos = Latch.kr(resetPos, posGate);
    repRate = TExpRand.kr(2.0, 12.0, posGate);
    trigem = Impulse.kr(repRate * posGate);
    read = Phasor.ar(trigem, BufRateScale.kr(bufnum) * rate, 0, BufFrames.kr(bufnum), resetPos);
    LocalOut.ar(read);
    sig=DelayC.ar(BufRd.ar(1, bufnum, read, 1, 4), 0.067, 0.067) * (1 - trigem).lag(0.067);
    smear = if(dist, Mix(FreqShift.ar(sig, { ExpRand(20, 440.0) * TChoose.kr(Impulse.kr(0), [-1, 1]) }.dup(4), mul: 0.4)), sig);
    // smear = SMS.ar(sig, noisefloor: 0.05, freqmult: LFNoise0.kr(0.2).range(-12, 12).midiratio, formantpreserve: 1);
    // smear = smear[1] * LFNoise1.kr(0.1).range(0.2, 0.4) + smear[0] * 2;
    Out.ar(out,
        Pan2.ar(
            if(LFNoise2.kr(LFNoise0.kr(0.3).exprange(0.125, 0.4), 0.5, 0.35).max(0).round.lag(0.2), LPF.ar(LeakDC.ar(sig), 10000), smear),
            pan,
            amp
        )
    )
}).load;
)

(
s = Server.local;
b = Buffer.read(s, "C:/Users/sr876/acad/devel/wotw-app/Sensors2OSC/SuperCollider-library/src/main/assets/a11wlk01.wav");
)

(
a = Synth("bufSticker", [\bufnum, b.bufnum]);
)

(
a.set(\dist, 0.5);
)

