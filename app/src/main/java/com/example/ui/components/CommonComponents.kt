package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.TechBadgeType
import com.example.ui.theme.BadgeFlutter
import com.example.ui.theme.BadgeHtml
import com.example.ui.theme.BadgeJava
import com.example.ui.theme.BadgeKotlin
import com.example.ui.theme.BadgeNative
import com.example.ui.theme.BadgeReact
import com.example.ui.theme.BadgeUnity
import com.example.ui.theme.BadgeXml
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun StudioTopBar(
    appName: String,
    langToggleLabel: String,
    isDarkTheme: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = appName,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "APK & HTML Code Inspector",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onToggleLanguage,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("toggle_language_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = stringResource(R.string.cd_toggle_language),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = langToggleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onToggleTheme,
                modifier = Modifier.testTag("toggle_theme_button")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(R.string.cd_toggle_theme)
                )
            }
        }
    }
}

@Composable
fun DrawableAppIcon(
    drawable: Drawable?,
    bitmap: Bitmap? = null,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val resolvedBitmap = remember(drawable, bitmap) {
        bitmap ?: drawable?.let { d ->
            if (d is BitmapDrawable && d.bitmap != null) {
                d.bitmap
            } else {
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 96
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 96
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    d.setBounds(0, 0, canvas.width, canvas.height)
                    d.draw(canvas)
                }
            }
        }
    }

    if (resolvedBitmap != null) {
        Image(
            bitmap = resolvedBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DeveloperMode,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun CodeBlockCard(
    code: String,
    title: String,
    copyLabel: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightDp: Int = 460
) {
    val lines = remember(code) {
        code.lines().take(1200)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF070B13)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101827))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF38BDF8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.testTag("copy_code_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = copyLabel,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = copyLabel,
                        color = Color(0xFF34D399),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Force LTR for source code regardless of Kurdish RTL UI
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val vScroll = rememberScrollState()
                val hScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeightDp.dp)
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Column {
                            lines.forEachIndexed { index, line ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = "${index + 1}".padStart(4, ' '),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = line,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = syntaxLineColor(line)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun syntaxLineColor(line: String): Color {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("//") || trimmed.startsWith("<!--") || trimmed.startsWith("#") -> Color(0xFF64748B)
        trimmed.startsWith("<") -> Color(0xFF38BDF8)
        trimmed.startsWith("package ") || trimmed.startsWith("import ") -> Color(0xFFA855F7)
        trimmed.contains("class ") || trimmed.contains("function ") -> Color(0xFF34D399)
        else -> Color(0xFFE2E8F0)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebViewPreview(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://codelens.local/",
                htmlContent,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    )
}

fun techBadgeVisuals(type: TechBadgeType): Pair<Color, ImageVector> = when (type) {
    TechBadgeType.KOTLIN -> BadgeKotlin to Icons.Rounded.Code
    TechBadgeType.JAVA -> BadgeJava to Icons.Rounded.Terminal
    TechBadgeType.COMPOSE -> BadgeKotlin to Icons.Rounded.Widgets
    TechBadgeType.HTML_WEB -> BadgeHtml to Icons.Rounded.Html
    TechBadgeType.FLUTTER -> BadgeFlutter to Icons.Rounded.Layers
    TechBadgeType.REACT_NATIVE -> BadgeReact to Icons.Rounded.DeveloperMode
    TechBadgeType.NATIVE_CPP -> BadgeNative to Icons.Rounded.Memory
    TechBadgeType.UNITY -> BadgeUnity to Icons.Rounded.SportsEsports
    TechBadgeType.XML_VIEWS -> BadgeXml to Icons.Rounded.Code
}
