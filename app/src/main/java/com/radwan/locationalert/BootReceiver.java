package com.radwan.locationalert;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
      PendingResult result = goAsync();
      Fences.restore(c, result::finish);
    }
  }
}
