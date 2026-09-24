package com.einote.app.ui

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.einote.app.data.AttachmentDao
import com.einote.app.data.AttachmentEntity
import com.einote.app.data.NoteDatabase
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AttachmentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = NoteDatabase.get(application)
    private val dao: AttachmentDao = database.attachmentDao()
    private val blockDao = database.noteBlockDao()
    private val app = application
    private var recorder: MediaRecorder? = null
    private var recordingNoteId: Long? = null
    private var recordingFile: File? = null
    private var recordingInsertPosition: Int? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingStartedAt = MutableStateFlow<Long?>(null)
    val recordingStartedAt: StateFlow<Long?> = _recordingStartedAt.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private var recordingTicker: kotlinx.coroutines.Job? = null

    fun observe(noteId: Long): Flow<List<AttachmentEntity>> = dao.observeForNote(noteId)

    fun addFromUri(noteId: Long, uri: Uri) = viewModelScope.launch {
        val resolver = app.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = queryDisplayName(uri) ?: "پیوست-${System.currentTimeMillis()}"
        copyIntoAppStorage(noteId, name, mime, uri, null)
    }

    fun addImageFromUri(noteId: Long, uri: Uri, position: Int? = null) = viewModelScope.launch {
        val resolver = app.contentResolver
        val mime = resolver.getType(uri) ?: "image/*"
        val name = queryDisplayName(uri) ?: "عکس-${System.currentTimeMillis()}.jpg"
        copyIntoAppStorage(noteId, name, mime, uri, BlockType.IMAGE, position)
    }

    fun startVoiceRecording(noteId: Long, insertPosition: Int? = null): Boolean {
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
                recordingInsertPosition = insertPosition
                _isRecording.value = true
                _recordingStartedAt.value = System.currentTimeMillis()
                _recordingElapsedMs.value = 0L
                recordingTicker?.cancel()
                recordingTicker = viewModelScope.launch {
                    while (_isRecording.value) {
                        _recordingElapsedMs.value = System.currentTimeMillis() - (_recordingStartedAt.value ?: System.currentTimeMillis())
                        kotlinx.coroutines.delay(250)
                    }
                }
            }
        }.isSuccess
    }

    fun pauseVoiceRecording() {
        if (!_isRecording.value) return
        runCatching { recorder?.pause() }
    }

    fun resumeVoiceRecording() {
        if (!_isRecording.value) return
        runCatching { recorder?.resume() }
    }

    fun stopVoiceRecording() {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        val noteId = recordingNoteId
        val insertPosition = recordingInsertPosition
        recorder = null
        recordingFile = null
        recordingNoteId = null
        recordingInsertPosition = null
        _isRecording.value = false
        recordingTicker?.cancel()
        recordingTicker = null
        _recordingElapsedMs.value = if (_recordingStartedAt.value != null) System.currentTimeMillis() - _recordingStartedAt.value!! else 0L
        _recordingStartedAt.value = null
        runCatching { activeRecorder.stop() }
            .onSuccess {
                activeRecorder.release()
                if (file != null && noteId != null && file.exists() && file.length() > 0) {
                    viewModelScope.launch {
                        val attachmentId = dao.insert(
                            AttachmentEntity(
                                noteId = noteId,
                                fileName = "یادداشت صوتی ${System.currentTimeMillis()}.m4a",
                                mimeType = "audio/mp4",
                                sizeBytes = file.length(),
                                localPath = file.absolutePath
                            )
                        )
                        blockDao.insert(
                            NoteBlockEntity(
                                noteId = noteId,
                                type = BlockType.AUDIO.name,
                                content = attachmentId.toString(),
                                position = insertPosition ?: blockDao.nextPosition(noteId)
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
        recordingTicker?.cancel()
        recordingTicker = null
        _isRecording.value = false
        _recordingStartedAt.value = null
        super.onCleared()
    }

    private suspend fun copyIntoAppStorage(
        noteId: Long,
        name: String,
        mime: String,
        uri: Uri,
        createBlock: BlockType?,
        position: Int? = null
    ) {
        val safeName = name.replace(Regex("""[\/:*?"<>|]"""), "_")
        val dir = File(app.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}_$safeName")
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("فایل قابل خواندن نیست")

            val attachmentId = dao.insert(
                AttachmentEntity(
                    noteId = noteId,
                    fileName = name,
                    mimeType = mime,
                    sizeBytes = file.length(),
                    localPath = file.absolutePath
                )
            )
            if (createBlock != null) {
                val targetPosition = position ?: blockDao.nextPosition(noteId)
                blockDao.shiftPositions(noteId, targetPosition, System.currentTimeMillis())
                blockDao.insert(
                    NoteBlockEntity(
                        noteId = noteId,
                        type = createBlock.name,
                        content = attachmentId.toString(),
                        position = targetPosition
                    )
                )
            }
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