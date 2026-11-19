package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.ApkAnalysisResult
import com.example.model.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Handles downloading HTML reports, full extracted ZIP source bundles, and individual files
 * to public Downloads/CodeLens (zero permissions needed on API 29+) or sharing via FileProvider.
 */
class ExportManager(private val context: Context) {

    suspend fun saveHtmlToDownloads(result: ApkAnalysisResult): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitizeFilename(result.appName.ifBlank { result.packageName })
            val bytes = result.generatedHtmlReport.toByteArray(Charsets.UTF_8)
            saveBytesToDownloads(
                fileName = "index.html",
                mimeType = "text/html",
                bytes = bytes,
                subFolder = safeName
            )
        }
    }

    suspend fun saveSingleFileToDownloads(fileName: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = sanitizeFilename(fileName.substringAfterLast('/'))
                saveBytesToDownloads(safeName, "application/octet-stream", bytes)
            }
        }

    suspend fun saveZipBundleToDownloads(result: ApkAnalysisResult): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitizeFilename(result.appName.ifBlank { result.packageName })
            val fileName = "${safeName}_source_bundle.zip"
            val tempZip = File(getExportCacheDir(), fileName)
            writeZipBundleToFile(result, tempZip)
            val bytes = tempZip.readBytes()
            saveBytesToDownloads(fileName, "application/zip", bytes)
        }
    }

    suspend fun writeHtmlToUri(uri: Uri, html: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(html.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot open output stream")
        }
    }

    suspend fun writeZipToUri(uri: Uri, result: ApkAnalysisResult): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tempZip = File(getExportCacheDir(), "saf_bundle_${System.currentTimeMillis()}.zip")
            writeZipBundleToFile(result, tempZip)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempZip.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open output stream")
            tempZip.delete()
            Unit
        }
    }

    suspend fun createShareHtmlIntent(result: ApkAnalysisResult): Intent = withContext(Dispatchers.IO) {
        val safeName = sanitizeFilename(result.appName.ifBlank { result.packageName })
        val dir = File(getExportCacheDir(), safeName).apply { mkdirs() }
        val outFile = File(dir, "index.html")
        outFile.writeText(result.generatedHtmlReport, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${result.appName} — index.html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun createShareZipIntent(result: ApkAnalysisResult): Intent = withContext(Dispatchers.IO) {
        val safeName = sanitizeFilename(result.appName.ifBlank { result.packageName })
        val outFile = File(getExportCacheDir(), "${safeName}_source_bundle.zip")
        writeZipBundleToFile(result, outFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${result.appName} — Extracted Source Bundle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeZipBundleToFile(result: ApkAnalysisResult, outFile: File) {
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            // 1. Interactive HTML report as index.html
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write(result.generatedHtmlReport.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Decoded AndroidManifest.xml
            zos.putNextEntry(ZipEntry("AndroidManifest_decoded.xml"))
            zos.write(result.decodedManifestXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. DEX Class & Package outline
            zos.putNextEntry(ZipEntry("dex_classes_outline.txt"))
            zos.write(result.dexAnalysis.sampleClassOutline.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Tech stack summary JSON
            val json = buildTechJson(result)
            zos.putNextEntry(ZipEntry("tech_stack_summary.json"))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. Extracted readable & web/XML/asset files from APK
            val apkFile = File(result.apkFilePath)
            if (apkFile.exists()) {
                ZipFile(apkFile).use { apkZip ->
                    val exportable = result.entries.filter { entry ->
                        (entry.category == FileCategory.WEB_HTML ||
                            entry.category == FileCategory.XML_MANIFEST ||
                            entry.extension in setOf("png", "jpg", "webp", "svg", "json", "txt", "pro")) &&
                            entry.sizeBytes in 1..2_000_000
                    }.take(300)

                    for (entry in exportable) {
                        val ze = apkZip.getEntry(entry.path) ?: continue
                        val raw = apkZip.getInputStream(ze).use { it.readBytes() }
                        val data = if (entry.extension == "xml" && BinaryXmlDecoder.isBinaryXml(raw)) {
                            BinaryXmlDecoder.decode(raw).toByteArray(Charsets.UTF_8)
                        } else {
                            raw
                        }
                        zos.putNextEntry(ZipEntry("extracted_files/${entry.path}"))
                        zos.write(data)
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    private fun buildTechJson(result: ApkAnalysisResult): String {
        val techArr = result.techDetections.joinToString(",\n    ") { t ->
            """{"name": "${escJson(t.name)}", "confidence": ${t.confidencePercent}, "primary": ${t.isPrimary}}"""
        }
        return """
{
  "appName": "${escJson(result.appName)}",
  "packageName": "${escJson(result.packageName)}",
  "versionName": "${escJson(result.versionName)}",
  "versionCode": ${result.versionCode},
  "minSdk": ${result.minSdk},
  "targetSdk": ${result.targetSdk},
  "totalClasses": ${result.dexAnalysis.totalClasses},
  "totalMethods": ${result.dexAnalysis.totalMethods},
  "detectedStack": [
    $techArr
  ]
}
        """.trimIndent()
    }

    private fun saveBytesToDownloads(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        subFolder: String? = null
    ): String {
        val folderSuffix = if (!subFolder.isNullOrBlank()) "/$subFolder" else ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CodeLens$folderSuffix")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create download entry")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Failed to write download file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/CodeLens$folderSuffix/$fileName"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "CodeLens$folderSuffix").apply {
                mkdirs()
            }
            val outFile = File(dir, fileName)
            outFile.writeBytes(bytes)
            return outFile.absolutePath
        }
    }

    private fun getExportCacheDir(): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "app" }
    }

    private fun escJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
