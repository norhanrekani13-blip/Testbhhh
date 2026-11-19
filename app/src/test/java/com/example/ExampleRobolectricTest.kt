package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ApkAnalyzerEngine
import com.example.model.FileCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches CodeLens`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CodeLens", appName)
    }

    @Test
    fun `analyze synthetic apk detects tech stack and generates html report`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempApk = File(context.cacheDir, "sample_test.apk")
        ZipOutputStream(FileOutputStream(tempApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("<manifest package=\"com.rhmsoft.edit\"><application/></manifest>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("assets/www/index.html"))
            zos.write("<html><body><h1>QuickEdit HTML</h1></body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/app.kotlin_module"))
            zos.write(byteArrayOf(0, 1, 2, 3))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libeditor.so"))
            zos.write(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1))
            zos.closeEntry()
        }

        val engine = ApkAnalyzerEngine(context)
        val result = engine.analyzeApk(tempApk.absolutePath, fallbackAppName = "QuickEdit Sample")

        assertTrue(result.techDetections.any { it.name.contains("Kotlin") })
        assertTrue(result.techDetections.any { it.name.contains("HTML") })
        assertTrue(result.techDetections.any { it.name.contains("Native") })
        assertTrue(result.entries.any { it.category == FileCategory.WEB_HTML && it.name == "index.html" })
        assertTrue(result.generatedHtmlReport.contains("<!DOCTYPE html>"))
        assertTrue(result.generatedHtmlReport.contains("QuickEdit HTML"))
        assertTrue(result.nativeAbis.contains("arm64-v8a"))

        tempApk.delete()
    }
}
