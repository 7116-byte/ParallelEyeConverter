package com.local.paralleleyeconverter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_OWNER = "7116-byte"
private const val GITHUB_REPO = "ParallelEyeConverter"
private const val CURRENT_VERSION = "0.1.3"

private const val TEXT_APP_NAME = "\u5b9e\u65f6\u5e73\u884c\u773c\u8f6c\u5316"
private const val TEXT_SUBTITLE = "\u5f55\u5c4f\u6355\u83b7  /  \u60ac\u6d6e\u7403  /  \u5de6\u53f3\u773c\u8f93\u51fa"
private const val TEXT_DESCRIPTION =
    "\u70b9\u51fb\u5f00\u59cb\u540e\u6388\u6743\u5f55\u5c4f\uff0c\u8fdb\u5165\u8981\u8f6c\u5316\u7684 App\uff0c" +
        "\u6388\u6743\u6210\u529f\u540e\u4f1a\u76f4\u63a5\u76d6\u4e0a\u5de6\u53f3\u773c\u5e73\u884c\u753b\u9762\u3002"
private const val TEXT_START = "\u5f00\u59cb\u8f6c\u5316"
private const val TEXT_STOP = "\u505c\u6b62\u8f6c\u5316"
private const val TEXT_CHECK_UPDATE = "\u68c0\u67e5\u66f4\u65b0"
private const val TEXT_DOWNLOAD_UPDATE = "\u4e0b\u8f7d\u66f4\u65b0"
private const val TEXT_READY = "\u51c6\u5907\u5c31\u7eea"
private const val TEXT_NEED_OVERLAY = "\u8bf7\u5148\u6388\u4e88\u60ac\u6d6e\u7a97\u6743\u9650"
private const val TEXT_WAITING_CAPTURE = "\u5df2\u8bf7\u6c42\u5f55\u5c4f\u6388\u6743\uff1b\u6388\u6743\u540e\u4f1a\u76f4\u63a5\u5f00\u542f\u5e73\u884c\u773c\u753b\u9762"
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

    private fun createContentView(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(13, 18, 25))
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        root.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        content.addView(TextView(this).apply {
            text = TEXT_APP_NAME
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, fullWidthParams())

        content.addView(TextView(this).apply {
            text = TEXT_SUBTITLE
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(Color.rgb(31, 230, 168))
            gravity = Gravity.CENTER
        }, fullWidthParams(top = dp(8)))

        content.addView(TextView(this).apply {
            text = TEXT_DESCRIPTION
            textSize = 16f
            setTextColor(Color.rgb(199, 211, 224))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }, fullWidthParams(top = dp(24)))

        statusText = TextView(this).apply {
            text = TEXT_READY
            textSize = 16f
            setTextColor(Color.rgb(234, 241, 248))
            gravity = Gravity.CENTER
            background = roundedStroke(Color.rgb(21, 29, 39), Color.rgb(52, 67, 82), dp(12))
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        content.addView(statusText, fullWidthParams(top = dp(24)))

        content.addView(primaryButton(TEXT_START).apply {
            setOnClickListener { startConversion() }
        }, fullWidthParams(top = dp(22), height = dp(54)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        row.addView(secondaryButton(TEXT_STOP).apply {
            setOnClickListener { stopConversion() }
        }, weightedParams(end = dp(6)))
        row.addView(secondaryButton(TEXT_CHECK_UPDATE).apply {
            setOnClickListener { checkForUpdates() }
        }, weightedParams(start = dp(6)))
        content.addView(row, fullWidthParams(top = dp(12), height = dp(50)))

        downloadButton = secondaryButton(TEXT_DOWNLOAD_UPDATE).apply {
            isEnabled = false
            alpha = 0.45f
            setOnClickListener {
                updateUrl?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            }
        }
        content.addView(downloadButton, fullWidthParams(top = dp(12), height = dp(50)))

        content.addView(TextView(this).apply {
            text = "v$CURRENT_VERSION"
            textSize = 13f
            setTextColor(Color.rgb(101, 116, 132))
            gravity = Gravity.CENTER
        }, fullWidthParams(top = dp(22)))

        return root
    }

    private fun primaryButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(8, 18, 24))
            background = rounded(Color.rgb(31, 230, 168), dp(13))
            stateListAnimator = null
            isAllCaps = false
        }
    }

    private fun secondaryButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.rgb(232, 239, 246))
            background = roundedStroke(Color.rgb(25, 34, 45), Color.rgb(60, 78, 96), dp(12))
            stateListAnimator = null
            isAllCaps = false
        }
    }

    private fun fullWidthParams(top: Int = 0, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply { topMargin = top }

    private fun weightedParams(start: Int = 0, end: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginStart = start
            marginEnd = end
        }

    private fun startConversion() {
        if (!Settings.canDrawOverlays(this)) {
            statusText.text = TEXT_NEED_OVERLAY
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        requestNotificationPermission()
        startActivity(Intent(this, CapturePermissionActivity::class.java))
        statusText.text = TEXT_WAITING_CAPTURE
    }

    private fun stopConversion() {
        stopService(Intent(this, ConverterProjectionService::class.java).setAction(ConverterProjectionService.ACTION_STOP))
        stopService(Intent(this, ConverterOverlayService::class.java))
        statusText.text = TEXT_STOPPED
    }

    private fun checkForUpdates() {
        statusText.text = TEXT_CHECKING
        updateUrl = null
        downloadButton.isEnabled = false
        downloadButton.alpha = 0.45f
        Thread {
            val result = checkLatestRelease()
            runOnUiThread {
                statusText.text = result.message
                updateUrl = result.downloadUrl
                downloadButton.isEnabled = result.downloadUrl != null
                downloadButton.alpha = if (result.downloadUrl != null) 1f else 0.45f
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

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun roundedStroke(color: Int, stroke: Int, radius: Int): GradientDrawable {
        return rounded(color, radius).apply {
            setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
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
