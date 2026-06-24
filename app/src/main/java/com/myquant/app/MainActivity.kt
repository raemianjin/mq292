package com.myquant.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var web: WebView
    private val TAG = "myQuant"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate v2.9.2")

        web = WebView(this)
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        // KR/US JSON을 file:// 페이지에서 직접 fetch 하기 위한 CORS 우회
        s.allowUniversalAccessFromFileURLs = true
        s.allowFileAccessFromFileURLs = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.userAgentString = s.userAgentString + " myQuant/2.9.2"

        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface
        fun getAppVersion(): String = "2.9.2"

        // 웹 로그 → Logcat (adb logcat -s myQuant 로 확인)
        @JavascriptInterface
        fun log(level: String, tag: String, msg: String) {
            when (level) {
                "ERROR" -> Log.e(tag, msg)
                "WARN"  -> Log.w(tag, msg)
                "DEBUG" -> Log.d(tag, msg)
                else    -> Log.i(tag, msg)
            }
        }

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("myQuant log", text))
                Toast.makeText(this@MainActivity, "로그 복사됨", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "myQuant 진단 로그")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(i, "로그 공유"))
            }
        }

        // 공식 시세 페이지를 외부 브라우저로 열어 실데이터 대조
        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                } catch (e: Exception) {
                    Log.e(TAG, "openUrl 실패: " + e.message)
                }
            }
        }

        // 설정 JSON을 다운로드 폴더(또는 앱 외부저장소)에 저장
        @JavascriptInterface
        fun saveJson(name: String, content: String): String {
            return try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
                        contentResolver.update(uri, cv, null, null)
                        "Download/" + name
                    } else "ERR:uri-null"
                } else {
                    val dir = getExternalFilesDir(null)
                    val f = java.io.File(dir, name)
                    f.writeText(content)
                    f.absolutePath
                }
            } catch (e: Exception) { "ERR:" + e.message }
        }

        // 시스템 파일 선택 → 내용을 window.onJsonPicked(content) 로 전달
        @JavascriptInterface
        fun pickJson() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(i, 4711)
                } catch (e: Exception) { Log.e(TAG, "pickJson 실패: " + e.message) }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4711 && resultCode == Activity.RESULT_OK && data?.data != null) {
            try {
                val txt = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: ""
                val esc = txt.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
                runOnUiThread { web.evaluateJavascript("window.onJsonPicked && window.onJsonPicked('" + esc + "')", null) }
                Log.i(TAG, "pickJson 읽기 " + txt.length + "자")
            } catch (e: Exception) { Log.e(TAG, "onActivityResult 실패: " + e.message) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 웹 시트(설정/로그/근거/상세)가 열려 있으면 먼저 닫고, 없으면 종료 확인
        web.evaluateJavascript("(window.onAndroidBack?window.onAndroidBack():false)") { res ->
            if (res != "true") {
                runOnUiThread { showExitDialog() }
            }
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("myQuant 종료")
            .setMessage("앱을 종료할까요?")
            .setPositiveButton("종료") { _, _ -> finish() }
            .setNegativeButton("취소", null)
            .show()
    }
}
