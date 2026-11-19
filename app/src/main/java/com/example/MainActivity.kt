package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.ui.components.CodeViewerDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InspectionScreen
import com.example.ui.theme.CodeLensTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.ScreenState

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val language by viewModel.language.collectAsStateWithLifecycle()
            val strings = remember(language) { stringsFor(language) }
            val layoutDirection = if (language == AppLanguage.KURDISH) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CodeLensTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    CodeLensAppRoot(
                        viewModel = viewModel,
                        language = language,
                        isDarkTheme = isDarkTheme,
                        strings = strings
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            viewModel.inspectFromUri(uri)
        }
    }
}

@Composable
private fun CodeLensAppRoot(
    viewModel: MainViewModel,
    language: AppLanguage,
    isDarkTheme: Boolean,
    strings: com.example.i18n.AppStrings
) {
    val context = LocalContext.current
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val apps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val appCounts by viewModel.appCounts.collectAsStateWithLifecycle()
    val selectedAppFilter by viewModel.appFilterType.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val appSearchQuery by viewModel.appSearchQuery.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val fileSearchQuery by viewModel.fileSearchQuery.collectAsStateWithLifecycle()
    val selectedFileEntry by viewModel.selectedFileEntry.collectAsStateWithLifecycle()
    val filePreview by viewModel.filePreview.collectAsStateWithLifecycle()
    val htmlRawMode by viewModel.htmlRawMode.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val openApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectFromUri(uri)
        }
    }

    val saveHtmlAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            viewModel.saveHtmlToUri(uri)
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CodeLens", text.take(400_000)))
        viewModel.showMessage(strings.copiedToast)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Crossfade(
            targetState = screenState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "screen_crossfade"
        ) { state ->
            when (state) {
                ScreenState.Home -> {
                    HomeScreen(
                        strings = strings,
                        langToggleLabel = strings.langToggleLabel,
                        isDarkTheme = isDarkTheme,
                        apps = apps,
                        appCounts = appCounts,
                        selectedAppFilter = selectedAppFilter,
                        isLoadingApps = isLoadingApps,
                        searchQuery = appSearchQuery,
                        loadAppIcon = viewModel::loadAppIcon,
                        onSearchChange = viewModel::updateAppSearch,
                        onSelectAppFilter = viewModel::selectAppFilter,
                        onRefreshApps = viewModel::refreshInstalledApps,
                        onPickApkClick = {
                            openApkLauncher.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/zip",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        onInspectSelfClick = viewModel::inspectSelfApp,
                        onInspectAppClick = viewModel::inspectInstalledApp,
                        onToggleLanguage = viewModel::toggleLanguage,
                        onToggleTheme = viewModel::toggleTheme
                    )
                }

                is ScreenState.Analyzing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(56.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = state.targetName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.analyzingTitle,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = strings.analyzingSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is ScreenState.Result -> {
                    InspectionScreen(
                        analysis = state.analysis,
                        language = language,
                        strings = strings,
                        activeTab = activeTab,
                        selectedCategory = selectedCategory,
                        fileSearchQuery = fileSearchQuery,
                        htmlRawMode = htmlRawMode,
                        isExporting = isExporting,
                        onSelectTab = viewModel::selectTab,
                        onSelectCategory = viewModel::selectFileCategory,
                        onFileSearchChange = viewModel::updateFileSearch,
                        onOpenFile = viewModel::openFileEntry,
                        onSetHtmlRawMode = viewModel::setHtmlRawMode,
                        onCopyText = ::copyToClipboard,
                        onDownloadHtml = viewModel::downloadHtmlReport,
                        onDownloadZip = viewModel::downloadZipBundle,
                        onSaveHtmlAs = {
                            saveHtmlAsLauncher.launch("index.html")
                        },
                        onShareHtml = { viewModel.shareHtml(context) },
                        onBack = viewModel::navigateBackHome
                    )
                }

                is ScreenState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = strings.errorAnalyzeTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = viewModel::navigateBackHome,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(strings.retryOrBack)
                            }
                        }
                    }
                }
            }
        }
    }

    val openEntry = selectedFileEntry
    if (openEntry != null) {
        CodeViewerDialog(
            entry = openEntry,
            preview = filePreview,
            strings = strings,
            onCopy = ::copyToClipboard,
            onDownloadFile = viewModel::downloadSelectedFile,
            onDismiss = viewModel::closeFilePreview
        )
    }
}
