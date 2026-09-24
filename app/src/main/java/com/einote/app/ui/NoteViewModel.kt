package com.einote.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.einote.app.ReminderWorker
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteDatabase
import com.einote.app.data.NoteEntity
import com.einote.app.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NoteDatabase.get(application)
    private val repository = NoteRepository(database.noteDao(), database.noteBlockDao(), database.attachmentDao())
    private val workManager = WorkManager.getInstance(application)

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes = query.flatMapLatest(repository::observeNotes)

    private val pinnedOnly = MutableStateFlow(false)
    val pinnedOnlyFilter: StateFlow<Boolean> = pinnedOnly.asStateFlow()
    private val archivedOnly = MutableStateFlow(false)
    val archivedOnlyFilter: StateFlow<Boolean> = archivedOnly.asStateFlow()
    private val selectedTag = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = selectedTag.asStateFlow()
    private val sort = MutableStateFlow(SORT_UPDATED)
    val sortFilter: StateFlow<String> = sort.asStateFlow()

    fun setSearchQuery(value: String) { query.value = normalize(value) }
    fun setPinnedOnly(value: Boolean) { pinnedOnly.value = value }
    fun setArchivedOnly(value: Boolean) { archivedOnly.value = value }
    fun setSelectedTag(value: String?) { selectedTag.value = value?.trim()?.takeIf { it.isNotBlank() } }
    fun setSort(value: String) { sort.value = value }
    fun clearFilters() {
        pinnedOnly.value = false
        archivedOnly.value = false
        selectedTag.value = null
        sort.value = SORT_UPDATED
    }

    companion object {
        const val SORT_UPDATED = "updated"
        const val SORT_CREATED = "created"
        const val SORT_TITLE = "title"
        private fun normalize(value: String): String = value
            .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
            .replace(Regex("\\s+"), " ").trim()
    }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insert(NoteEntity())
            repository.addBlock(id, BlockType.TEXT)
            onCreated(id)
        }
    }

    fun getNote(id: Long, onLoaded: (NoteEntity?) -> Unit) {
        viewModelScope.launch { onLoaded(repository.getById(id)) }
    }

    fun observeBlocks(noteId: Long) = repository.observeBlocks(noteId)
    fun observePlannedBlocks() = repository.observePlannedBlocks()
    fun observeAttachments(noteId: Long) = repository.observeAttachments(noteId)

    fun saveNote(note: NoteEntity) = viewModelScope.launch {
        repository.update(note.copy(updatedAt = System.currentTimeMillis()))
    }

    fun addTextBlock(noteId: Long) = viewModelScope.launch { repository.addBlock(noteId, BlockType.TEXT) }
    fun addChecklistBlock(noteId: Long) = viewModelScope.launch { repository.addBlock(noteId, BlockType.CHECKLIST) }
    fun updateBlock(block: NoteBlockEntity) = viewModelScope.launch {
        repository.updateBlock(block.copy(updatedAt = System.currentTimeMillis()))
        scheduleReminder(block)
    }

    fun moveBlock(blocks: List<NoteBlockEntity>, from: Int, to: Int) = viewModelScope.launch {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return@launch
        val ordered = blocks.toMutableList().apply { add(to, removeAt(from)) }
        persistBlockOrder(ordered)
    }

    fun persistBlockOrder(ordered: List<NoteBlockEntity>) = viewModelScope.launch {
        ordered.forEachIndexed { index, block ->
            if (block.position != index) repository.updateBlockPosition(block.id, index)
        }
    }
    fun deleteBlock(block: NoteBlockEntity) = viewModelScope.launch {
        repository.deleteBlock(block)
        cancelReminder(block.id)
    }

    fun scheduleReminder(block: NoteBlockEntity) {
        val reminderAt = block.reminderAt ?: run { cancelReminder(block.id); return }
        if (block.checked || reminderAt <= System.currentTimeMillis()) return
        val delay = reminderAt - System.currentTimeMillis()
        val data = Data.Builder().putLong(ReminderWorker.KEY_BLOCK_ID, block.id).build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        workManager.enqueueUniqueWork(
            ReminderWorker.WORK_PREFIX + block.id,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(blockId: Long) {
        workManager.cancelUniqueWork(ReminderWorker.WORK_PREFIX + blockId)
    }

    fun deleteNote(note: NoteEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        repository.delete(note)
        onDone()
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch {
        repository.setPinned(note.id, !note.isPinned)
    }

    fun archive(note: NoteEntity, onDone: () -> Unit = {}) = viewModelScope.launch {
        repository.setArchived(note.id, !note.isArchived)
        onDone()
    }
}
