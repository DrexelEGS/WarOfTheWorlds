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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ScService extends Service { 
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
		return getBaseSCDirStr(context) + "/lib";
	}
    // note: the pluginsDirStr was previously below getDataDirectory(), more specifically:
    // getFilesDir().getParent() + "/lib" (this way of constructing it won't work without a Context)
    // this might have implications for loading lib files from it (unclear?),
	// but this seems to have been for user plugins only, and unused currently

	/**
	 * Our AIDL implementation to allow a bound Activity to talk to us
	 */
	private final ISuperCollider.Stub mBinder = new ISuperCollider.Stub() {
		//@Override
		public void start() throws RemoteException {
			ScService.this.start();
		}
		//@Override
		public void stop() throws RemoteException {
			ScService.this.stop();
		}
		//@Override
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
	
    private int NOTIFICATION_ID = 1;
    private SCAudio audioThread;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void start() {
		if (audioThread == null || !audioThread.isRunning() ) {
			audioThread = new SCAudio(getPluginsDirStr(this), getSynthDefsDirStr(this));
			audioThread.start();
		}
	}

	@Override
    public void onCreate() {
		audioThread = null;
		String synthDefsDirStr = getSynthDefsDirStr(this);
		try {
			ScService.initDataDir(synthDefsDirStr);
			ScService.deliverDataFile(this, "default.scsyndef", synthDefsDirStr);
		} catch (IOException e) {
			e.printStackTrace();
			showError("Could not create directory " + synthDefsDirStr + " or copy default.scsyndef to it. Check if SD card is mounted to a host.");
		}
    }

	private void showError(String errorMsg) {
		//1
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		//2
		int icon = R.drawable.icon;
		CharSequence errorText = errorMsg;
		Notification mNotification = new Notification(icon, errorText, System.currentTimeMillis());
		//3
		Context context = getApplicationContext();
		CharSequence errorTitle = "SuperCollider error";
		Intent notificationIntent = new Intent(this, ScService.class);
		PendingIntent errorIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
		//			mNotification.setLatestEventInfo(context, errorTitle, errorText, errorIntent);
		//4
		mNotificationManager.notify(1, mNotification);

		Log.e(SCAudio.TAG, errorMsg);
	}

	/* onStart is called for Android versions < 2.0, but onStartCommand is
       called for Android 2.0 and above.  Anything which is generic to all
       Android releases should be declared in onStart.  Any newer features
       should be declared in onStartCommand.  Access new features using 
       * reflection, to facilitate compilation to 1.5 and 1.6
    */
    @Override
    public void onStart(Intent intent, int startId) { }
    public int onStartCommand(Intent intent, int flags, int startId) {
     	onStart(intent, startId);
        int START_STICKY = 1;
        try {
            // Android 2.1 API allows us to specify that this service is a foreground task
            Notification notification = new Notification(R.drawable.icon,
                    getText(R.string.app_name), System.currentTimeMillis());
            Class<?> superClass = super.getClass();
            Method startForeground = superClass.getMethod("startForeground",
                new Class[] {
                    int.class,
                    Class.forName("android.app.Notification")
                }
            );
            Field startStickyValue = superClass.getField("START_STICKY");
            START_STICKY=startStickyValue.getInt(null);
            startForeground.invoke(this, new Object[] {
                NOTIFICATION_ID, 
                notification}
            );
        } catch (Exception nsme) {
            // We can't get the newer methods
        }
        return Service.START_STICKY;
    }
	
    public void stop() {
		try {
			mBinder.sendQuit();
		} catch (RemoteException re) {
			re.printStackTrace();
		} 
		while(!audioThread.isEnded()){
			try{
				Thread.sleep(50L);
			}catch(InterruptedException err){
				err.printStackTrace();
				break;
			}
		}
    }
    
	// Called by Android API when not the front app any more. For this one we'll quit
	@Override
	public void onDestroy(){
		stop();
		super.onDestroy();
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
