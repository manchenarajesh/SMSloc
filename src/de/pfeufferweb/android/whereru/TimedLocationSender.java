package de.pfeufferweb.android.whereru;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

public class TimedLocationSender extends Thread {

	private static final float ACCEPTED_ACCURACY = 30f;
	private static final int WAIT_TIME = 1000;
	private static final int MAX_WAIT_TIME = 20000;

	private final LocationManager locationManager;
	private final Context context;
	private final String receiver;
	private final int notificationId;

	private long startTime;
	private Location lastLocation;

	public TimedLocationSender(Context context, String receiver,
			int notificationId) {
		this.context = context;
		this.receiver = receiver;
		this.notificationId = notificationId;
		this.locationManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);
	}

	@Override
	public synchronized void run() {
		startTime = System.currentTimeMillis();
		UpdateThread updateThread = new UpdateThread();
		updateThread.start();
		try {
			while (!isGoodEnough() && inTime()) {
				try {
					this.wait(WAIT_TIME);
					Log.d("TimedLocationProvider", "checking...");
				} catch (InterruptedException e) {
				}
			}
		} finally {
			updateThread.close();
		}
		new LocationSender(context, receiver, notificationId)
				.send(lastLocation);
	}

	private boolean inTime() {
		boolean inTime = (System.currentTimeMillis() - startTime) < MAX_WAIT_TIME;
		Log.d("TimedLocationProvider", "in time: " + inTime);
		return inTime;
	}

	private boolean isGoodEnough() {
		boolean goodEnough = lastLocation != null
				&& lastLocation.getAccuracy() < ACCEPTED_ACCURACY;
		Log.d("TimedLocationProvider", "good enough: " + goodEnough);
		return goodEnough;
	}

	private class UpdateThread extends Thread implements LocationListener {
		@Override
		public void run() {
			Log.d("TimedLocationProvider", "looper prepare");
			Looper.prepare();
			Log.d("TimedLocationProvider", "requesting location updates");
			locationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER, 1000, 1, this);
			Log.d("TimedLocationProvider", "looper loop");
			Looper.loop();
		}

		public void close() {
			locationManager.removeUpdates(this);
			Log.d("TimedLocationProvider", "looper quit");
			if (Looper.myLooper() != null) {
				Looper.myLooper().quit();
			}
		}

		@Override
		public void onLocationChanged(Location actLocation) {
			Log.d("TimedLocationProvider", "got location update");
			if (isBetter(actLocation)) {
				lastLocation = actLocation;
				synchronized (TimedLocationSender.this) {
					TimedLocationSender.this.notify();
				}
			}
		}

		private boolean isBetter(Location actLocation) {
			return lastLocation == null || actLocation != null
					&& lastLocation.getAccuracy() >= actLocation.getAccuracy();
		}

		@Override
		public void onProviderDisabled(String arg0) {
		}

		@Override
		public void onProviderEnabled(String arg0) {
		}

		@Override
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		}
	}
}