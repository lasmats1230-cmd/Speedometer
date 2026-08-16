package com.lasse.speedometer.ui.tools

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lasse.speedometer.app
import com.lasse.speedometer.data.db.RouteEntity
import com.lasse.speedometer.data.io.RouteParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.app.tripRepository

    val routes: StateFlow<List<RouteEntity>> = repository.routes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun importRoute(uri: Uri, successTemplate: String, failure: String) = viewModelScope.launch {
        val context = getApplication<Application>()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName(uri) ?: "Route"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    RouteParser.parse(stream, name)
                } ?: error("Could not open $uri")
            }
        }
        result
            .onSuccess { route ->
                repository.insertRoute(route)
                _messages.emit(successTemplate.format(route.name))
            }
            .onFailure { _messages.emit(failure) }
    }

    fun deleteRoute(id: Long) = viewModelScope.launch { repository.deleteRoute(id) }

    private fun displayName(uri: Uri): String? {
        val context = getApplication<Application>()
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment
    }
}
