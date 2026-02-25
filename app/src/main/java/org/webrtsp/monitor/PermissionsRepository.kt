package org.webrtsp.monitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionsRepository @Inject constructor(
    @param:ApplicationContext val _applicationContext: Context
) {
    val postNotificationsGranted: Boolean
        get() = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                _applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    var postNotificationsRequestAllowed: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val fullScreenIntentAllowed: Boolean
        get() = NotificationManagerCompat.from(_applicationContext).canUseFullScreenIntent()

    fun requestFullScreenIntentPermission() {
        _applicationContext.startActivity(
            Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts(
                    "package",
                    _applicationContext.packageName,
                    null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
