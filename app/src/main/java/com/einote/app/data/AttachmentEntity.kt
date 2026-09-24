package com.einote.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "attachments", indices = [Index(value = ["noteId"])])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val createdAt: Long = System.currentTimeMillis()
)
