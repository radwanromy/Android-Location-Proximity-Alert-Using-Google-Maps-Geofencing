package com.radwan.locationalert;

import android.app.*;
import android.content.*;
import android.util.Log;
import com.google.android.gms.location.*;
import org.json.*;

public class GeofenceReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    GeofencingEvent e = GeofencingEvent.fromIntent(intent);
    if (e == null) return;
    if (e.hasError()) {
      Log.e("LocationReminders", "Geofence event error " + e.getErrorCode());
      return;
    }
    if (e.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER
        && e.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_DWELL) return;
    if (e.getTriggeringGeofences() == null) return;
    for (Geofence f : e.getTriggeringGeofences()) {
      JSONObject r = Store.find(c, f.getRequestId());
      if (r == null || r.optBoolean("fired") || System.currentTimeMillis() < r.optLong("after"))
        continue;
      NotificationManager n = c.getSystemService(NotificationManager.class);
      if (!n.areNotificationsEnabled()) {
        Store.status(c, f.getRequestId(), "Enable notifications");
        return;
      }
      n.createNotificationChannel(
          new NotificationChannel(
              "arrivals", "Arrival reminders", NotificationManager.IMPORTANCE_HIGH));
      PendingIntent open =
          PendingIntent.getActivity(
              c,
              0,
              new Intent(c, MainActivity.class),
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      try {
        n.notify(
            f.getRequestId().hashCode(),
            new Notification.Builder(c, "arrivals")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Arrived: " + r.optString("name"))
                .setContentText(
                    r.optString("comment").isEmpty()
                        ? "Your saved place is nearby."
                        : r.optString("comment"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
        JSONArray a = Store.all(c);
        for (int i = 0; i < a.length(); i++)
          if (f.getRequestId().equals(a.optJSONObject(i).optString("id"))) {
            a.optJSONObject(i).put("fired", true);
            a.optJSONObject(i).put("status", "Reminded");
          }
        Store.save(c, a);
        LocationServices.getGeofencingClient(c)
            .removeGeofences(java.util.Collections.singletonList(f.getRequestId()));
        Log.i("LocationReminders", "Arrival notification delivered: " + r.optString("name"));
      } catch (Exception ex) {
        Log.e("LocationReminders", "Notification failed", ex);
      }
    }
  }
}
