package tech.cameraanalyzer

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit

/** Offline, read-only APK analyzer. Static evidence does not establish runtime behavior or compatibility. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var output: LinearLayout
    private lateinit var analyzeButton: Button
    private lateinit var deepButton: Button
    private var lastReport = JSONArray()
    private var selectedFilter = "All"
    private val categories = listOf("All", "Camera1", "Camera2", "CameraX", "WebRTC", "Native", "Unknown")
    private val saveJson = 41
    private val saveCsv = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 22, 20, 12) }
        root.addView(TextView(this).apply { text = "Camera Analyzer"; textSize = 26f; setTextColor(0xff18324a.toInt()) })
        root.addView(TextView(this).apply { text = "Offline static analysis • No account, ads, analytics, or network"; textSize = 13f; setPadding(0, 4, 0, 12) })
        root.addView(TextView(this).apply {
            text = "${Build.MANUFACTURER} ${Build.MODEL} • Android API ${Build.VERSION.SDK_INT}\nRoot: ${if (hasSuBinary()) "available (not granted)" else "not detected"}"
            textSize = 13f
        })
        status = TextView(this).apply { text = "Ready. Static evidence cannot prove runtime camera behavior."; setPadding(0, 12, 0, 6) }
        root.addView(status)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        analyzeButton = Button(this).apply { text = "Analyze"; setOnClickListener { scan(false) } }
        deepButton = Button(this).apply { text = "Deep Analysis"; setOnClickListener { scan(true) } }
        actions.addView(analyzeButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(deepButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actions)
        val exports = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exports.addView(Button(this).apply { text = "Save JSON"; setOnClickListener { export("json") } })
        exports.addView(Button(this).apply { text = "Save CSV"; setOnClickListener { export("csv") } })
        root.addView(exports)
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedFilter = categories[position]; render() }
            }
        })
        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun hasSuBinary() = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su").any { File(it).exists() }

    private fun scan(deep: Boolean) {
        analyzeButton.isEnabled = false; deepButton.isEnabled = false
        status.text = if (deep) "Requesting optional root permission for package inventory…" else "Scanning installed APKs locally…"
        Thread {
            val results = JSONArray()
            val apps = try {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA).filter { it.packageName != packageName }.sortedBy { it.packageName }
            } catch (_: Exception) { emptyList() }
            val rootPackages = if (deep) rootPackageInventory() else emptySet()
            apps.forEachIndexed { index, app ->
                results.put(analyze(app))
                if (index % 8 == 0) runOnUiThread { status.text = "Analyzing ${index + 1}/${apps.size} applications…" }
            }
            runOnUiThread {
                lastReport = results
                status.text = "Finished: ${apps.size} apps inspected; ${rootPackages.size} additional root-visible package names. APK contents remain PackageManager-accessible only."
                render(); analyzeButton.isEnabled = true; deepButton.isEnabled = true
            }
        }.start()
    }

    /** su is invoked only after explicit Deep Analysis tap; command is read-only package listing. */
    private fun rootPackageInventory(): Set<String> = try {
        val process = ProcessBuilder("su", "-c", "pm list packages").redirectErrorStream(true).start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); emptySet() }
        else if (process.exitValue() != 0) emptySet() else process.inputStream.bufferedReader().useLines { lines ->
            lines.take(10000).mapNotNull { line -> line.removePrefix("package:").trim().takeIf { it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) } }.toSet()
        }
    } catch (_: Exception) { emptySet() }

    private fun analyze(app: ApplicationInfo): JSONObject {
        val found = linkedMapOf<String, MutableList<String>>()
        val paths = listOfNotNull(app.sourceDir) + (app.splitSourceDirs?.toList() ?: emptyList())
        val markers = linkedMapOf(
            "Camera1" to listOf("android/hardware/Camera", "Landroid/hardware/Camera;"),
            "Camera2" to listOf("android/hardware/camera2", "Landroid/hardware/camera2/"),
            "CameraX" to listOf("androidx/camera", "Landroidx/camera/"),
            "WebRTC" to listOf("org/webrtc", "Lorg/webrtc/"),
            "Native" to listOf("ACameraManager", "ACameraDevice", "libcamera", "libwebrtc")
        )
        var dexCount = 0; var unreadable = 0; var nativeCount = 0
        for (path in paths) try {
            ZipFile(File(path)).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement(); if (entry.isDirectory) continue
                    if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) {
                        nativeCount++
                        val n = entry.name.lowercase()
                        if ("camera" in n || "webrtc" in n) found.getOrPut(if ("webrtc" in n) "WebRTC" else "Native") { mutableListOf() }.add("${entry.name} (native library name)")
                    }
                    if (!entry.name.matches(Regex("classes(\\d*)\\.dex"))) continue
                    val needles = markers.values.flatten().toSet(); val matches = mutableSetOf<String>()
                    val buffer = ByteArray(128 * 1024); var carry = ""; var total = 0
                    zip.getInputStream(entry).use { input ->
                        while (total < 32 * 1024 * 1024) {
                            val count = input.read(buffer, 0, minOf(buffer.size, 32 * 1024 * 1024 - total)); if (count <= 0) break
                            val block = carry + String(buffer, 0, count, Charsets.ISO_8859_1); total += count
                            for (needle in needles) if (needle in block) matches.add(needle)
                            carry = block.takeLast(128)
                        }
                    }
                    dexCount++
                    for ((technology, tokens) in markers) for (token in tokens) if (token in matches) found.getOrPut(technology) { mutableListOf() }.add("${entry.name}: $token")
                }
            }
        } catch (_: Exception) { unreadable++ }
        val technologies = found.keys.toList()
        val classification = when { unreadable > 0 && dexCount == 0 -> "Unable to analyze"; technologies.isNotEmpty() -> "Possible camera implementation"; else -> "No recognizable camera indicators" }
        val evidence = found.flatMap { (tech, items) -> items.distinct().take(8).map { "$tech: $it" } }
        return JSONObject().apply {
            put("app", try { packageManager.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName })
            put("package", app.packageName)
            put("version", try { @Suppress("DEPRECATION") packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown" } catch (_: Exception) { "unknown" })
            put("version_code", try { if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(app.packageName, 0).longVersionCode else @Suppress("DEPRECATION") packageManager.getPackageInfo(app.packageName, 0).versionCode.toLong() } catch (_: Exception) { -1 })
            put("android_api", Build.VERSION.SDK_INT); put("device_manufacturer", Build.MANUFACTURER); put("device_model", Build.MODEL)
            put("technologies", JSONArray(technologies)); put("indicators", JSONArray(if (technologies.isEmpty()) listOf("Unknown") else technologies))
            put("evidence", JSONArray(evidence)); put("classification", classification)
            put("confidence_explanation", if (technologies.isNotEmpty()) "Static references or library names were found; reachability and runtime use are unverified." else "No known strings were found in readable APK DEX/native library names; obfuscation or dynamic code may hide usage.")
            put("apks_scanned_dex", dexCount); put("apks_unreadable", unreadable); put("native_libraries_seen", nativeCount)
            put("limitations", "Static indicators do not prove runtime camera behavior, framework selection, or Privacycam compatibility. APKs and code loaded dynamically may be missed.")
        }
    }

    private fun render() {
        if (!::output.isInitialized) return
        output.removeAllViews()
        for (i in 0 until lastReport.length()) {
            val row = lastReport.getJSONObject(i)
            val techs = row.getJSONArray("technologies").let { a -> (0 until a.length()).map { a.getString(it) } }
            if (selectedFilter != "All" && !(if (selectedFilter == "Unknown") techs.isEmpty() else selectedFilter in techs)) continue
            val title = TextView(this).apply {
                text = "${row.optString("app")} • ${row.optString("classification")}\n${row.optString("package")} v${row.optString("version")}\n${if (techs.isEmpty()) "Unknown" else techs.joinToString()}"
                textSize = 15f; setPadding(10, 12, 8, 10)
            }
            val detail = TextView(this).apply {
                val evidence = row.getJSONArray("evidence")
                text = "${row.optString("confidence_explanation")}\n\nEvidence:\n" + (0 until evidence.length()).joinToString("\n") { "• ${evidence.getString(it)}" } + "\n\n${row.optString("limitations")}"
                textSize = 12f; visibility = View.GONE; setPadding(18, 0, 12, 12)
            }
            title.setOnClickListener { detail.visibility = if (detail.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            output.addView(title); output.addView(detail); output.addView(View(this).apply { setBackgroundColor(0xffdddddd.toInt()) }, LinearLayout.LayoutParams(-1, 1))
        }
    }

    private fun export(format: String) {
        if (lastReport.length() == 0) { Toast.makeText(this, "Analyze apps first", Toast.LENGTH_SHORT).show(); return }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = if (format == "json") "application/json" else "text/csv"
            putExtra(Intent.EXTRA_TITLE, "camera-analyzer-results.$format")
        }
        startActivityForResult(intent, if (format == "json") saveJson else saveCsv)
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val content = if (requestCode == saveJson) lastReport.toString(2) else toCsv()
            contentResolver.openOutputStream(data.data!!)?.bufferedWriter()?.use { it.write(content) }
            Toast.makeText(this, "Report saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun toCsv(): String {
        val rows = mutableListOf("app,package,version,android_api,manufacturer,model,technologies,classification,evidence,limitations")
        for (i in 0 until lastReport.length()) {
            val o = lastReport.getJSONObject(i)
            val tech = o.getJSONArray("technologies").let { a -> (0 until a.length()).joinToString("|") { a.getString(it) } }
            val evidence = o.getJSONArray("evidence").let { a -> (0 until a.length()).joinToString(" | ") { a.getString(it) } }
            rows.add(listOf(o.optString("app"), o.optString("package"), o.optString("version"), o.optString("android_api"), o.optString("device_manufacturer"), o.optString("device_model"), tech, o.optString("classification"), evidence, o.optString("limitations")).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
        return rows.joinToString("\n")
    }
}
