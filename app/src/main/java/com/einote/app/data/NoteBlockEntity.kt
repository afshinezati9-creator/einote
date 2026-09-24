package com.einote.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_blocks",
    indices = [Index(value = ["noteId", "position"])]
)
data class NoteBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val type: String = BlockType.TEXT.name,
    val content: String = "",
    val checked: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
