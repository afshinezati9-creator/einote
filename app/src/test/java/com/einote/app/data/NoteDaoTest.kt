package com.einote.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteDaoTest {
    private lateinit var db: NoteDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NoteDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun insertAndReadNote() {
        val id = db.noteDao().insert(NoteEntity(title = "تست"))
        val notes = kotlinx.coroutines.runBlocking { db.noteDao().getAll() }
        assertTrue(notes.any { it.id == id && it.title == "تست" })
    }
}
