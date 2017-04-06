/*
* Test receiving OSC Data
*/

// check Addr and ports:

NetAddr.langPort;
NetAddr.localAddr;
// the above will probably not give you the external ip address of the machine
// but only 127.0.0.1 which is localhost. you'll haveto use the Operating System
// to find the ip address that the app needs to know

thisProcess.openUDPPort(9000); // attempt to open 9000
// if you don't do this, you need to change the port in the app to 57120

thisProcess.openPorts;

// check receiving:

// we could create a specific handler:
o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/doesntmatter');

// but for now, just checking that we get data is good enough:
OSCFunc.trace(true);

// after this and after turning on "Send data" and at least one of the
// (constant) data sources on the phone, such as pressure for example,
// there should be a lot of debug output...

OSCFunc.trace(false);

o.free;

