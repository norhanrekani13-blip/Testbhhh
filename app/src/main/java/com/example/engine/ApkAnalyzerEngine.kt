package com.example.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import com.example.model.ApkAnalysisResult
import com.example.model.ApkFileEntry
import com.example.model.FileCategory
import com.example.model.FilePreviewContent
import com.example.model.InstalledAppItem
import com.example.model.TechBadgeType
import com.example.model.TechDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

class ApkAnalyzerEngine(private val context: Context) {

    private val iconCache = LruCache<String, Bitmap>(160)

    suspend fun loadAllInstalledApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val apps = ArrayList<InstalledAppItem>(packages.size)
        for (pInfo in packages) {
            val appInfo = pInfo.applicationInfo ?: continue
            val pkg = pInfo.packageName ?: continue
            val sourceDir = appInfo.sourceDir ?: continue
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || !apkFile.canRead()) continue

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

            val label = runCatching {
                appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: pkg

            apps.add(
                InstalledAppItem(
                    appName = label,
                    packageName = pkg,
                    versionName = pInfo.versionName ?: "1.0",
                    apkPath = sourceDir,
                    apkSizeBytes = apkFile.length(),
                    isSystemApp = isSystem,
                    icon = null
                )
            )
        }

        apps.sortedWith(
            compareByDescending<InstalledAppItem> {
                it.packageName.contains("edit", ignoreCase = true) ||
                    it.appName.contains("quick", ignoreCase = true)
            }.thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    suspend fun loadAppIconBitmap(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName)?.let { return@withContext it }
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return@withContext null
        val bmp = drawableToBitmap(drawable) ?: return@withContext null
        iconCache.put(packageName, bmp)
        bmp
    }

    suspend fun copyUriToInspectableApk(uri: Uri): File = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "imported_app.apk"
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val cacheDir = File(context.cacheDir, "inspected_apks").apply { mkdirs() }
        // Clean old temp APKs to keep cache lean
        cacheDir.listFiles()?.forEach { old ->
            if (System.currentTimeMillis() - old.lastModified() > 60 * 60 * 1000L) {
                old.delete()
            }
        }
        val destFile = File(cacheDir, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: throw IllegalStateException("Cannot open selected file")

        // If the user uploaded an .apks / .xapk / .zip bundle containing base.apk, extract the APK
        if (safeName.endsWith(".apks", ignoreCase = true) ||
            safeName.endsWith(".xapk", ignoreCase = true) ||
            safeName.endsWith(".zip", ignoreCase = true)
        ) {
            val extractedApk = tryExtractApkFromBundle(destFile, cacheDir)
            if (extractedApk != null) {
                destFile.delete()
                return@withContext extractedApk
            }
        }
        destFile
    }

