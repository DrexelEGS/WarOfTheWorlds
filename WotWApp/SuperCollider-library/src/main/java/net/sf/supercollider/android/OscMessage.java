package net.sf.supercollider.android;

import java.util.LinkedList;

import android.os.Parcel;
import android.os.Parcelable;

/** A Simple pastiche of the SuperCollider packet in Java land.  The internal
 *  representation is simple java objects, which are converted to a buffer in 
 *  c++ later.
 * 
 * Simple case: The first add() to a new OscMessage must be a string /command, 
 *    the trailing arguments can be int, long, string, float, or double
 * TODO: BROKEN! Compound case: All the adds must be OscMessages
 * 
 * The create___Message() methods serve as examples and for convenience
 * 
 * This object is eventually evaluated by scsynth_android_doOsc in JNI
 *  
 * @author alex
 *
 */
public final class OscMessage implements Parcelable {
	
	public static final int defaultNodeId = 999;

	///////////////////////////////////////////////////////////////////////////
	// Static templates for common operations
	///////////////////////////////////////////////////////////////////////////
	
	public static OscMessage createGroupMessage(int nodeId, int addAction, int target) {
		return new OscMessage().add("/g_new").add(nodeId).add(addAction).add(target);
	}
	public static OscMessage createGroupMessage(int nodeId) {
		// default to add as head of default group:
		return createGroupMessage(nodeId, 0, 1);
	}

	public static OscMessage createSynthMessage(String name, int nodeId, int addAction, int target) {
		return new OscMessage().add("/s_new").add(name).add(nodeId).add(addAction).add(target);
	}
	public static OscMessage createSynthMessage(String name, int nodeId) {
		// default to add as head of default group:
		return createSynthMessage(name, nodeId, 0, 1);
	}

	public static OscMessage createNodeFreeMessage(int nodeId) {
		return new OscMessage().add("/n_free").add(nodeId);
	}

	public static OscMessage createSetControlMessage(int nodeId, String control, float value) {
		return new OscMessage().add("/n_set").add(nodeId).add(control).add(value);
	}

	public static OscMessage createAllocReadMessage(int bufferId, String filePath) {
		return new OscMessage().add("/b_allocRead").add(bufferId).add(filePath);
	}

	public static OscMessage createBufferFreeMessage(int bufferId) {
		return new OscMessage().add("/b_free").add(bufferId);
	}

	public static OscMessage createNotifyMessage(int mode) {
		return new OscMessage().add("/notify").add(mode);
	}
	public static OscMessage createNotifyMessage() {
		// default to enabling notifications:
		return createNotifyMessage(1);
	}

	public static OscMessage createErrorModeMessage(int mode) {
		return new OscMessage().add("/error").add(mode);
	}
	public static OscMessage createErrorModeMessage() {
		// default to enabling global error reporting:
		return createErrorModeMessage(1);
	}

    public static OscMessage createSyncMessage(int syncId) {
        return new OscMessage().add("/sync").add(syncId);
    }
    public static OscMessage createSyncMessage() {
        // default to requesting a sync ID of 0 in the response:
        return createSyncMessage(0);
    }

	/*
	 * NOTE: it is better not to send a plain /quit message yourself,
	 * instead call SCAudio.sendQuit() which tidies up the java part of the audio too.
	 */
	public static OscMessage createQuitMessage() {
		return new OscMessage().add("/quit");
	}

	// TODO: This message seems to be getting parsed by doOsc, but it
	// doesn't affect the output.  What's that all about then eh?
	/* (commented out as it is a very specific and node dependent message using the default id)
	public static OscMessage createNoteMessage(int note, int velocity) {
	    OscMessage notebundle = new OscMessage().add("/n_set")
				.add(defaultNodeId).add("note").add(note);
	    OscMessage velbundle = new OscMessage().add("/n_set")
				.add(defaultNodeId).add("velocity").add(velocity);
		return new OscMessage().add(notebundle).add(velbundle);
	}
	 */

	///////////////////////////////////////////////////////////////////////////
	// The actual OscMessage implementation
	///////////////////////////////////////////////////////////////////////////
	
	private LinkedList<Object> message = new LinkedList<Object>();
	/*
	 * Creates an empty OscMessage
	 */
	public OscMessage() {}
	/*
	 * Convenience constructor for creating a whole message in a oner
	 */
	public OscMessage(Object[] message) {
		for (Object token : message) {
			if (token instanceof Integer) add ((Integer) token);
			else if (token instanceof Float) add((Float) token);
			else if (token instanceof Double) add((Double) token);
			else if (token instanceof Long) add((Long) token);
			else if (token instanceof String) add ((String) token);
		}
	}
	public OscMessage add(int i) { message.add(i); return this; }
	public OscMessage add(float f) { message.add(f); return this; }
	public OscMessage add(double d) { message.add(d); return this; }
	public OscMessage add(String s) { message.add(s); return this; }
	public OscMessage add(long ii) { message.add(ii); return this; }
	public OscMessage add(OscMessage m) { message.add(m); return this; }

	// void-returning add methods used from C-code to simplify marshalling
	// c-code doesn't need the convenience of the .add(x).add(y).add(z) builder pattern
	public void vadd(int i) { message.add(i); }
	public void vadd(float f) { message.add(f); }
	public void vadd(double d) { message.add(d); }
	public void vadd(String s) { message.add(s); }
	public void vadd(long ii) { message.add(ii); }
	public void vadd(OscMessage m) { message.add(m); }

	public Object[] toArray() { return message.toArray(); }

	/**
	 * Convenient string representation for debugging
	 */
	public String toString() {
		String stringValue= new String();
		for (Object elem : message) stringValue += "/"+elem.toString();
		return stringValue;
	}
	
	public Object get(int location) {
		return message.get(location);
	}
	
	///////////////////////////////////////////////////////////////////////////
	// Parcelling code for AIDL 
	///////////////////////////////////////////////////////////////////////////
	
	public static final Parcelable.Creator<OscMessage> CREATOR = new Parcelable.Creator<OscMessage>() {
		//@Override
		public OscMessage createFromParcel(Parcel source) {
			OscMessage retval = new OscMessage();
			source.readList(retval.message, null);
			return retval;
		}

		//@Override
		public OscMessage[] newArray(int size) {
			OscMessage[] retval = new OscMessage[size];
			for(int i = 0; i<size;++i) retval[i] = new OscMessage();
			return retval;
		}
	};
	
	//@Override
	public int describeContents() {
		return 0;
	}
	//@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeList(message);
	}
}
