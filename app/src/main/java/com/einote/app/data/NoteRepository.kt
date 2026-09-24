package com.einote.app.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val dao: NoteDao,
    private val blockDao: NoteBlockDao
) {
    fun observeNotes(query: String): Flow<List<NoteEntity>> = dao.observeNotes(query)
    suspend fun getById(id: Long): NoteEntity? = dao.getById(id)
    fun observeBlocks(noteId: Long): Flow<List<NoteBlockEntity>> = blockDao.observeForNote(noteId)
    fun observePlannedBlocks(): Flow<List<NoteBlockEntity>> = blockDao.observePlanned()
    suspend fun insert(note: NoteEntity): Long = dao.insert(note)
    suspend fun update(note: NoteEntity) = dao.update(note)

    suspend fun delete(note: NoteEntity) {
        blockDao.deleteForNote(note.id)
        dao.delete(note)
    }

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    suspend fun archive(id: Long) = dao.archive(id, System.currentTimeMillis())

    suspend fun addBlock(noteId: Long, type: BlockType, content: String = ""): Long {
        return blockDao.insert(
            NoteBlockEntity(
                noteId = noteId,
                type = type.name,
                content = content,
                position = blockDao.nextPosition(noteId)
            )
        )
    }

    suspend fun updateBlock(block: NoteBlockEntity) {
        blockDao.update(block.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteBlock(block: NoteBlockEntity) = blockDao.delete(block)
}
