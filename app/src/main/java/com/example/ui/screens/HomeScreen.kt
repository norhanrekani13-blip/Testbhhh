package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.HtmlReportGenerator
import com.example.i18n.AppStrings
import com.example.model.InstalledAppItem
import com.example.ui.components.DrawableAppIcon
import com.example.ui.components.StudioTopBar
import com.example.viewmodel.AppFilterType

@Composable
fun HomeScreen(
    strings: AppStrings,
    langToggleLabel: String,
    isDarkTheme: Boolean,
    apps: List<InstalledAppItem>,
    appCounts: Triple<Int, Int, Int>,
    selectedAppFilter: AppFilterType,
    isLoadingApps: Boolean,
    searchQuery: String,
    loadAppIcon: suspend (String) -> Bitmap?,
    onSearchChange: (String) -> Unit,
    onSelectAppFilter: (AppFilterType) -> Unit,
    onRefreshApps: () -> Unit,
    onPickApkClick: () -> Unit,
    onInspectSelfClick: () -> Unit,
    onInspectAppClick: (InstalledAppItem) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            StudioTopBar(
                appName = strings.appName,
                langToggleLabel = langToggleLabel,
                isDarkTheme = isDarkTheme,
                onToggleLanguage = onToggleLanguage,
                onToggleTheme = onToggleTheme
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "hero_card") {
                    HeroInspectorCard(
                        strings = strings,
                        onPickApkClick = onPickApkClick,
                        onInspectSelfClick = onInspectSelfClick
                    )
                }

                item(key = "apps_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.installedAppsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = onRefreshApps,
                                modifier = Modifier.testTag("refresh_apps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = strings.refreshApps
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = "${apps.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_search_input"),
                            placeholder = {
                                Text(
                                    text = strings.searchAppsHint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.cd_search)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChange("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.cd_clear_search)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedAppFilter == AppFilterType.ALL,
                                onClick = { onSelectAppFilter(AppFilterType.ALL) },
                                label = { Text("${strings.filterAllApps} (${appCounts.first})") },
                                modifier = Modifier.testTag("filter_apps_all")
                            )
                            FilterChip(
                                selected = selectedAppFilter == AppFilterType.USER,
                                onClick = { onSelectAppFilter(AppFilterType.USER) },
                                label = { Text("${strings.filterUserApps} (${appCounts.second})") },
                                modifier = Modifier.testTag("filter_apps_user")
                            )
                            FilterChip(
                                selected = selectedAppFilter == AppFilterType.SYSTEM,
                                onClick = { onSelectAppFilter(AppFilterType.SYSTEM) },
                                label = { Text("${strings.filterSystemApps} (${appCounts.third})") },
                                modifier = Modifier.testTag("filter_apps_system")
                            )
                        }
                    }
                }

                if (isLoadingApps) {
                    item(key = "loading_apps") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (apps.isEmpty()) {
                    item(key = "empty_apps") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = strings.noAppsFound,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = apps,
                        key = { it.packageName }
                    ) { app ->
                        InstalledAppRow(
                            app = app,
                            strings = strings,
                            loadAppIcon = loadAppIcon,
                            onClick = { onInspectAppClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroInspectorCard(
    strings: AppStrings,
    onPickApkClick: () -> Unit,
    onInspectSelfClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
        tonalElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_inspector_hero_1795098625501),
                contentDescription = stringResource(R.string.cd_hero_banner),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC090E1A),
                                Color(0xEC0B1120)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.heroTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = strings.heroSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickApkClick,
                        modifier = Modifier
                            .weight(1.35f)
                            .height(50.dp)
                            .testTag("pick_apk_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22D3EE),
                            contentColor = Color(0xFF00262E)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.pickApkButton,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = onInspectSelfClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("inspect_self_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF34D399)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = strings.inspectSelfButton,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledAppItem,
    strings: AppStrings,
    loadAppIcon: suspend (String) -> Bitmap?,
    onClick: () -> Unit
) {
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = app.packageName) {
        value = loadAppIcon(app.packageName)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .testTag("app_item_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DrawableAppIcon(
                drawable = app.icon,
                bitmap = iconBitmap,
                contentDescription = "${app.appName} ${stringResource(R.string.cd_app_icon)}",
                modifier = Modifier.size(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (app.isSystemApp) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = if (app.isSystemApp) strings.systemBadge else strings.userBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (app.isSystemApp) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${app.versionName} • ${HtmlReportGenerator.formatBytes(app.apkSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = strings.inspectAction,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
