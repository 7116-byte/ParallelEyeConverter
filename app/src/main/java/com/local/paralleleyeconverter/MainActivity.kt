package com.local.paralleleyeconverter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_OWNER = "7116-byte"
private const val GITHUB_REPO = "ParallelEyeConverter"
private const val CURRENT_VERSION = "0.1.0"

private const val TEXT_APP_NAME = "\u5b9e\u65f6\u5e73\u884c\u773c\u8f6c\u5316"
private const val TEXT_DESCRIPTION =
    "\u628a\u5f53\u524d\u5c4f\u5e55\u5b9e\u65f6\u8f6c\u5316\u4e3a\u5de6\u53f3\u773c\u5e73\u884c\u753b\u9762\u3002" +
        "\u5f53\u524d\u7248\u672c\u5148\u63d0\u4f9b\u53ef\u7ef4\u62a4\u7684\u52a8\u6001\u6355\u83b7\u9aa8\u67b6\uff0c" +
        "\u540e\u7eed\u63a5\u5165\u6df1\u5ea6\u89c6\u5dee\u3001\u8865\u6d1e\u548c\u6027\u80fd\u53c2\u6570\u3002"
private const val TEXT_START = "\u5f00\u59cb\u8f6c\u5316"
private const val TEXT_STOP = "\u505c\u6b62\u8f6c\u5316"
private const val TEXT_CHECK_UPDATE = "\u68c0\u67e5\u66f4\u65b0"
private const val TEXT_DOWNLOAD_UPDATE = "\u4e0b\u8f7d\u66f4\u65b0"
private const val TEXT_READY = "\u51c6\u5907\u5c31\u7eea"
private const val TEXT_NEED_OVERLAY = "\u8bf7\u5148\u6388\u4e88\u60ac\u6d6e\u7a97\u6743\u9650"
private const val TEXT_WAITING_CAPTURE = "\u7b49\u5f85\u7cfb\u7edf\u5f55\u5c4f\u6388\u6743"
private const val TEXT_STOPPED = "\u5df2\u505c\u6b62"
private const val TEXT_CHECKING = "\u6b63\u5728\u68c0\u67e5\u66f4\u65b0..."

class MainActivity : Activity() {
    private lateinit var statusText: TextView
    private lateinit var downloadButton: Button
    private var updateUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 246, 247))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            addView(TextView(context).apply {
                text = TEXT_APP_NAME
                textSize = 26f
                setTextColor(Color.rgb(20, 24, 33))
                setTypeface(typeface, Typeface.BOLD)
            }, fullWidthParams())

            addView(TextView(context).apply {
                text = TEXT_DESCRIPTION
                textSize = 15f
                setTextColor(Color.rgb(68, 74, 84))
            }, fullWidthParams(top = dp(10)))

            addView(Button(context).apply {
                text = TEXT_START
                setOnClickListener { startConversion() }
            }, fullWidthParams(top = dp(18)))

            addView(Button(context).apply {
                text = TEXT_STOP
                setOnClickListener { stopConversion() }
            }, fullWidthParams(top = dp(10)))

            val updateRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            updateRow.addView(Button(context).apply {
                text = TEXT_CHECK_UPDATE
                setOnClickListener { checkForUpdates() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
            })
            downloadButton = Button(context).apply {
                text = TEXT_DOWNLOAD_UPDATE
                isEnabled = false
                setOnClickListener {
                    updateUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
            }
            updateRow.addView(downloadButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(5)
            })
            addView(updateRow, fullWidthParams(top = dp(10)))

            statusText = TextView(context).apply {
                text = TEXT_READY
                textSize = 17f
                setTextColor(Color.rgb(20, 24, 33))
            }
            addView(statusText, fullWidthParams(top = dp(18)))
        }
    }

    private fun fullWidthParams(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }

    private fun startConversion() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = TEXT_NEED_OVERLAY
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        requestNotificationPermission()
        startActivity(Intent(this, CapturePermissionActivity::class.java))
        statusText.text = TEXT_WAITING_CAPTURE
    }

    private fun stopConversion() {
        stopService(
            Intent(this, ConverterProjectionService::class.java)
                .setAction(ConverterProjectionService.ACTION_STOP),
        )
        stopService(Intent(this, ConverterOverlayService::class.java))
        statusText.text = TEXT_STOPPED
    }

    private fun checkForUpdates() {
        statusText.text = TEXT_CHECKING
        updateUrl = null
        downloadButton.isEnabled = false
        Thread {
            val result = checkLatestRelease()
            runOnUiThread {
                statusText.text = result.message
                updateUrl = result.downloadUrl
                downloadButton.isEnabled = result.downloadUrl != null
            }
        }.start()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7201)
        }
    }
}

private data class UpdateResult(val message: String, val downloadUrl: String?)

private fun checkLatestRelease(): UpdateResult {
    return runCatching {
        fetchLatestRelease()
    }.getOrElse { error ->
        UpdateResult("\u68c0\u67e5\u66f4\u65b0\u5931\u8d25\uff1a${error.message}", null)
    }
}

private fun fetchLatestRelease(): UpdateResult {
    val api = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    val connection = (URL(api).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 12000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    val latestTag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1).orEmpty()
    val apkUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").findAll(body)
        .map { it.groupValues[1] }
        .firstOrNull()
    val pageUrl = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
    val current = "v$CURRENT_VERSION"
    val url = apkUrl ?: pageUrl
    return when {
        latestTag.isBlank() -> UpdateResult("\u6ca1\u6709\u8bfb\u5230 GitHub Release \u7248\u672c", null)
        latestTag == current -> UpdateResult("\u5df2\u662f\u6700\u65b0\u7248\u672c\uff1a$current", null)
        url != null -> UpdateResult("\u53d1\u73b0\u65b0\u7248\u672c\uff1a$latestTag\uff0c\u5f53\u524d\uff1a$current", url)
        else -> UpdateResult("\u53d1\u73b0\u65b0\u7248\u672c\uff1a$latestTag\uff0c\u4f46\u6ca1\u6709\u53ef\u6253\u5f00\u7684\u4e0b\u8f7d\u5730\u5740", null)
    }
}
