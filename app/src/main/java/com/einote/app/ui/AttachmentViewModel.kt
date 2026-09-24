package com.einote.app.ui

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.data.AttachmentDao
import com.einote.app.data.AttachmentEntity
import com.einote.app.data.NoteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: AttachmentDao = NoteDatabase.get(application).attachmentDao()
    private val app = application
    private var recorder: MediaRecorder? = null
    private var recordingNoteId: Long? = null
    private var recordingFile: File? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun observe(noteId: Long): Flow<List<AttachmentEntity>> = dao.observeForNote(noteId)

    fun addFromUri(noteId: Long, uri: Uri) = viewModelScope.launch {
        val resolver = app.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: "پیوست-${System.currentTimeMillis()}"
        copyIntoAppStorage(noteId, name, mime) {
            resolver.openInputStream(uri)?.use { input -> input.copyTo(this) }
                ?: throw IllegalStateException("فایل قابل خواندن نیست")
        }
    }

    fun startVoiceRecording(noteId: Long): Boolean {
        if (_isRecording.value) return false
        val dir = File(app.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        return runCatching {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }.also {
                recorder = it
                recordingNoteId = noteId
                recordingFile = file
                _isRecording.value = true
            }
        }.isSuccess
    }

    fun stopVoiceRecording() {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        val noteId = recordingNoteId
        recorder = null
        recordingFile = null
        recordingNoteId = null
        _isRecording.value = false
        runCatching { activeRecorder.stop() }
            .onSuccess {
                activeRecorder.release()
                if (file != null && noteId != null && file.exists() && file.length() > 0) {
                    viewModelScope.launch {
                        dao.insert(
                            AttachmentEntity(
                                noteId = noteId,
                                fileName = "یادداشت صوتی ${System.currentTimeMillis()}.m4a",
                                mimeType = "audio/mp4",
                                sizeBytes = file.length(),
                                localPath = file.absolutePath
                            )
                        )
                    }
                }
            }
            .onFailure {
                runCatching { activeRecorder.reset() }
                activeRecorder.release()
                runCatching { file?.delete() }
            }
    }

    fun delete(attachment: AttachmentEntity) = viewModelScope.launch {
        runCatching { File(attachment.localPath).delete() }
        dao.delete(attachment)
    }

    override fun onCleared() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        runCatching { recordingFile?.delete() }
        recorder = null
        super.onCleared()
    }

    private suspend fun copyIntoAppStorage(
        noteId: Long,
        name: String,
        mime: String,
        writer: java.io.OutputStream.() -> Unit
    ) {
        val safeName = name.replace(Regex("""[\/:*?"<>|]"""), "_")
        val dir = File(app.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_$safeName")
        runCatching {
            file.outputStream().use(writer)
            dao.insert(
                AttachmentEntity(
                    noteId = noteId,
                    fileName = name,
                    mimeType = mime,
                    sizeBytes = file.length(),
                    localPath = file.absolutePath
                )
            )
        }.onFailure { runCatching { file.delete() } }
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