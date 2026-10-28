package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DocumentRef
import com.example.data.preferences.ReaderPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.parser.DocumentContent
import com.example.parser.DocumentParser
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab {
    HOME,
    BROWSE,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = DocumentRepository(application)
    val preferencesRepository = UserPreferencesRepository(application)
    val parser = DocumentParser(application)

    val preferences: StateFlow<ReaderPreferences> = preferencesRepository.preferences

    val recentDocuments: StateFlow<List<DocumentRef>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTab = MutableStateFlow(MainTab.HOME)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    private val _activeDocument = MutableStateFlow<DocumentRef?>(null)
    val activeDocument: StateFlow<DocumentRef?> = _activeDocument.asStateFlow()

    private val _activeContent = MutableStateFlow<DocumentContent?>(null)
    val activeContent: StateFlow<DocumentContent?> = _activeContent.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Seed bundled realistic sample documents on initial launch
        viewModelScope.launch {
            repository.seedSampleDocumentsIfFirstRun()
        }
    }

    fun setTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun openDocument(doc: DocumentRef) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            // Clean up previous if PDF
            (_activeContent.value as? DocumentContent.Pdf)?.close()

            val updatedRef = repository.recordDocumentOpened(
                uri = Uri.parse(doc.uri),
                isAsset = doc.isAsset,
                assetPath = doc.assetPath
            )
            _activeDocument.value = updatedRef

            val content = parser.parse(updatedRef)
            _activeContent.value = content
            _isLoading.value = false
        }
    }

    fun openFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            (_activeContent.value as? DocumentContent.Pdf)?.close()

            val recorded = repository.recordDocumentOpened(uri = uri)
            _activeDocument.value = recorded

            val content = parser.parse(recorded)
            _activeContent.value = content
            _isLoading.value = false
        }
    }

    fun reloadCurrentDocument() {
        val current = _activeDocument.value ?: return
        openDocument(current)
    }

    fun closeDocument() {
        (_activeContent.value as? DocumentContent.Pdf)?.close()
        _activeContent.value = null
        _activeDocument.value = null
    }

    fun updateReadingProgress(position: Int, total: Int) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateReadingProgress(doc.uri, position, total)
            _activeDocument.value = doc.copy(lastReadPosition = position, totalPages = total)
        }
    }

    fun toggleFavorite(doc: DocumentRef) {
        viewModelScope.launch(Dispatchers.IO) {
            val newFav = !doc.isFavorite
            repository.toggleFavorite(doc.uri, newFav)
            if (_activeDocument.value?.uri == doc.uri) {
                _activeDocument.value = _activeDocument.value?.copy(isFavorite = newFav)
            }
        }
    }

    fun removeFromHistory(doc: DocumentRef) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeFromHistory(doc.uri)
            if (_activeDocument.value?.uri == doc.uri) {
                closeDocument()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllHistory()
            closeDocument()
        }
    }

    // Preferences
    fun setThemeMode(mode: AppThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    fun setFontSize(sizeSp: Float) {
        preferencesRepository.setFontSize(sizeSp)
    }

    fun setContinuousPdfScroll(enabled: Boolean) {
        preferencesRepository.setContinuousPdfScroll(enabled)
    }

    fun setShowLineNumbers(enabled: Boolean) {
        preferencesRepository.setShowLineNumbers(enabled)
    }

    fun setWordWrap(enabled: Boolean) {
        preferencesRepository.setWordWrap(enabled)
    }

    override fun onCleared() {
        super.onCleared()
        (_activeContent.value as? DocumentContent.Pdf)?.close()
    }
}