    private fun tryExtractApkFromBundle(bundleFile: File, cacheDir: File): File? {
        return try {
            ZipFile(bundleFile).use { zf ->
                val entries = zf.entries().toList()
                val baseEntry = entries.firstOrNull {
                    it.name.equals("base.apk", ignoreCase = true)
                } ?: entries.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: return null

                val outApk = File(cacheDir, "extracted_${System.currentTimeMillis()}.apk")
                zf.getInputStream(baseEntry).use { input ->
                    FileOutputStream(outApk).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                outApk
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun analyzeApk(
        apkPath: String,
        fallbackAppName: String? = null,
        fallbackIcon: Drawable? = null
    ): ApkAnalysisResult = withContext(Dispatchers.IO) {
        val file = File(apkPath)
        require(file.exists() && file.canRead()) { "APK file cannot be read: $apkPath" }

        val pm = context.packageManager
        val archiveInfo = getPackageArchiveInfoCompat(pm, apkPath)
        val appInfo = archiveInfo?.applicationInfo?.apply {
            sourceDir = apkPath
            publicSourceDir = apkPath
        }

        val resolvedName = appInfo?.let {
            runCatching { it.loadLabel(pm).toString() }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: fallbackAppName ?: file.nameWithoutExtension

        val resolvedIcon = appInfo?.let {
            runCatching { it.loadIcon(pm) }.getOrNull()
        } ?: fallbackIcon

        val pkgName = archiveInfo?.packageName ?: appInfo?.packageName ?: "unknown.archive"
        val versionName = archiveInfo?.versionName ?: "1.0"
        val versionCode = if (archiveInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode
            else @Suppress("DEPRECATION") archiveInfo.versionCode.toLong()
        } else 1L

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 21
        } else 21
        val targetSdk = appInfo?.targetSdkVersion ?: 34

        val permissions = archiveInfo?.requestedPermissions?.toList().orEmpty()
        val activities = archiveInfo?.activities?.map { it.name }.orEmpty()
        val services = archiveInfo?.services?.map { it.name }.orEmpty()

        ZipFile(file).use { zip ->
            val entries = mutableListOf<ApkFileEntry>()
            val dexBytesList = mutableListOf<Pair<String, ByteArray>>()
            var decodedManifestXml = "<!-- AndroidManifest.xml not found in archive -->"
            val abis = linkedSetOf<String>()

            val zipEnum = zip.entries()
            while (zipEnum.hasMoreElements()) {
                val ze = zipEnum.nextElement()
                if (ze.isDirectory) continue
                val path = ze.name
                val name = path.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                val category = classifyEntry(path, ext)
                val isBinXml = ext == "xml" && (
                    path == "AndroidManifest.xml" ||
                        path.startsWith("res/")
                    )

                entries.add(
                    ApkFileEntry(
                        path = path,
                        name = name,
                        sizeBytes = ze.size.coerceAtLeast(0L),
                        compressedSizeBytes = ze.compressedSize.coerceAtLeast(0L),
                        category = category,
                        extension = ext,
                        isBinaryXml = isBinXml
                    )
                )

                if (path == "AndroidManifest.xml") {
                    val manifestBytes = zip.getInputStream(ze).use { it.readBytes() }
                    decodedManifestXml = BinaryXmlDecoder.decode(manifestBytes)
                }

                if (path.startsWith("lib/") && ext == "so") {
                    val parts = path.split('/')
                    if (parts.size >= 3) abis.add(parts[1])
                }

                // Read up to 4 DEX files (or <= 16MB each) for deep bytecode inspection
                if (ext == "dex" && !path.contains('/') && dexBytesList.size < 4 && ze.size in 112..18_000_000) {
                    val dexBytes = zip.getInputStream(ze).use { it.readBytes() }
                    dexBytesList.add(path to dexBytes)
                }
            }

            entries.sortWith(compareBy<ApkFileEntry> { it.category.ordinal }.thenBy { it.path })

            val dexResult = DexInspector.inspectMultipleDex(dexBytesList)
            val techDetections = detectTechnologies(entries, dexResult.detectedFrameworks, abis)

            val htmlReport = HtmlReportGenerator.generateHtmlReport(
                appName = resolvedName,
                packageName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                apkSizeBytes = file.length(),
                techDetections = techDetections,
                decodedManifestXml = decodedManifestXml,
                permissions = permissions,
                activities = activities,
                nativeAbis = abis.toList(),
                entries = entries,
                dexAnalysis = dexResult,
                zipFile = zip
            )

            ApkAnalysisResult(
                apkFilePath = apkPath,
                appName = resolvedName,
                packageName = pkgName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                apkSizeBytes = file.length(),
                appIconBitmap = drawableToBitmap(resolvedIcon),
                techDetections = techDetections,
                decodedManifestXml = decodedManifestXml,
                permissions = permissions,
                activities = activities,
                services = services,
                nativeAbis = abis.toList(),
                entries = entries,
                dexAnalysis = dexResult,
                generatedHtmlReport = htmlReport
            )
        }
    }

    suspend fun readEntryPreview(apkPath: String, entry: ApkFileEntry): FilePreviewContent =
        withContext(Dispatchers.IO) {
            try {
                ZipFile(File(apkPath)).use { zip ->
                    val ze = zip.getEntry(entry.path)
                        ?: return@withContext FilePreviewContent.Error("Entry not found: ${entry.path}")

                    if (entry.extension in IMAGE_EXTENSIONS) {
                        if (entry.sizeBytes > 8_000_000) {
                            return@withContext FilePreviewContent.BinarySummary(
                                entry.path,
                                "Image file (${HtmlReportGenerator.formatBytes(entry.sizeBytes)})"
                            )
                        }
                        val bytes = zip.getInputStream(ze).use { it.readBytes() }
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        return@withContext if (bmp != null) {
                            FilePreviewContent.ImagePreview(entry.path, bmp, bmp.width, bmp.height)
                        } else {
                            FilePreviewContent.BinarySummary(entry.path, "Compressed image asset")
                        }
                    }

                    if (entry.extension == "dex") {
                        if (entry.sizeBytes > 24_000_000) {
                            return@withContext FilePreviewContent.BinarySummary(
                                entry.path,
                                "Large DEX bytecode (${HtmlReportGenerator.formatBytes(entry.sizeBytes)})"
                            )
                        }
                        val bytes = zip.getInputStream(ze).use { it.readBytes() }
                        val text = DexInspector.formatDexFilePreview(entry.path, bytes)
                        return@withContext FilePreviewContent.TextCode(entry.path, text, isHtml = false)
                    }

                    if (entry.extension == "so") {
                        val header = ByteArray(64)
                        val readCount = zip.getInputStream(ze).use { it.read(header) }
                        val elfInfo = inspectElfHeader(header, readCount, entry.sizeBytes)
                        return@withContext FilePreviewContent.BinarySummary(entry.path, elfInfo)
                    }

                    if (entry.extension == "xml") {
                        val maxRead = minOf(entry.sizeBytes, 2_000_000L).toInt().coerceAtLeast(1024)
                        val bytes = zip.getInputStream(ze).use { stream ->
                            val raw = stream.readBytes()
                            if (raw.size > maxRead) raw.copyOf(maxRead) else raw
                        }
                        val decoded = BinaryXmlDecoder.decode(bytes)
                        return@withContext FilePreviewContent.TextCode(entry.path, decoded, isHtml = false)
                    }

                    // Text, HTML, JS, CSS, JSON, Properties, SVG, etc.
                    val isKnownText = entry.extension in TEXT_EXTENSIONS ||
                        entry.path.startsWith("META-INF/") && entry.extension in setOf("mf", "sf", "version", "kotlin_module")

                    if (isKnownText || entry.sizeBytes <= 512_000) {
                        val raw = zip.getInputStream(ze).use { it.readBytes() }
                        val limit = 250_000
                        val truncated = raw.size > limit
                        val slice = if (truncated) raw.copyOf(limit) else raw
                        // Check if mostly printable UTF-8 text
                        if (isProbablyText(slice)) {
                            val text = String(slice, Charsets.UTF_8)
                            val isHtml = entry.extension in setOf("html", "htm", "xhtml")
                            return@withContext FilePreviewContent.TextCode(
                                path = entry.path,
                                text = text,
                                isHtml = isHtml,
                                isNoticeTruncated = truncated
                            )
                        }
                    }

                    FilePreviewContent.BinarySummary(
                        entry.path,
                        "Binary file • Extension: .${entry.extension} • Size: ${HtmlReportGenerator.formatBytes(entry.sizeBytes)}"
                    )
                }
            } catch (e: Exception) {
                FilePreviewContent.Error(e.message ?: "Failed to read file")
            }
        }

    suspend fun extractEntryRawBytes(apkPath: String, entryPath: String): ByteArray =
        withContext(Dispatchers.IO) {
            ZipFile(File(apkPath)).use { zip ->
                val ze = zip.getEntry(entryPath) ?: throw IllegalStateException("Entry missing")
                val raw = zip.getInputStream(ze).use { it.readBytes() }
                if (entryPath.endsWith(".xml", ignoreCase = true) && BinaryXmlDecoder.isBinaryXml(raw)) {
                    BinaryXmlDecoder.decode(raw).toByteArray(Charsets.UTF_8)
                } else {
                    raw
                }
            }
        }

    private fun detectTechnologies(
        entries: List<ApkFileEntry>,
        dexFrameworks: Set<String>,
        abis: Set<String>
    ): List<TechDetection> {
        val detections = mutableListOf<TechDetection>()

        val kotlinModules = entries.filter { it.path.endsWith(".kotlin_module") || it.path.startsWith("kotlin/") }
        val hasKotlin = "Kotlin" in dexFrameworks || kotlinModules.isNotEmpty()
        val hasCompose = "Jetpack Compose" in dexFrameworks
        val flutterFiles = entries.filter { it.path.contains("libflutter.so") || it.path.contains("flutter_assets") }
        val rnFiles = entries.filter { it.path.contains("index.android.bundle") || it.path.contains("libreactnativejni.so") || it.path.contains("libhermes.so") }
        val webFiles = entries.filter { it.extension in setOf("html", "htm", "js", "css") || it.path.startsWith("assets/www/") }
        val unityFiles = entries.filter { it.path.contains("libunity.so") || it.path.contains("libil2cpp.so") }
        val soFiles = entries.filter { it.extension == "so" }
        val dexFiles = entries.filter { it.extension == "dex" }
        val layoutXmls = entries.filter { it.path.startsWith("res/layout") }

        if (flutterFiles.isNotEmpty() || "Flutter" in dexFrameworks) {
            detections.add(
                TechDetection(
                    name = "Flutter / Dart",
                    subtitleKu = "ئەپ ب فلاتەر (Dart + Skia/Impeller) هاتیە دروستکرن",
                    subtitleEn = "Cross-platform UI compiled with Dart & Flutter Engine",
                    confidencePercent = 98,
                    badgeType = TechBadgeType.FLUTTER,
                    isPrimary = true,
                    evidence = flutterFiles.take(4).map { it.path }.ifEmpty { listOf("io.flutter.* classes in DEX") }
                )
            )
        }

        if (rnFiles.isNotEmpty() || "React Native" in dexFrameworks) {
            detections.add(
                TechDetection(
                    name = "React Native (JavaScript)",
                    subtitleKu = "ب React Native و JavaScript/Hermes هاتیە دروستکرن",
                    subtitleEn = "JavaScript bundle running on Hermes / React Native bridge",
                    confidencePercent = 96,
                    badgeType = TechBadgeType.REACT_NATIVE,
                    isPrimary = true,
                    evidence = rnFiles.take(4).map { it.path }.ifEmpty { listOf("com.facebook.react.* in DEX") }
                )
            )
        }

        if (unityFiles.isNotEmpty() || "Unity" in dexFrameworks) {
            detections.add(
                TechDetection(
                    name = "Unity Engine (C# / IL2CPP)",
                    subtitleKu = "یاری یان بەرنامێ Unity ب زمانێ C#",
                    subtitleEn = "Unity 3D/2D runtime with IL2CPP compiled C#",
                    confidencePercent = 98,
                    badgeType = TechBadgeType.UNITY,
                    isPrimary = true,
                    evidence = unityFiles.take(4).map { it.path }
                )
            )
        }

        if (hasCompose) {
            detections.add(
                TechDetection(
                    name = "Jetpack Compose (Kotlin)",
                    subtitleKu = "دیزاینێ مۆدێرن یێ Android ب Kotlin + Jetpack Compose",
                    subtitleEn = "Modern declarative Android UI written in Kotlin",
                    confidencePercent = 97,
                    badgeType = TechBadgeType.COMPOSE,
                    isPrimary = true,
                    evidence = listOf("androidx.compose.* classes in DEX") + kotlinModules.take(2).map { it.path }
                )
            )
        }

        if (hasKotlin) {
            detections.add(
                TechDetection(
                    name = "Kotlin",
                    subtitleKu = "زمانێ سەرەکی یێ فەرمی یێ ئەندرۆید (Kotlin)",
                    subtitleEn = "Compiled to Dalvik/ART DEX bytecode from Kotlin source",
                    confidencePercent = 95,
                    badgeType = TechBadgeType.KOTLIN,
                    isPrimary = !hasCompose && flutterFiles.isEmpty(),
                    evidence = (kotlinModules.take(3).map { it.path } + listOf("kotlin.Metadata annotations in DEX")).distinct()
                )
            )
        }

        if (dexFiles.isNotEmpty()) {
            detections.add(
                TechDetection(
                    name = "Java / Dalvik Bytecode",
                    subtitleKu = if (hasKotlin) "کۆدێ Java و کتابخانێن ئەندرۆید د ناڤ DEX دا" else "ئەپ ب زمانێ Java یێ ئەندرۆید هاتیە دروستکرن",
                    subtitleEn = "JVM/Dalvik compiled .dex bytecode (${dexFiles.size} DEX file(s))",
                    confidencePercent = if (hasKotlin) 88 else 96,
                    badgeType = TechBadgeType.JAVA,
                    isPrimary = !hasKotlin && flutterFiles.isEmpty() && rnFiles.isEmpty() && unityFiles.isEmpty(),
                    evidence = dexFiles.map { "${it.path} (${HtmlReportGenerator.formatBytes(it.sizeBytes)})" }
                )
            )
        }

        if (webFiles.isNotEmpty() || "Cordova / Capacitor" in dexFrameworks || "Android WebView" in dexFrameworks) {
            val isHybrid = webFiles.any { it.path.startsWith("assets/www/") } || "Cordova / Capacitor" in dexFrameworks
            detections.add(
                TechDetection(
                    name = if (isHybrid) "HTML5 / CSS / JS (Hybrid)" else "HTML / WebView Assets",
                    subtitleKu = if (isHybrid) "بەرنامێ Web ب HTML5 و CSS و JavaScript" else "فایلێن HTML یان WebView د ناڤ بەرنامەی دا هەنە",
                    subtitleEn = if (isHybrid) "Hybrid web application packaged in assets" else "Embedded HTML/JS/CSS assets or WebView components",
                    confidencePercent = if (isHybrid) 95 else 78,
                    badgeType = TechBadgeType.HTML_WEB,
                    isPrimary = isHybrid,
                    evidence = webFiles.take(4).map { it.path }.ifEmpty { listOf("android.webkit.WebView in DEX") }
                )
            )
        }

        if (soFiles.isNotEmpty()) {
            detections.add(
                TechDetection(
                    name = "Native C / C++ (NDK)",
                    subtitleKu = "کۆدێ خێرا یێ C/C++ (${abis.joinToString(", ")})",
                    subtitleEn = "Compiled ELF native shared libraries (${soFiles.size} .so files)",
                    confidencePercent = 92,
                    badgeType = TechBadgeType.NATIVE_CPP,
                    isPrimary = false,
                    evidence = soFiles.take(4).map { it.path }
                )
            )
        }

        if (layoutXmls.isNotEmpty() || "Android XML Views / Material" in dexFrameworks) {
            detections.add(
                TechDetection(
                    name = "Android XML Layouts",
                    subtitleKu = "دیزاینێ شاشان ب XML Layouts (${layoutXmls.size} فایل)",
                    subtitleEn = "Traditional Android XML View hierarchy & Material components",
                    confidencePercent = 90,
                    badgeType = TechBadgeType.XML_VIEWS,
                    isPrimary = false,
                    evidence = layoutXmls.take(3).map { it.path }.ifEmpty { listOf("androidx.appcompat / Material in DEX") }
                )
            )
        }

        return detections.sortedByDescending { (if (it.isPrimary) 100 else 0) + it.confidencePercent }
    }

    private fun classifyEntry(path: String, ext: String): FileCategory {
        return when {
            ext in setOf("html", "htm", "xhtml", "css", "js", "mjs", "json", "svg", "txt", "md", "properties", "pro") ||
                path.startsWith("assets/www/") -> FileCategory.WEB_HTML
            ext == "xml" -> FileCategory.XML_MANIFEST
            ext == "dex" || path.endsWith(".kotlin_module") || path.startsWith("kotlin/") -> FileCategory.DEX_CODE
            ext == "so" -> FileCategory.NATIVE_LIB
            else -> FileCategory.ASSETS_MEDIA
        }
    }

    private fun inspectElfHeader(bytes: ByteArray, length: Int, totalSize: Long): String {
        if (length < 20 || bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return "Native library (${HtmlReportGenerator.formatBytes(totalSize)})"
        }
        val elfClass = if (bytes[4].toInt() == 2) "ELF64 (64-bit)" else "ELF32 (32-bit)"
        val endian = if (bytes[5].toInt() == 1) "Little Endian" else "Big Endian"
        val machine = ByteBuffer.wrap(bytes, 18, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val arch = when (machine) {
            0xB7 -> "ARM64 (AArch64)"
            0x28 -> "ARM32 (armeabi-v7a)"
            0x3E -> "x86_64"
            0x03 -> "x86"
            0xF3 -> "RISC-V"
            else -> "Machine 0x${Integer.toHexString(machine)}"
        }
        return buildString {
            appendLine("// Native Shared Library (.so)")
            appendLine("Format:       $elfClass")
            appendLine("Architecture: $arch")
            appendLine("Byte Order:   $endian")
            appendLine("File Size:    ${HtmlReportGenerator.formatBytes(totalSize)}")
        }
    }

    private fun isProbablyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        var controlCount = 0
        val sampleSize = minOf(bytes.size, 2048)
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F && b != 0x1B)) controlCount++
        }
        return controlCount * 20 < sampleSize
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
    }

    private fun getPackageArchiveInfoCompat(pm: PackageManager, apkPath: String): PackageInfo? {
        val flags = (PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_PERMISSIONS).toLong()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, flags.toInt())
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        private val TEXT_EXTENSIONS = setOf(
            "html", "htm", "xhtml", "css", "js", "mjs", "ts", "json",
            "txt", "md", "properties", "pro", "cfg", "ini", "yaml", "yml",
            "svg", "glsl", "frag", "vert", "csv", "sql", "gradle", "kt", "java"
        )
    }
}
