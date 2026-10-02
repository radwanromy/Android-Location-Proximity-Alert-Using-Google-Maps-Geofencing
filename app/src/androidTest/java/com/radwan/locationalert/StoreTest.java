package com.radwan.locationalert;

import static org.junit.Assert.*;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StoreTest {
  Context context;

  @Before
  public void setup() {
    context =
        new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
          @Override
          public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences("test_" + name, mode);
          }
        };
    context.getSharedPreferences("reminders", 0).edit().clear().commit();
  }

  @After
  public void cleanup() {
    context.getSharedPreferences("reminders", 0).edit().clear().commit();
  }

  @Test
  public void persistedRemindersKeepWorldwideCoordinatesAndNotes() throws Exception {
    JSONArray a = new JSONArray();
    a.put(
        new JSONObject()
            .put("id", "japan")
            .put("lat", 35.681236)
            .put("lon", 139.767125)
            .put("radius", 500)
            .put("name", "東京駅")
            .put("comment", "買い物"));
    a.put(
        new JSONObject()
            .put("id", "bangladesh")
            .put("lat", 23.8103)
            .put("lon", 90.4125)
            .put("name", "ঢাকা"));
    a.put(new JSONObject().put("id", "western").put("lat", -33.86).put("lon", -70.66));
    Store.save(context, a);
    assertEquals(3, Store.all(context).length());
    assertEquals("東京駅", Store.find(context, "japan").getString("name"));
    assertEquals(500, Store.find(context, "japan").getInt("radius"));
    assertEquals(-70.66, Store.find(context, "western").getDouble("lon"), 0.000001);
    assertEquals("ঢাকা", Store.find(context, "bangladesh").getString("name"));
  }

  @Test
  public void statusUpdatesPreserveOtherRemindersAndDeletionPersists() throws Exception {
    Store.save(
        context,
        new JSONArray()
            .put(new JSONObject().put("id", "a").put("name", "Keep"))
            .put(new JSONObject().put("id", "b").put("name", "Delete")));
    Store.status(context, "a", "Active");
    assertEquals("Active", Store.find(context, "a").getString("status"));
    assertEquals("Delete", Store.find(context, "b").getString("name"));
    Store.save(context, new JSONArray().put(Store.find(context, "a")));
    assertNull(Store.find(context, "b"));
    assertEquals(1, Store.all(context).length());
  }

  @Test
  public void malformedStorageRecoversAndUnknownIdsAreSafe() {
    context.getSharedPreferences("reminders", 0).edit().putString("items", "invalid").commit();
    assertEquals(0, Store.all(context).length());
    assertNull(Store.find(context, "missing"));
    Store.status(context, "missing", "Active");
    assertEquals(0, Store.all(context).length());
  }
}
