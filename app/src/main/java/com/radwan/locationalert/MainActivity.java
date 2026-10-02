package com.radwan.locationalert;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.android.gms.location.LocationServices;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.*;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;

public class MainActivity extends Activity {
  MapView map;
  LinearLayout root;
  TextView status;
  EditText query;
  GeoPoint selected = new GeoPoint(35.681236, 139.767125);
  Marker selection;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  int teal = Color.rgb(0, 108, 104);

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    Configuration.getInstance()
        .setUserAgentValue("LocationReminders/1.0 (com.radwan.locationalert)");
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(14, 8, 14, 8);
    root.setBackgroundColor(Color.rgb(245, 250, 249));
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              14,
              insets.getSystemWindowInsetTop() + 8,
              14,
              insets.getSystemWindowInsetBottom() + 8);
          return insets;
        });
    TextView title = text("Location Reminders", 24);
    title.setTextColor(teal);
    root.addView(title);
    root.addView(text("Pick a place. Remember when you arrive.", 14));
    LinearLayout search = row();
    query = new EditText(this);
    query.setSingleLine();
    query.setHint("City, place, or latitude, longitude");
    search.addView(query, new LinearLayout.LayoutParams(0, -2, 1));
    search.addView(button("Search", v -> search()));
    root.addView(search);
    LinearLayout shortcuts = row();
    shortcuts.addView(
        button("Tokyo", v -> choose(new GeoPoint(35.681236, 139.767125), "Tokyo Station")));
    shortcuts.addView(button("Dhaka", v -> choose(new GeoPoint(23.8103, 90.4125), "Dhaka")));
    shortcuts.addView(button("My location", v -> current()));
    root.addView(shortcuts);
    map = new MapView(this);
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setMultiTouchControls(true);
    map.getController().setZoom(13.0);
    map.getController().setCenter(selected);
    MapEventsOverlay events =
        new MapEventsOverlay(
            new org.osmdroid.events.MapEventsReceiver() {
              public boolean singleTapConfirmedHelper(GeoPoint p) {
                choose(p, "Selected place");
                return true;
              }

              public boolean longPressHelper(GeoPoint p) {
                choose(p, "Selected place");
                editor();
                return true;
              }
            });
    map.getOverlays().add(events);
    root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView credit = text("© OpenStreetMap contributors", 12);
    credit.setOnClickListener(
        v ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))));
    root.addView(credit);
    status = text("", 13);
    root.addView(status);
    LinearLayout actions = row();
    actions.addView(button("Save reminder", v -> editor()));
    actions.addView(button("Reminders", v -> list()));
    actions.addView(button("Options", v -> options()));
    root.addView(actions);
    choose(selected, "Tokyo Station");
    markers();
    if (Fences.permitted(this)) Fences.restore(this);
  }

  TextView text(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setPadding(6, 4, 6, 4);
    return t;
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    return l;
  }

  Button button(String s, View.OnClickListener click) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(teal);
    b.setOnClickListener(click);
    return b;
  }

  void say(String s) {
    status.setText(s);
  }

  void choose(GeoPoint p, String name) {
    selected = p;
    if (selection == null) {
      selection = new Marker(map);
      map.getOverlays().add(selection);
    }
    selection.setPosition(p);
    selection.setTitle(name);
    map.getController().animateTo(p);
    say(
        String.format(
            Locale.US,
            "Selected: %.6f, %.6f · Tap map to change",
            p.getLatitude(),
            p.getLongitude()));
    map.invalidate();
  }

  void search() {
    String q = query.getText().toString().trim();
    if (q.isEmpty()) return;
    try {
      String[] a = q.split(",");
      if (a.length == 2) {
        double lat = Double.parseDouble(a[0].trim()), lon = Double.parseDouble(a[1].trim());
        if (!Double.isFinite(lat)
            || !Double.isFinite(lon)
            || lat < -90
            || lat > 90
            || lon < -180
            || lon > 180) throw new IllegalArgumentException();
        choose(new GeoPoint(lat, lon), "Coordinates");
        return;
      }
    } catch (NumberFormatException ignored) {
    } catch (Exception e) {
      say("Latitude must be −90…90; longitude −180…180.");
      return;
    }
    say("Searching worldwide…");
    worker.execute(
        () -> {
          try {
            java.util.List<Address> results =
                new Geocoder(this, Locale.getDefault()).getFromLocationName(q, 8);
            runOnUiThread(
                () -> {
                  if (results == null || results.isEmpty()) {
                    say("No results. Tap map or enter coordinates.");
                    return;
                  }
                  String[] labels = new String[results.size()];
                  for (int i = 0; i < labels.length; i++)
                    labels[i] = results.get(i).getAddressLine(0);
                  new AlertDialog.Builder(this)
                      .setTitle("Choose location")
                      .setItems(
                          labels,
                          (d, i) -> {
                            Address a = results.get(i);
                            choose(new GeoPoint(a.getLatitude(), a.getLongitude()), labels[i]);
                          })
                      .show();
                });
          } catch (Exception e) {
            runOnUiThread(() -> say("Search unavailable. Map and coordinates still work."));
          }
        });
  }

  void current() {
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      permissions();
      return;
    }
    LocationServices.getFusedLocationProviderClient(this)
        .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener(
            l -> {
              if (l != null) choose(new GeoPoint(l.getLatitude(), l.getLongitude()), "My location");
              else say("No location yet. Select on map or enter coordinates.");
            })
        .addOnFailureListener(e -> say("Location unavailable. Select manually."));
  }

  EditText input(String hint, String value) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setText(value);
    return e;
  }

  void editor() {
    if (Store.all(this).length() >= 100) {
      say("Android allows 100 geofences. Delete a reminder first.");
      return;
    }
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(28, 8, 28, 8);
    EditText name = input("Place name", ""),
        lat = input("Latitude", String.valueOf(selected.getLatitude())),
        lon = input("Longitude", String.valueOf(selected.getLongitude())),
        radius = input("Radius in meters (100–10000)", "500"),
        comment = input("Optional comment", "");
    form.addView(name);
    form.addView(lat);
    form.addView(lon);
    form.addView(radius);
    form.addView(comment);
    long[] after = {0};
    Button when =
        button(
            "Eligible immediately · change date/time",
            v -> {
              Calendar cal = Calendar.getInstance();
              new DatePickerDialog(
                      this,
                      (dp, y, m, d) -> {
                        cal.set(y, m, d);
                        new TimePickerDialog(
                                this,
                                (tp, h, min) -> {
                                  cal.set(Calendar.HOUR_OF_DAY, h);
                                  cal.set(Calendar.MINUTE, min);
                                  cal.set(Calendar.SECOND, 0);
                                  after[0] = cal.getTimeInMillis();
                                  ((Button) v)
                                      .setText(
                                          "After "
                                              + new java.text.SimpleDateFormat(
                                                      "yyyy-MM-dd HH:mm", Locale.getDefault())
                                                  .format(cal.getTime()));
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                true)
                            .show();
                      },
                      cal.get(Calendar.YEAR),
                      cal.get(Calendar.MONTH),
                      cal.get(Calendar.DAY_OF_MONTH))
                  .show();
            });
    form.addView(when);
    form.addView(
        text(
            "Alerts on arrival after the chosen time. Allow precise location, ‘Allow all the time’,"
                + " and notifications.",
            13));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(form);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("New arrival reminder")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        double la = Double.parseDouble(lat.getText().toString()),
                            lo = Double.parseDouble(lon.getText().toString()),
                            ra = Double.parseDouble(radius.getText().toString());
                        if (!Double.isFinite(la)
                            || !Double.isFinite(lo)
                            || !Double.isFinite(ra)
                            || la < -90
                            || la > 90
                            || lo < -180
                            || lo > 180
                            || ra < 100
                            || ra > 10000)
                          throw new IllegalArgumentException(
                              "Check coordinates and radius (100–10000 m).");
                        if (name.getText().toString().trim().isEmpty())
                          throw new IllegalArgumentException("Enter a place name.");
                        if (after[0] != 0 && after[0] <= System.currentTimeMillis())
                          throw new IllegalArgumentException("Choose a future date/time.");
                        JSONObject r =
                            new JSONObject()
                                .put("id", UUID.randomUUID().toString())
                                .put("name", name.getText().toString().trim())
                                .put("lat", la)
                                .put("lon", lo)
                                .put("radius", ra)
                                .put("comment", comment.getText().toString())
                                .put("after", after[0])
                                .put("status", "Registering");
                        JSONArray a = Store.all(this);
                        a.put(r);
                        Store.save(this, a);
                        Fences.register(
                            this,
                            r,
                            () -> {
                              say(Store.find(this, r.optString("id")).optString("status"));
                              markers();
                            });
                        dialog.dismiss();
                        markers();
                        if (!Fences.permitted(this)) permissions();
                      } catch (Exception ex) {
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                      }
                    }));
    dialog.show();
  }

  void markers() {
    map.getOverlays().removeIf(o -> o instanceof Marker && o != selection || o instanceof Polygon);
    JSONArray a = Store.all(this);
    for (int i = 0; i < a.length(); i++) {
      JSONObject r = a.optJSONObject(i);
      GeoPoint p = new GeoPoint(r.optDouble("lat"), r.optDouble("lon"));
      Marker m = new Marker(map);
      m.setPosition(p);
      m.setTitle(r.optString("name"));
      m.setSnippet(r.optString("status"));
      map.getOverlays().add(m);
      Polygon circle = new Polygon(map);
      circle.setPoints(Polygon.pointsAsCircle(p, r.optDouble("radius")));
      circle.getFillPaint().setColor(0x22006C68);
      circle.getOutlinePaint().setColor(teal);
      circle.getOutlinePaint().setStrokeWidth(2);
      map.getOverlays().add(circle);
    }
    map.invalidate();
  }

  void list() {
    JSONArray a = Store.all(this);
    if (a.length() == 0) {
      say("No reminders yet. Pick a place and save one.");
      return;
    }
    String[] labels = new String[a.length()];
    for (int i = 0; i < labels.length; i++) {
      JSONObject r = a.optJSONObject(i);
      labels[i] =
          r.optString("name")
              + " · "
              + r.optString("status")
              + "\n"
              + r.optDouble("lat")
              + ", "
              + r.optDouble("lon")
              + " · "
              + r.optInt("radius")
              + " m"
              + (r.optLong("after") > 0
                  ? "\nAfter "
                      + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                          .format(new Date(r.optLong("after")))
                  : "");
    }
    new AlertDialog.Builder(this)
        .setTitle("Saved reminders · available offline")
        .setItems(
            labels,
            (d, i) -> {
              JSONObject r = a.optJSONObject(i);
              new AlertDialog.Builder(this)
                  .setTitle(r.optString("name"))
                  .setMessage(labels[i] + "\n" + r.optString("comment"))
                  .setPositiveButton(
                      "Show on map",
                      (dd, w) ->
                          choose(
                              new GeoPoint(r.optDouble("lat"), r.optDouble("lon")),
                              r.optString("name")))
                  .setNeutralButton(
                      "Retry activation",
                      (dd, w) ->
                          Fences.register(
                              this,
                              r,
                              () -> say(Store.find(this, r.optString("id")).optString("status"))))
                  .setNegativeButton(
                      "Delete",
                      (dd, w) -> {
                        LocationServices.getGeofencingClient(this)
                            .removeGeofences(Collections.singletonList(r.optString("id")));
                        JSONArray kept = new JSONArray();
                        JSONArray latest = Store.all(this);
                        for (int j = 0; j < latest.length(); j++)
                          if (!r.optString("id").equals(latest.optJSONObject(j).optString("id")))
                            kept.put(latest.optJSONObject(j));
                        Store.save(this, kept);
                        markers();
                      })
                  .show();
            })
        .show();
  }

  void permissions() {
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      ArrayList<String> p = new ArrayList<>();
      p.add(Manifest.permission.ACCESS_FINE_LOCATION);
      p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
      if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
      requestPermissions(p.toArray(new String[0]), 10);
      return;
    }
    if (Build.VERSION.SDK_INT >= 29 && !Fences.permitted(this)) {
      new AlertDialog.Builder(this)
          .setTitle("Enable arrival reminders")
          .setMessage(
              "In Permissions → Location, choose ‘Allow all the time’ and enable precise location."
                  + " Saved places remain available without permission.")
          .setPositiveButton(
              "Open app settings",
              (d, w) ->
                  startActivity(
                      new Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:" + getPackageName()))))
          .setNegativeButton("Later", null)
          .show();
      return;
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 11);
    else {
      Fences.restore(this);
      say("Location permissions ready. Saved reminders are being activated.");
    }
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 10) {
      if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
          == PackageManager.PERMISSION_GRANTED) permissions();
      else
        say(
            "Precise location is needed for arrival alerts. Manual selection and saving still"
                + " work.");
    }
  }

  void options() {
    new AlertDialog.Builder(this)
        .setTitle("Map and reminder options")
        .setItems(
            new String[] {
              "OpenStreetMap (default, no key)",
              "Google Maps (optional, open selected place)",
              "Geoapify tiles (optional API key)",
              "Enable location / notification permissions"
            },
            (d, i) -> {
              if (i == 0) {
                map.setTileSource(TileSourceFactory.MAPNIK);
                say("OpenStreetMap · no API key required");
              }
              if (i == 1)
                startActivity(
                    new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://www.google.com/maps/search/?api=1&query="
                                + selected.getLatitude()
                                + ","
                                + selected.getLongitude())));
              if (i == 2) {
                EditText key = input("Geoapify API key", "");
                key.setInputType(129);
                new AlertDialog.Builder(this)
                    .setTitle("Optional Geoapify tiles")
                    .setMessage(
                        "Use your existing key. OpenStreetMap works without one. No account or"
                            + " payment is needed for this app.")
                    .setView(key)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                        "Use",
                        (dd, w) -> {
                          String k = key.getText().toString().trim();
                          if (k.isEmpty()) {
                            say("No key entered. OpenStreetMap remains available.");
                            return;
                          }
                          map.setTileSource(
                              new OnlineTileSourceBase(
                                  "Geoapify",
                                  0,
                                  20,
                                  256,
                                  ".png",
                                  new String[] {"https://maps.geoapify.com/v1/tile/osm-carto/"}) {
                                public String getTileURLString(long index) {
                                  return getBaseUrl()
                                      + org.osmdroid.util.MapTileIndex.getZoom(index)
                                      + "/"
                                      + org.osmdroid.util.MapTileIndex.getX(index)
                                      + "/"
                                      + org.osmdroid.util.MapTileIndex.getY(index)
                                      + ".png?apiKey="
                                      + Uri.encode(k);
                                }
                              });
                          say("Geoapify · © OpenStreetMap contributors");
                        })
                    .show();
              }
              if (i == 3) permissions();
            })
        .show();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (map != null) {
      map.onResume();
      markers();
    }
    if (Fences.permitted(this)) {
      JSONArray a = Store.all(this);
      for (int i = 0; i < a.length(); i++) {
        JSONObject r = a.optJSONObject(i);
        if (!r.optBoolean("fired") && !"Active".equals(r.optString("status")))
          Fences.register(
              this,
              r,
              () -> {
                markers();
                say(Store.find(this, r.optString("id")).optString("status"));
              });
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    map.onPause();
  }

  @Override
  public void onDestroy() {
    map.onDetach();
    worker.shutdownNow();
    super.onDestroy();
  }
}
