package com.example.engine

import com.example.model.DexAnalysisResult
import com.example.model.DexFileSummary
import com.example.model.DexPackageSummary
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Dalvik Executable (.dex) binary headers, string pools, type tables, and class definitions
 * to extract package namespaces, class outlines, framework signatures, and embedded URLs.
 */
object DexInspector {

    fun inspectMultipleDex(dexBytesList: List<Pair<String, ByteArray>>): DexAnalysisResult {
        val summaries = mutableListOf<DexFileSummary>()
        val packageToClasses = linkedMapOf<String, LinkedHashSet<String>>()
        val frameworks = linkedSetOf<String>()
        val urls = linkedSetOf<String>()
        var totalClasses = 0
        var totalMethods = 0
        var totalStrings = 0

        for ((fileName, bytes) in dexBytesList) {
            val parsed = inspectSingleDex(fileName, bytes)
            if (parsed != null) {
                summaries.add(parsed.summary)
                totalClasses += parsed.summary.classCount
                totalMethods += parsed.summary.methodCount
                totalStrings += parsed.summary.stringCount
                frameworks.addAll(parsed.frameworks)
                urls.addAll(parsed.urls)

                for (classDesc in parsed.classNames) {
                    val fqcn = descriptorToClassName(classDesc)
                    if (fqcn.isBlank()) continue
                    val pkg = fqcn.substringBeforeLast('.', "(default)")
                    val simple = fqcn.substringAfterLast('.')
                    val set = packageToClasses.getOrPut(pkg) { linkedSetOf() }
                    if (set.size < 40) set.add(simple)
                }
            }
        }

        val sortedPackages = packageToClasses.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, LinkedHashSet<String>>> {
                    !it.key.startsWith("androidx.") &&
                        !it.key.startsWith("android.") &&
                        !it.key.startsWith("kotlin") &&
                        !it.key.startsWith("kotlinx.") &&
                        !it.key.startsWith("com.google.")
                }.thenByDescending { it.value.size }
            )
            .take(45)
            .map { (pkg, classes) ->
                DexPackageSummary(
                    packageName = pkg,
                    classCount = classes.size,
                    sampleClasses = classes.toList().take(12)
                )
            }

        val outlineBuilder = StringBuilder()
        outlineBuilder.append("// Extracted DEX Class & Package Outline\n")
        outlineBuilder.append("// Total DEX files: ${summaries.size}, Classes: $totalClasses, Methods: $totalMethods\n\n")
        for (pkg in sortedPackages.take(25)) {
            outlineBuilder.append("package ${pkg.packageName};\n")
            for (cls in pkg.sampleClasses.take(8)) {
                outlineBuilder.append("    public class $cls { /* compiled Dalvik bytecode */ }\n")
            }
            outlineBuilder.append("\n")
        }

