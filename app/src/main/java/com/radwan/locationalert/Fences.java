package com.radwan.locationalert;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import com.google.android.gms.location.*;
import org.json.*;

public class Fences {
  static boolean permitted(Context c) {
    return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        && (Build.VERSION.SDK_INT < 29
            || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
  }

  static PendingIntent pending(Context c) {
    return PendingIntent.getBroadcast(
        c,
        0,
        new Intent(c, GeofenceReceiver.class),
        PendingIntent.FLAG_UPDATE_CURRENT
            | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
  }

  static void register(Context c, JSONObject r, Runnable done) {
    String id = r.optString("id");
    if (!permitted(c)) {
      Store.status(c, id, "Needs location permissions");
      if (done != null) done.run();
      return;
    }
    if (r.optBoolean("fired")) {
      if (done != null) done.run();
      return;
    }
    try {
      Geofence f =
          new Geofence.Builder()
              .setRequestId(id)
              .setCircularRegion(
                  r.getDouble("lat"), r.getDouble("lon"), (float) r.getDouble("radius"))
              .setExpirationDuration(Geofence.NEVER_EXPIRE)
              .setTransitionTypes(
                  Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL)
              .setLoiteringDelay(10000)
              .setNotificationResponsiveness(5000)
              .build();
      LocationServices.getGeofencingClient(c)
          .addGeofences(
              new GeofencingRequest.Builder()
                  .setInitialTrigger(
                      GeofencingRequest.INITIAL_TRIGGER_ENTER
                          | GeofencingRequest.INITIAL_TRIGGER_DWELL)
                  .addGeofence(f)
                  .build(),
              pending(c))
          .addOnSuccessListener(
              v -> {
                Store.status(c, id, "Active");
                Log.i("LocationReminders", "Geofence registered: " + id);
                if (done != null) done.run();
              })
          .addOnFailureListener(
              e -> {
                Store.status(
                    c,
                    id,
                    (e instanceof com.google.android.gms.common.api.ApiException
                            && ((com.google.android.gms.common.api.ApiException) e).getStatusCode()
                                == 1000
                        ? "Enable Location Accuracy in Android Settings, then retry"
                        : "Registration failed: " + e.getMessage()));
                Log.e("LocationReminders", "Registration failed", e);
                if (done != null) done.run();
              });
    } catch (SecurityException e) {
      Store.status(c, id, "Location permission was revoked. Enable it and retry.");
      if (done != null) done.run();
    } catch (Exception e) {
      Store.status(
          c,
          id,
          (e instanceof com.google.android.gms.common.api.ApiException
                  && ((com.google.android.gms.common.api.ApiException) e).getStatusCode() == 1000
              ? "Enable Location Accuracy in Android Settings, then retry"
              : "Registration failed: " + e.getMessage()));
      if (done != null) done.run();
    }
  }

  static void restore(Context c) {
    restore(c, null);
  }

  static void restore(Context c, Runnable done) {
    JSONArray a = Store.all(c);
    if (a.length() == 0) {
      if (done != null) done.run();
      return;
    }
    java.util.concurrent.atomic.AtomicInteger remaining =
        new java.util.concurrent.atomic.AtomicInteger(a.length());
    for (int i = 0; i < a.length(); i++)
      register(
          c,
          a.optJSONObject(i),
          () -> {
            if (remaining.decrementAndGet() == 0 && done != null) done.run();
          });
  }
}
