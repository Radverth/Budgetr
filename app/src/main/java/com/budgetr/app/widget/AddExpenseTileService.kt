package com.budgetr.app.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.budgetr.app.MainActivity

/** Optional tile: the user adds it from Android's Quick Settings editor. */
class AddExpenseTileService : TileService() {
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent = Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_ADD_EXPENSE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
