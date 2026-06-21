package com.local.paralleleyeconverter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class CapturePermissionActivity : Activity() {
    private val requestCode = 7101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == this.requestCode && resultCode == RESULT_OK && data != null) {
            ForegroundAppHelper.markInitialTargetPackage(this)
            val service = Intent(this, ConverterProjectionService::class.java)
                .putExtra(ConverterProjectionService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ConverterProjectionService.EXTRA_RESULT_DATA, data)
            startForegroundService(service)
            startService(Intent(this, ConverterOverlayService::class.java))
        }
        finish()
    }
}
