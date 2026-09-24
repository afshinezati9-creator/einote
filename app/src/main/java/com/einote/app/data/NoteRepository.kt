package com.einote.app.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeNotes(query: String): Flow<List<NoteEntity>> = dao.observeNotes(query)

    suspend fun getById(id: Long): NoteEntity? = dao.getById(id)

    suspend fun insert(note: NoteEntity): Long = dao.insert(note)

    suspend fun update(note: NoteEntity) = dao.update(note)

    suspend fun delete(note: NoteEntity) = dao.delete(note)

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    suspend fun archive(id: Long) =
        dao.archive(id, System.currentTimeMillis())
}
