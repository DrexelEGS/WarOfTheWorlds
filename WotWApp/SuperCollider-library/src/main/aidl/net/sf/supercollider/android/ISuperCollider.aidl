package net.sf.supercollider.android;
import net.sf.supercollider.android.OscMessage;

/** Provide IPC to the SuperCollider service.
 *
 */ 

interface ISuperCollider {
	// Send an OSC message
	void sendMessage(in net.sf.supercollider.android.OscMessage oscMessage);
	// Open a UDP listener for remote connections. Please remember to close it on exit.
	void openUDP(in int port);
	// Close UDP listener.
	void closeUDP();
	// Gracefully quit the SC process and the Android audio loop
	void sendQuit(); 
}