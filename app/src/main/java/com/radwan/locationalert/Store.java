package com.radwan.locationalert;

import android.content.*;
import org.json.*;

public class Store {
  static JSONArray all(Context c) {
    try {
      return new JSONArray(c.getSharedPreferences("reminders", 0).getString("items", "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static void save(Context c, JSONArray a) {
    c.getSharedPreferences("reminders", 0).edit().putString("items", a.toString()).commit();
  }

  static JSONObject find(Context c, String id) {
    JSONArray a = all(c);
    for (int i = 0; i < a.length(); i++) {
      JSONObject r = a.optJSONObject(i);
      if (r != null && id.equals(r.optString("id"))) return r;
    }
    return null;
  }

  static void status(Context c, String id, String value) {
    JSONArray a = all(c);
    for (int i = 0; i < a.length(); i++)
      if (id.equals(a.optJSONObject(i).optString("id")))
        try {
          a.optJSONObject(i).put("status", value);
        } catch (Exception ignored) {
        }
    save(c, a);
  }
}
