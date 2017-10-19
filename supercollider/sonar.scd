(
SynthDef(\sonar, {|dist=1.0, pan=0, amp = 0.2, out=0|
    var env, sine, verb;
    env = EnvGen.kr(Env.perc(0.01, 1), gate: Impulse.kr(dist.linexp(0.0, 1.0, 0.5, 0.1)));
    sine = SinOsc.ar(685, mul: env * 0.5, add: PinkNoise.ar(0.05 * env));
    verb = GVerb.ar(sine, 80, 3, drylevel: dist.linlin(0.0, 1.0, 1.0, 0.2), taillevel: dist.linlin(0.0, 1.0, 0.2, 0.6));
    Out.ar(0, Pan2.ar(verb.mean, pan) * amp * dist.linlin(0, 1.0, 0.1, 1.0));
}).store;
)

