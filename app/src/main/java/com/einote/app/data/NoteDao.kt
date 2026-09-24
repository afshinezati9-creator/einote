package com.einote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("""
        SELECT * FROM notes
        WHERE (
            :query = '' OR
            title LIKE '%' || :query || '%' OR
            content LIKE '%' || :query || '%' OR
            tags LIKE '%' || :query || '%' OR
            EXISTS (SELECT 1 FROM note_blocks WHERE note_blocks.noteId = notes.id AND note_blocks.content LIKE '%' || :query || '%') OR
            EXISTS (SELECT 1 FROM attachments WHERE attachments.noteId = notes.id AND attachments.fileName LIKE '%' || :query || '%')
        )
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun observeNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun getAll(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)
}
