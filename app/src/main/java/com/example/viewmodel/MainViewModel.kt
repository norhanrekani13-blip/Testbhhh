package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.ApkAnalyzerEngine
import com.example.engine.ExportManager
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import com.example.model.ApkAnalysisResult
import com.example.model.ApkFileEntry
import com.example.model.FileCategory
import com.example.model.FilePreviewContent
import com.example.model.InstalledAppItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilterType {
    ALL,
    USER,
    SYSTEM
}

enum class InspectionTab {
    TECH_STACK,
    FILES_CODE,
    HTML_REPORT,
    MANIFEST_DEX
}

sealed interface ScreenState {
    data object Home : ScreenState
    data class Analyzing(val targetName: String) : ScreenState
    data class Result(val analysis: ApkAnalysisResult) : ScreenState
    data class Error(val message: String) : ScreenState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val analyzer = ApkAnalyzerEngine(application)
    private val exporter = ExportManager(application)

    private val _language = MutableStateFlow(AppLanguage.KURDISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Home)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    private val _appFilterType = MutableStateFlow(AppFilterType.ALL)
    val appFilterType: StateFlow<AppFilterType> = _appFilterType.asStateFlow()

    val appCounts: StateFlow<Triple<Int, Int, Int>> = _installedApps.map { apps ->
        val total = apps.size
        val system = apps.count { it.isSystemApp }
        val user = total - system
        Triple(total, user, system)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0, 0))

    val filteredApps: StateFlow<List<InstalledAppItem>> =
        combine(_installedApps, _appSearchQuery, _appFilterType) { apps, query, filterType ->
            val q = query.trim().lowercase()
            apps.filter { app ->
                val matchesFilter = when (filterType) {
                    AppFilterType.ALL -> true
                    AppFilterType.USER -> !app.isSystemApp
                    AppFilterType.SYSTEM -> app.isSystemApp
                }
                val matchesQuery = q.isEmpty() ||
                    app.appName.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q)
                matchesFilter && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTab = MutableStateFlow(InspectionTab.TECH_STACK)
    val activeTab: StateFlow<InspectionTab> = _activeTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery: StateFlow<String> = _fileSearchQuery.asStateFlow()

    private val _selectedFileEntry = MutableStateFlow<ApkFileEntry?>(null)
    val selectedFileEntry: StateFlow<ApkFileEntry?> = _selectedFileEntry.asStateFlow()

    private val _filePreview = MutableStateFlow<FilePreviewContent?>(null)
    val filePreview: StateFlow<FilePreviewContent?> = _filePreview.asStateFlow()

    private val _htmlRawMode = MutableStateFlow(false)
    val htmlRawMode: StateFlow<Boolean> = _htmlRawMode.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _installedApps.value = runCatching {
                analyzer.loadAllInstalledApps()
            }.getOrDefault(emptyList())
            _isLoadingApps.value = false
        }
    }

    suspend fun loadAppIcon(packageName: String): Bitmap? {
        return analyzer.loadAppIconBitmap(packageName)
    }

    fun selectAppFilter(filterType: AppFilterType) {
        _appFilterType.value = filterType
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == AppLanguage.KURDISH) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.KURDISH
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun updateAppSearch(query: String) {
        _appSearchQuery.value = query
    }

    fun selectTab(tab: InspectionTab) {
        _activeTab.value = tab
    }

    fun selectFileCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun updateFileSearch(query: String) {
        _fileSearchQuery.value = query
    }

    fun setHtmlRawMode(raw: Boolean) {
        _htmlRawMode.value = raw
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun navigateBackHome() {
        _selectedFileEntry.value = null
        _filePreview.value = null
        _fileSearchQuery.value = ""
        _selectedCategory.value = FileCategory.ALL
        _activeTab.value = InspectionTab.TECH_STACK
        _screenState.value = ScreenState.Home
    }

    fun inspectInstalledApp(app: InstalledAppItem) {
        startAnalysis(
            targetLabel = app.appName,
            apkPathProvider = { app.apkPath },
            fallbackName = app.appName,
            fallbackIcon = app.icon
        )
    }

    fun inspectSelfApp() {
        val ctx = getApplication<Application>()
        val sourceDir = ctx.applicationInfo.sourceDir
        val label = ctx.applicationInfo.loadLabel(ctx.packageManager).toString()
        val icon = runCatching { ctx.applicationInfo.loadIcon(ctx.packageManager) }.getOrNull()
        startAnalysis(
            targetLabel = label,
            apkPathProvider = { sourceDir },
            fallbackName = label,
            fallbackIcon = icon
        )
    }

    fun inspectFromUri(uri: Uri) {
        viewModelScope.launch {
            _screenState.value = ScreenState.Analyzing("APK")
            try {
                val localApk = analyzer.copyUriToInspectableApk(uri)
                val result = analyzer.analyzeApk(localApk.absolutePath)
                _activeTab.value = InspectionTab.TECH_STACK
                _screenState.value = ScreenState.Result(result)
            } catch (e: Exception) {
                _screenState.value = ScreenState.Error(e.message ?: "Failed to parse APK file")
            }
        }
    }

    private fun startAnalysis(
        targetLabel: String,
        apkPathProvider: suspend () -> String,
        fallbackName: String?,
        fallbackIcon: Drawable?
    ) {
        viewModelScope.launch {
            _screenState.value = ScreenState.Analyzing(targetLabel)
            try {
                val path = apkPathProvider()
                val result = analyzer.analyzeApk(
                    apkPath = path,
                    fallbackAppName = fallbackName,
                    fallbackIcon = fallbackIcon
                )
                _activeTab.value = InspectionTab.TECH_STACK
                _screenState.value = ScreenState.Result(result)
            } catch (e: Exception) {
                _screenState.value = ScreenState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    fun openFileEntry(entry: ApkFileEntry) {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        _selectedFileEntry.value = entry
        _filePreview.value = FilePreviewContent.Loading
        viewModelScope.launch {
            _filePreview.value = analyzer.readEntryPreview(current.apkFilePath, entry)
        }
    }

    fun closeFilePreview() {
        _selectedFileEntry.value = null
        _filePreview.value = null
    }

    fun downloadHtmlReport() {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        val strings = stringsFor(_language.value)
        viewModelScope.launch {
            _isExporting.value = true
            val res = exporter.saveHtmlToDownloads(current)
            _isExporting.value = false
            _snackbarMessage.value = res.fold(
                onSuccess = { path -> "${strings.downloadSuccess}: $path" },
                onFailure = { err -> "${strings.downloadError}: ${err.message}" }
            )
        }
    }

    fun downloadZipBundle() {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        val strings = stringsFor(_language.value)
        viewModelScope.launch {
            _isExporting.value = true
            val res = exporter.saveZipBundleToDownloads(current)
            _isExporting.value = false
            _snackbarMessage.value = res.fold(
                onSuccess = { path -> "${strings.downloadSuccess}: $path" },
                onFailure = { err -> "${strings.downloadError}: ${err.message}" }
            )
        }
    }

    fun saveHtmlToUri(uri: Uri) {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        val strings = stringsFor(_language.value)
        viewModelScope.launch {
            val res = exporter.writeHtmlToUri(uri, current.generatedHtmlReport)
            _snackbarMessage.value = res.fold(
                onSuccess = { strings.downloadSuccess },
                onFailure = { "${strings.downloadError}: ${it.message}" }
            )
        }
    }

    fun saveZipToUri(uri: Uri) {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        val strings = stringsFor(_language.value)
        viewModelScope.launch {
            val res = exporter.writeZipToUri(uri, current)
            _snackbarMessage.value = res.fold(
                onSuccess = { strings.downloadSuccess },
                onFailure = { "${strings.downloadError}: ${it.message}" }
            )
        }
    }

    fun downloadSelectedFile() {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        val entry = _selectedFileEntry.value ?: return
        val strings = stringsFor(_language.value)
        viewModelScope.launch {
            _isExporting.value = true
            val res = runCatching {
                val bytes = analyzer.extractEntryRawBytes(current.apkFilePath, entry.path)
                exporter.saveSingleFileToDownloads(entry.name, bytes).getOrThrow()
            }
            _isExporting.value = false
            _snackbarMessage.value = res.fold(
                onSuccess = { path -> "${strings.downloadSuccess}: $path" },
                onFailure = { err -> "${strings.downloadError}: ${err.message}" }
            )
        }
    }

    fun shareHtml(context: Context) {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        viewModelScope.launch {
            runCatching {
                val intent = exporter.createShareHtmlIntent(current)
                context.startActivity(Intent.createChooser(intent, "Share HTML Code Report"))
            }.onFailure {
                _snackbarMessage.value = it.message
            }
        }
    }

    fun shareZip(context: Context) {
        val current = (_screenState.value as? ScreenState.Result)?.analysis ?: return
        viewModelScope.launch {
            runCatching {
                val intent = exporter.createShareZipIntent(current)
                context.startActivity(Intent.createChooser(intent, "Share Source ZIP"))
            }.onFailure {
                _snackbarMessage.value = it.message
            }
        }
    }
}
