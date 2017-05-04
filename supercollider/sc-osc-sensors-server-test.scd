( // run first to define specs for different sensors
	var sensor;
	ControlSpec.specs[\accelerometer] = ControlSpec(-19.6133, 19.6133);
	ControlSpec.specs[\magneticfield] = ControlSpec(-600, 600);
	ControlSpec.specs[\gyroscope] = ControlSpec(-8.726646, 8.726646);
	ControlSpec.specs[\pressure] = ControlSpec(0, 1013.25);
	ControlSpec.specs[\proximity] = ControlSpec(0, 8); // front = 0, back = 8
	ControlSpec.specs[\light] = ControlSpec(0, 60000);
	ControlSpec.specs[\relativehumidity] = ControlSpec(0, 100);
	ControlSpec.specs[\ambienttemperature] = ControlSpec(0, 165.0);
	ControlSpec.specs[\65558] = ControlSpec(0, 255);
	ControlSpec.specs[\rotationvector] = ControlSpec(-1.0, 1.0);
	ControlSpec.specs[\linearacceleration] = ControlSpec(-19.6133, 19.6133);
	// orientation will require preprocessing for different X scales and potentially to wrap angle values
	ControlSpec.specs[\orientation] = ControlSpec(-180, 180);
	
	// open the port(s) for OSC from Android
	thisProcess.openUDPPort(9000); 
	thisProcess.openPorts;

	// Boot the server, if you haven't already
	Server.default.waitForBoot{
	
		// Simple subtractive synthesis. Light sensor turns on and off (must be dark to hear)
		sensor = 'accelerometer';
		a = {|freq = 110, ffreq = 180, rq = 0.1, amp = 0.2, gate=1|
			var sig;
			sig = Mix.fill(3, { LFSaw.ar((freq * rrand(0.985, 1.01)).lag(0.1)) }) * 0.3;
			sig = BLowPass.ar(sig, ffreq.lag(0.1), rq.lag(0.1), amp.lag(0.1));
			Pan2.ar(sig * EnvGen.kr(Env.asr(0.1, 1, 1), gate), 0);
		}.play;
		
		OSCFunc.newMatching({|msg, time, addr, recvPort| 
			a.set(\freq, sensor.asSpec.unmap(msg[1]).linexp(0, 1.0, 40, 280.0));
		}, sensor ++ '/X');
		OSCFunc.newMatching({|msg, time, addr, recvPort| 
			a.set(\ffreq, sensor.asSpec.unmap(msg[1]).linexp(0, 1.0, 20, 20000.0));
		}, sensor ++ '/Y');
		OSCFunc.newMatching({|msg, time, addr, recvPort| 
			a.set(\amp, sensor.asSpec.unmap(msg[1]).squared);
		}, sensor ++ '/Z');
		OSCFunc.newMatching({|msg, time, addr, recvPort| 
			(msg[1] < 10).if({
				a.set(\gate, 1.0);
			}, {
				a.set(\gate, 0);
			});
		}, 'light');
	}
)