package com.example.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val apkPath: String,
    val apkSizeBytes: Long,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)

enum class FileCategory {
    ALL,
    WEB_HTML,
    XML_MANIFEST,
    DEX_CODE,
    NATIVE_LIB,
    ASSETS_MEDIA
}

data class ApkFileEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val category: FileCategory,
    val extension: String,
    val isBinaryXml: Boolean = false
)

enum class TechBadgeType {
    KOTLIN,
    JAVA,
    COMPOSE,
    HTML_WEB,
    FLUTTER,
    REACT_NATIVE,
    NATIVE_CPP,
    UNITY,
    XML_VIEWS
}

data class TechDetection(
    val name: String,
    val subtitleKu: String,
    val subtitleEn: String,
    val confidencePercent: Int,
    val badgeType: TechBadgeType,
    val isPrimary: Boolean,
    val evidence: List<String>
)

data class DexPackageSummary(
    val packageName: String,
    val classCount: Int,
    val sampleClasses: List<String>
)

data class DexFileSummary(
    val fileName: String,
    val dexVersion: String,
    val fileSizeBytes: Int,
    val stringCount: Int,
    val typeCount: Int,
    val fieldCount: Int,
    val methodCount: Int,
    val classCount: Int
)

data class DexAnalysisResult(
    val dexFiles: List<DexFileSummary>,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalStrings: Int,
    val topPackages: List<DexPackageSummary>,
    val detectedFrameworks: Set<String>,
    val sampleClassOutline: String,
    val interestingUrls: List<String>
)

data class ApkAnalysisResult(
    val apkFilePath: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val apkSizeBytes: Long,
    val appIconBitmap: Bitmap?,
    val techDetections: List<TechDetection>,
    val decodedManifestXml: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val nativeAbis: List<String>,
    val entries: List<ApkFileEntry>,
    val dexAnalysis: DexAnalysisResult,
    val generatedHtmlReport: String
)

sealed interface FilePreviewContent {
    data object Loading : FilePreviewContent
    data class TextCode(
        val path: String,
        val text: String,
        val isHtml: Boolean = false,
        val isNoticeTruncated: Boolean = false
    ) : FilePreviewContent

    data class ImagePreview(
        val path: String,
        val bitmap: Bitmap,
        val width: Int,
        val height: Int
    ) : FilePreviewContent

    data class BinarySummary(
        val path: String,
        val headerSummary: String
    ) : FilePreviewContent

    data class Error(val message: String) : FilePreviewContent
}
