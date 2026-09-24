package com.einote.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt DESC, id DESC")
    fun observeForNote(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY id ASC")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Insert
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
