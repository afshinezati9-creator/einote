package com.einote.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.data.NoteDatabase
import com.einote.app.data.NoteEntity
import com.einote.app.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = NoteRepository(NoteDatabase.get(application).noteDao())

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = query.flatMapLatest(repository::observeNotes)

    fun setSearchQuery(value: String) {
        query.value = value
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insert(NoteEntity())
            onCreated(id)
        }
    }

    fun getNote(id: Long, onLoaded: (NoteEntity?) -> Unit) {
        viewModelScope.launch { onLoaded(repository.getById(id)) }
    }

    fun saveNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.update(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteNote(note: NoteEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(note)
            onDone()
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.setPinned(note.id, !note.isPinned)
        }
    }

    fun archive(note: NoteEntity, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.archive(note.id)
            onDone()
        }
    }
}
