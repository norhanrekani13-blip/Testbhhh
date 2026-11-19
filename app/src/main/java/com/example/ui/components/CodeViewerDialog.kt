package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.HtmlReportGenerator
import com.example.i18n.AppStrings
import com.example.model.ApkFileEntry
import com.example.model.FilePreviewContent

@Composable
fun CodeViewerDialog(
    entry: ApkFileEntry,
    preview: FilePreviewContent?,
    strings: AppStrings,
    onCopy: (String) -> Unit,
    onDownloadFile: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var showHtmlPreview by remember(entry.path) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.path} • ${HtmlReportGenerator.formatBytes(entry.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_file_viewer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (preview is FilePreviewContent.TextCode && preview.isHtml) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        FilterChip(
                            selected = !showHtmlPreview,
                            onClick = { showHtmlPreview = false },
                            label = { Text(strings.htmlRawMode) },
                            leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = showHtmlPreview,
                            onClick = { showHtmlPreview = true },
                            label = { Text(strings.htmlPreviewMode) },
                            leadingIcon = { Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (preview) {
                        null, FilePreviewContent.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(strings.loadingFile, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        is FilePreviewContent.TextCode -> {
                            if (preview.isHtml && showHtmlPreview) {
                                HtmlWebViewPreview(
                                    htmlContent = preview.text,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CodeBlockCard(
                                    code = preview.text,
                                    title = entry.path,
                                    copyLabel = strings.copyCode,
                                    onCopy = { onCopy(preview.text) },
                                    modifier = Modifier.fillMaxSize(),
                                    maxHeightDp = 700
                                )
                            }
                        }

                        is FilePreviewContent.ImagePreview -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    bitmap = preview.bitmap.asImageBitmap(),
                                    contentDescription = entry.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "${preview.width} × ${preview.height} px",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        is FilePreviewContent.BinarySummary -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = strings.binaryOrLargeNotice,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                CodeBlockCard(
                                    code = preview.headerSummary,
                                    title = entry.path,
                                    copyLabel = strings.copyCode,
                                    onCopy = { onCopy(preview.headerSummary) },
                                    maxHeightDp = 420
                                )
                            }
                        }

                        is FilePreviewContent.Error -> {
                            Text(
                                text = preview.message,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDownloadFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("download_single_file_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.downloadThisFile)
                }
            }
        }
    }
}
