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
        WHERE isArchived = 0
        AND (
            :query = '' OR
            title LIKE '%' || :query || '%' OR
            content LIKE '%' || :query || '%' OR
            tags LIKE '%' || :query || '%'
        )
        ORDER BY isPinned DESC, updatedAt DESC
    """)
    fun observeNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long)
}
