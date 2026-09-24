package com.einote.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.data.AttachmentDao
import com.einote.app.data.AttachmentEntity
import com.einote.app.data.NoteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

class AttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: AttachmentDao = NoteDatabase.get(application).attachmentDao()
    private val app = application

    fun observe(noteId: Long): Flow<List<AttachmentEntity>> = dao.observeForNote(noteId)

    fun addFromUri(noteId: Long, uri: Uri) = viewModelScope.launch {
        val resolver = app.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: "پیوست-${System.currentTimeMillis()}"
        val safeName = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val dir = File(app.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@launch

        dao.insert(
            AttachmentEntity(
                noteId = noteId,
                fileName = name,
                mimeType = mime,
                sizeBytes = file.length(),
                localPath = file.absolutePath
            )
        )
    }

    fun delete(attachment: AttachmentEntity) = viewModelScope.launch {
        runCatching { File(attachment.localPath).delete() }
        dao.delete(attachment)
    }

    private fun queryDisplayName(uri: Uri): String? {
        app.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }
}
