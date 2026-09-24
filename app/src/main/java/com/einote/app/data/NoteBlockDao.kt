package com.einote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBlockDao {
    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<NoteBlockEntity>>

    @Insert
    suspend fun insert(block: NoteBlockEntity): Long

    @Update
    suspend fun update(block: NoteBlockEntity)

    @Delete
    suspend fun delete(block: NoteBlockEntity)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM note_blocks WHERE noteId = :noteId")
    suspend fun nextPosition(noteId: Long): Int
}
