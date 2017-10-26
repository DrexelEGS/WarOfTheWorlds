/**
 * @author Peter Kirn peter@createdigitalmedia.net for the pdportable project
 * @stolenshamelesslyby Alex Shaw alex@glastonbridge.com for SuperCollider-Android
 */

package net.sf.supercollider.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ScService extends Service {
	protected static final String TAG="SuperCollider-Android";
	// static methods to construct SC file paths with a Context:
	private static String getBaseSCDirStr(Context context) {
		return context.getFilesDir().getAbsolutePath() + "/supercollider";
	}
	public static String getSoundsDirStr(Context context) {
		return getBaseSCDirStr(context) + "/sounds";
	}
	public static String getSynthDefsDirStr(Context context) {
		return getBaseSCDirStr(context) + "/synthdefs";
	}
	public static String getPluginsDirStr(Context context) {
		return context.getApplicationInfo().nativeLibraryDir;
	}
    // note: the pluginsDirStr was previously below getDataDirectory(), more specifically:
    // getFilesDir().getParent() + "/lib" (this way of constructing it won't work without a Context)
    // this might have implications for loading lib files from it (unclear?),
	// but this seems to have been for user plugins only, and unused currently

	/**
	 * Our AIDL implementation to allow a bound Activity to talk to us
	 */
	private final ISuperCollider.Stub mBinder = new ISuperCollider.Stub() {
		public void sendMessage(OscMessage oscMessage) throws RemoteException {
			ScService.this.audioThread.sendMessage(oscMessage);
		}
		public void openUDP(int port) throws RemoteException {
			ScService.this.audioThread.openUDP(port);
		}
		public void closeUDP() throws RemoteException {
			ScService.this.audioThread.closeUDP();
		}
		public void sendQuit() throws RemoteException {
			ScService.this.audioThread.sendQuit();
		}
	};
	
    private int NOTIFICATION_ID = 42;
    private SCAudio audioThread;

	public void start() {
		if (audioThread == null || !audioThread.isRunning() ) {
			audioThread = new SCAudio(0, getPluginsDirStr(this), getSynthDefsDirStr(this));
			audioThread.start();
		}
	}

	public void stop() {
		if (audioThread != null) {
			try {
				mBinder.sendQuit();
			} catch (RemoteException re) {
				re.printStackTrace();
			}
			while (!audioThread.isEnded()) {
				try {
					Thread.sleep(50L);
				} catch (InterruptedException err) {
					err.printStackTrace();
					break;
				}
			}
		}
	}

	/*
	 * Lifecycle management:
	 */

	@Override
	public void onCreate() {
		Log.i(TAG, "SCService - onCreate called");
		audioThread = null;
		String synthDefsDirStr = getSynthDefsDirStr(this);
		try {
			ScService.initDataDir(synthDefsDirStr);
			// deliver all scsyndefs:
			String[] filesToDeliver = this.getAssets().list("");
			StringBuilder sb = new StringBuilder();
			for (String fileTD : filesToDeliver) {
				if (fileTD.toLowerCase().endsWith(".scsyndef")) {
					ScService.deliverDataFile(this, fileTD, synthDefsDirStr);
					sb.append(fileTD + " ");
				}
			}
			Log.i(TAG, "SCService - delivered scsyndef files: " + sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
			showError("Could not create directory " + synthDefsDirStr + " or copy scsyndefs to it. Check if SD card is mounted to a host.");
		}
		// Start the service thread here:
		this.start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The service is starting, due to a call to startService()
		// So we want to actually make it a foreground service with a notification
		Log.i(TAG, "SCService - onStartCommand called");
		Notification notification = new Notification.Builder(this)
				.setContentTitle("SuperCollider Sound Server")
				.setContentText("running")
				.setSmallIcon(R.drawable.icon)
				.setWhen(System.currentTimeMillis())
				.build();
		startForeground(NOTIFICATION_ID, notification);
		return Service.START_STICKY;
	}

	@Override
    public IBinder onBind(Intent intent) {
		// A client is binding to the service with bindService()
		Log.d(TAG, "SCService - onBind called");
        return mBinder;
    }

	@Override
	public boolean onUnbind(Intent intent) {
		// All clients have unbound with unbindService()
		Log.d(TAG, "SCService - onUnbind called");
		return true; // allow rebind
	}

	@Override
	public void onRebind(Intent intent) {
		// A client is binding to the service with bindService(),
		// after onUnbind() has already been called
		Log.d(TAG, "SCService - onRebind called");
	}

	@Override
	public void onDestroy() {
		// The service is no longer used and is being destroyed
		// Called by Android API when not the front app any more. For this one we'll quit
		Log.d(TAG, "SCService - onDestroy called");
		this.stop();
		super.onDestroy();
	}

	private void showError(String errorMsg) {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(
				Context.NOTIFICATION_SERVICE);
		Context context = getApplicationContext();
		Notification notification = new Notification.Builder(context)
				.setContentTitle("SuperCollider Error")
				.setContentText(errorMsg)
				.setSmallIcon(R.drawable.icon)
				.setWhen(System.currentTimeMillis())
				.build();
		mNotificationManager.notify(1, notification);
		Log.e(SCAudio.TAG, errorMsg);
	}

	public static void deliverDataFile(Context context, String assetName, String targetDir) throws IOException {
		InputStream is = context.getAssets().open(assetName);
		OutputStream os = new FileOutputStream(targetDir + "/" + assetName);
		byte[] buf = new byte[1024];
		int bytesRead = 0;
		while (-1 != (bytesRead = is.read(buf))) {
			os.write(buf, 0, bytesRead);
		}
		is.close();
		os.close();
	}

	public static void initDataDir(String dirStr) throws IOException {
		File dir = new File(dirStr);
		if (! (dir.mkdirs() || dir.isDirectory())) {
			throw new IOException("Couldn't create dataDir: " +  dirStr);
		}
	}

}
