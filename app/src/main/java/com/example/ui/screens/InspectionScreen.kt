package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.HtmlReportGenerator
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.model.ApkAnalysisResult
import com.example.model.ApkFileEntry
import com.example.model.FileCategory
import com.example.model.TechDetection
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.DrawableAppIcon
import com.example.ui.components.HtmlWebViewPreview
import com.example.ui.components.techBadgeVisuals
import com.example.viewmodel.InspectionTab

@Composable
fun InspectionScreen(
    analysis: ApkAnalysisResult,
    language: AppLanguage,
    strings: AppStrings,
    activeTab: InspectionTab,
    selectedCategory: FileCategory,
    fileSearchQuery: String,
    htmlRawMode: Boolean,
    isExporting: Boolean,
    onSelectTab: (InspectionTab) -> Unit,
    onSelectCategory: (FileCategory) -> Unit,
    onFileSearchChange: (String) -> Unit,
    onOpenFile: (ApkFileEntry) -> Unit,
    onSetHtmlRawMode: (Boolean) -> Unit,
    onCopyText: (String) -> Unit,
    onDownloadHtml: () -> Unit,
    onDownloadZip: () -> Unit,
    onSaveHtmlAs: () -> Unit,
    onShareHtml: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }

                DrawableAppIcon(
                    drawable = null,
                    bitmap = analysis.appIconBitmap,
                    contentDescription = analysis.appName,
                    modifier = Modifier.size(44.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = analysis.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${analysis.packageName} • v${analysis.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Tabs
            val tabs = listOf(
                InspectionTab.TECH_STACK to strings.tabTechStack,
                InspectionTab.FILES_CODE to "${strings.tabFilesCode} (${analysis.entries.size})",
                InspectionTab.HTML_REPORT to strings.tabHtmlOutput,
                InspectionTab.MANIFEST_DEX to strings.tabManifestDex
            )

            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == activeTab }.coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabs.forEach { (tab, title) ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    InspectionTab.TECH_STACK -> TechStackTabContent(
                        analysis = analysis,
                        language = language,
                        strings = strings
                    )

                    InspectionTab.FILES_CODE -> FilesExplorerTabContent(
                        entries = analysis.entries,
                        selectedCategory = selectedCategory,
                        searchQuery = fileSearchQuery,
                        strings = strings,
                        onSelectCategory = onSelectCategory,
                        onSearchChange = onFileSearchChange,
                        onOpenFile = onOpenFile
                    )

                    InspectionTab.HTML_REPORT -> HtmlReportTabContent(
                        htmlReport = analysis.generatedHtmlReport,
                        htmlRawMode = htmlRawMode,
                        strings = strings,
                        onSetRawMode = onSetHtmlRawMode,
                        onCopy = { onCopyText(analysis.generatedHtmlReport) }
                    )

                    InspectionTab.MANIFEST_DEX -> ManifestAndDexTabContent(
                        analysis = analysis,
                        strings = strings,
                        onCopy = onCopyText
                    )
                }
            }

            // Persistent Download & Share Bottom Bar
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDownloadHtml,
                        enabled = !isExporting,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("download_html_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.cd_download_html),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.downloadHtml,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = onDownloadZip,
                        enabled = !isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_zip_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderZip,
                            contentDescription = stringResource(R.string.cd_download_zip),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.downloadZip,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onSaveHtmlAs,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("save_as_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SaveAs,
                            contentDescription = strings.saveAs
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onShareHtml,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.cd_share_report)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechStackTabContent(
    analysis: ApkAnalysisResult,
    language: AppLanguage,
    strings: AppStrings
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "built_with_header") {
            Text(
                text = strings.builtWithTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(
            items = analysis.techDetections,
            key = { it.name }
        ) { tech ->
            TechDetectionCard(
                tech = tech,
                language = language,
                evidenceLabel = strings.evidenceLabel
            )
        }

        item(key = "metadata_card") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.appInfoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    MetaRow(strings.packageLabel, analysis.packageName)
                    MetaRow(strings.versionLabel, "${analysis.versionName} (${analysis.versionCode})")
                    MetaRow(strings.sdkLabel, "Min API ${analysis.minSdk} / Target API ${analysis.targetSdk}")
                    MetaRow(strings.sizeLabel, HtmlReportGenerator.formatBytes(analysis.apkSizeBytes))
                    MetaRow(strings.filesCountLabel, "${analysis.entries.size}")
                    if (analysis.nativeAbis.isNotEmpty()) {
                        MetaRow("Native ABIs", analysis.nativeAbis.joinToString(", "))
                    }
                }
            }
        }

        if (analysis.permissions.isNotEmpty()) {
            item(key = "permissions_card") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${strings.permissionsTitle} (${analysis.permissions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            analysis.permissions.take(25).forEach { perm ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = perm.substringAfterLast('.'),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (analysis.activities.isNotEmpty()) {
            item(key = "activities_card") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${strings.activitiesTitle} (${analysis.activities.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        analysis.activities.take(15).forEach { act ->
                            Text(
                                text = "• $act",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechDetectionCard(
    tech: TechDetection,
    language: AppLanguage,
    evidenceLabel: String
) {
    val (accentColor, icon) = techBadgeVisuals(tech.badgeType)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (tech.isPrimary) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (tech.isPrimary) 1.5.dp else 1.dp,
                color = if (tech.isPrimary) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tech.name,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tech.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == AppLanguage.KURDISH) tech.subtitleKu else tech.subtitleEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${tech.confidencePercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { tech.confidencePercent / 100f },
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            if (tech.evidence.isNotEmpty()) {
                Text(
                    text = evidenceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tech.evidence.forEach { ev ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = ev,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilesExplorerTabContent(
    entries: List<ApkFileEntry>,
    selectedCategory: FileCategory,
    searchQuery: String,
    strings: AppStrings,
    onSelectCategory: (FileCategory) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenFile: (ApkFileEntry) -> Unit
) {
    val filteredEntries = remember(entries, selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        entries.filter { entry ->
            val catOk = selectedCategory == FileCategory.ALL || entry.category == selectedCategory
            val qOk = q.isEmpty() || entry.path.lowercase().contains(q)
            catOk && qOk
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchFilesHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("file_search_input")
            )

            val categories = listOf(
                FileCategory.ALL to strings.filterAll,
                FileCategory.WEB_HTML to strings.filterWebHtml,
                FileCategory.XML_MANIFEST to strings.filterXmlManifest,
                FileCategory.DEX_CODE to strings.filterDexCode,
                FileCategory.NATIVE_LIB to strings.filterNative,
                FileCategory.ASSETS_MEDIA to strings.filterAssetsMedia
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (cat, label) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.noFilesMatch,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredEntries,
                    key = { it.path }
                ) { entry ->
                    ApkFileRow(entry = entry, onClick = { onOpenFile(entry) })
                }
            }
        }
    }
}

@Composable
private fun ApkFileRow(
    entry: ApkFileEntry,
    onClick: () -> Unit
) {
    val icon = when (entry.category) {
        FileCategory.WEB_HTML -> Icons.Rounded.Html
        FileCategory.XML_MANIFEST -> Icons.Rounded.Code
        FileCategory.DEX_CODE -> Icons.Rounded.Description
        FileCategory.NATIVE_LIB -> Icons.Rounded.Memory
        FileCategory.ASSETS_MEDIA -> if (entry.extension in setOf("png", "jpg", "webp")) {
            Icons.Rounded.Image
        } else {
            Icons.Rounded.Archive
        }
        FileCategory.ALL -> Icons.Rounded.Description
    }

    val tint = when (entry.category) {
        FileCategory.WEB_HTML -> Color(0xFFEC4899)
        FileCategory.XML_MANIFEST -> Color(0xFFF59E0B)
        FileCategory.DEX_CODE -> Color(0xFFA855F7)
        FileCategory.NATIVE_LIB -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = entry.name,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = HtmlReportGenerator.formatBytes(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HtmlReportTabContent(
    htmlReport: String,
    htmlRawMode: Boolean,
    strings: AppStrings,
    onSetRawMode: (Boolean) -> Unit,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !htmlRawMode,
                onClick = { onSetRawMode(false) },
                label = { Text(strings.htmlPreviewMode) },
                leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = htmlRawMode,
                onClick = { onSetRawMode(true) },
                label = { Text(strings.htmlRawMode) },
                leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (htmlRawMode) {
                CodeBlockCard(
                    code = htmlReport,
                    title = "index_code_report.html",
                    copyLabel = strings.copyCode,
                    onCopy = onCopy,
                    modifier = Modifier.fillMaxSize(),
                    maxHeightDp = 700
                )
            } else {
                HtmlWebViewPreview(
                    htmlContent = htmlReport,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ManifestAndDexTabContent(
    analysis: ApkAnalysisResult,
    strings: AppStrings,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "dex_stats") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = strings.dexSummaryTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = strings.totalClassesLabel,
                            value = "${analysis.dexAnalysis.totalClasses}",
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = strings.totalMethodsLabel,
                            value = "${analysis.dexAnalysis.totalMethods}",
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = strings.totalStringsLabel,
                            value = "${analysis.dexAnalysis.totalStrings}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "manifest_code") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.manifestTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CodeBlockCard(
                    code = analysis.decodedManifestXml,
                    title = "AndroidManifest.xml (Decoded)",
                    copyLabel = strings.copyCode,
                    onCopy = { onCopy(analysis.decodedManifestXml) },
                    maxHeightDp = 360
                )
            }
        }

        item(key = "dex_outline_code") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.packagesTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CodeBlockCard(
                    code = analysis.dexAnalysis.sampleClassOutline,
                    title = "classes.dex — Package & Class Outline",
                    copyLabel = strings.copyCode,
                    onCopy = { onCopy(analysis.dexAnalysis.sampleClassOutline) },
                    maxHeightDp = 360
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