        return DexAnalysisResult(
            dexFiles = summaries,
            totalClasses = totalClasses,
            totalMethods = totalMethods,
            totalStrings = totalStrings,
            topPackages = sortedPackages,
            detectedFrameworks = frameworks,
            sampleClassOutline = outlineBuilder.toString(),
            interestingUrls = urls.take(25)
        )
    }

    fun formatDexFilePreview(fileName: String, bytes: ByteArray): String {
        val parsed = inspectSingleDex(fileName, bytes) ?: return "Invalid or unreadable DEX file: $fileName"
        val s = parsed.summary
        val sb = StringBuilder()
        sb.append("// ============================================\n")
        sb.append("// DEX File: ${s.fileName}\n")
        sb.append("// Format:   DEX v${s.dexVersion}\n")
        sb.append("// Size:     ${s.fileSizeBytes} bytes\n")
        sb.append("// Strings:  ${s.stringCount}\n")
        sb.append("// Types:    ${s.typeCount}\n")
        sb.append("// Fields:   ${s.fieldCount}\n")
        sb.append("// Methods:  ${s.methodCount}\n")
        sb.append("// Classes:  ${s.classCount}\n")
        sb.append("// ============================================\n\n")

        if (parsed.frameworks.isNotEmpty()) {
            sb.append("// Detected SDK / Framework Markers:\n")
            parsed.frameworks.forEach { sb.append("//   • $it\n") }
            sb.append("\n")
        }

        sb.append("// Class Definitions (showing up to 250):\n")
        parsed.classNames.take(250).forEach { desc ->
            val fqcn = descriptorToClassName(desc)
            if (fqcn.isNotEmpty()) {
                val pkg = fqcn.substringBeforeLast('.', "")
                val cls = fqcn.substringAfterLast('.')
                if (pkg.isNotEmpty()) {
                    sb.append("package $pkg; class $cls\n")
                } else {
                    sb.append("class $cls\n")
                }
            }
        }
        return sb.toString()
    }

    private data class SingleDexParsed(
        val summary: DexFileSummary,
        val classNames: List<String>,
        val frameworks: Set<String>,
        val urls: Set<String>
    )

    private fun inspectSingleDex(fileName: String, bytes: ByteArray): SingleDexParsed? {
        if (bytes.size < 112) return null
        // Check magic "dex\n"
        if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() ||
            bytes[2] != 'x'.code.toByte() || bytes[3] != '\n'.code.toByte()
        ) {
            return null
        }

        val version = String(bytes, 4, 3, Charsets.US_ASCII)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileSize = buf.getInt(32)
        val stringIdsSize = buf.getInt(56).coerceAtLeast(0)
        val stringIdsOff = buf.getInt(60).coerceAtLeast(0)
        val typeIdsSize = buf.getInt(64).coerceAtLeast(0)
        val typeIdsOff = buf.getInt(68).coerceAtLeast(0)
        val fieldIdsSize = buf.getInt(80).coerceAtLeast(0)
        val methodIdsSize = buf.getInt(88).coerceAtLeast(0)
        val classDefsSize = buf.getInt(96).coerceAtLeast(0)
        val classDefsOff = buf.getInt(100).coerceAtLeast(0)

        val frameworks = linkedSetOf<String>()
        val urls = linkedSetOf<String>()
        val classNames = ArrayList<String>(minOf(classDefsSize, 1500))

        // Helper to read a string at stringId index
        fun readStringAt(strIdx: Int): String? {
            if (strIdx < 0 || strIdx >= stringIdsSize) return null
            val offsetPos = stringIdsOff + strIdx * 4
            if (offsetPos + 4 > bytes.size) return null
            val dataOff = buf.getInt(offsetPos)
            if (dataOff < 0 || dataOff >= bytes.size) return null
            return decodeMutf8(bytes, dataOff)
        }

        // Read class_defs (32 bytes each; first int is class_idx into type_ids)
        val maxClassesToRead = minOf(classDefsSize, 2500)
        for (i in 0 until maxClassesToRead) {
            val classDefPos = classDefsOff + i * 32
            if (classDefPos + 4 > bytes.size) break
            val typeIdx = buf.getInt(classDefPos)
            if (typeIdx < 0 || typeIdx >= typeIdsSize) continue
            val typePos = typeIdsOff + typeIdx * 4
            if (typePos + 4 > bytes.size) continue
            val strIdx = buf.getInt(typePos)
            val descriptor = readStringAt(strIdx) ?: continue
            classNames.add(descriptor)
            checkFrameworkMarker(descriptor, frameworks)
        }

        // Sample strings for framework markers and URLs (cap at 18,000 for speed)
        val step = if (stringIdsSize > 18000) stringIdsSize / 18000 else 1
        var idx = 0
        while (idx < stringIdsSize) {
            val s = readStringAt(idx)
            if (s != null) {
                checkFrameworkMarker(s, frameworks)
                if ((s.startsWith("https://") || s.startsWith("http://")) && s.length in 10..140 && !s.contains("schemas.android.com")) {
                    if (urls.size < 30) urls.add(s)
                }
            }
            idx += step
        }

        return SingleDexParsed(
            summary = DexFileSummary(
                fileName = fileName,
                dexVersion = version,
                fileSizeBytes = fileSize,
                stringCount = stringIdsSize,
                typeCount = typeIdsSize,
                fieldCount = fieldIdsSize,
                methodCount = methodIdsSize,
                classCount = classDefsSize
            ),
            classNames = classNames,
            frameworks = frameworks,
            urls = urls
        )
    }

    private fun checkFrameworkMarker(str: String, frameworks: MutableSet<String>) {
        when {
            str.contains("Lkotlin/Metadata;") || str.startsWith("Lkotlin/") || str.startsWith("Lkotlinx/") ->
                frameworks.add("Kotlin")
            str.startsWith("Landroidx/compose/") ->
                frameworks.add("Jetpack Compose")
            str.startsWith("Lio/flutter/") ->
                frameworks.add("Flutter")
            str.startsWith("Lcom/facebook/react/") ->
                frameworks.add("React Native")
            str.startsWith("Lorg/apache/cordova/") || str.startsWith("Lcom/getcapacitor/") ->
                frameworks.add("Cordova / Capacitor")
            str.startsWith("Landroid/webkit/WebView") || str.contains("WebViewClient") ->
                frameworks.add("Android WebView")
            str.startsWith("Lcom/unity3d/") ->
                frameworks.add("Unity")
            str.startsWith("Landroidx/appcompat/") || str.startsWith("Lcom/google/android/material/") ->
                frameworks.add("Android XML Views / Material")
        }
    }

    private fun decodeMutf8(bytes: ByteArray, offset: Int): String {
        var pos = offset
        // Skip ULEB128 char count
        while (pos < bytes.size) {
            val b = bytes[pos].toInt() and 0xFF
            pos++
            if ((b and 0x80) == 0) break
        }
        val start = pos
        val maxEnd = (start + 512).coerceAtMost(bytes.size)
        while (pos < maxEnd && bytes[pos].toInt() != 0) {
            pos++
        }
        return String(bytes, start, (pos - start).coerceAtLeast(0), Charsets.UTF_8)
    }

    private fun descriptorToClassName(descriptor: String): String {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) return ""
        return descriptor.substring(1, descriptor.length - 1).replace('/', '.')
    }
}
