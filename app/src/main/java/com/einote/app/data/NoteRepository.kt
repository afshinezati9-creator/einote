package com.einote.app.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val dao: NoteDao,
    private val blockDao: NoteBlockDao,
    private val attachmentDao: AttachmentDao
) {
    fun observeNotes(query: String, space: String): Flow<List<NoteEntity>> = dao.observeNotes(query, space)
    suspend fun getById(id: Long): NoteEntity? = dao.getById(id)
    fun observeBlocks(noteId: Long): Flow<List<NoteBlockEntity>> = blockDao.observeForNote(noteId)
    fun observePlannedBlocks(): Flow<List<NoteBlockEntity>> = blockDao.observePlanned()
    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> = attachmentDao.observeForNote(noteId)
    suspend fun insert(note: NoteEntity): Long = dao.insert(note)
    suspend fun update(note: NoteEntity) = dao.update(note)

    suspend fun delete(note: NoteEntity) {
        attachmentDao.deleteForNote(note.id)
        blockDao.deleteForNote(note.id)
        dao.delete(note)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, System.currentTimeMillis())

    suspend fun addBlock(noteId: Long, type: BlockType, content: String = ""): Long =
        addBlockAt(noteId, type, blockDao.nextPosition(noteId), content)

    suspend fun addBlockAt(noteId: Long, type: BlockType, position: Int, content: String = ""): Long {
        val safePosition = position.coerceIn(0, blockDao.nextPosition(noteId))
        blockDao.shiftPositions(noteId, safePosition, System.currentTimeMillis())
        return blockDao.insert(
            NoteBlockEntity(
                noteId = noteId,
                type = type.name,
                content = content,
                position = safePosition
            )
        )
    }

    suspend fun updateBlock(block: NoteBlockEntity) {
        blockDao.update(block.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateBlockPosition(id: Long, position: Int) {
        blockDao.updatePosition(id, position, System.currentTimeMillis())
    }

    suspend fun deleteBlock(block: NoteBlockEntity) = blockDao.delete(block)
}
