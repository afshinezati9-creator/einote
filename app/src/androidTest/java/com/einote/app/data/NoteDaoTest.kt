package com.einote.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var db: NoteDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NoteDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndReadNote() = runBlocking {
        val id = db.noteDao().insert(
            NoteEntity(
                title = "یادداشت تست",
                content = "متن تست",
                tags = "کار,تست"
            )
        )

        val notes = db.noteDao().getAll()

        assertTrue(notes.any { it.id == id })
        assertEquals("یادداشت تست", notes.single { it.id == id }.title)
    }

    @Test
    fun archiveCanBeToggledBothWays() = runBlocking {
        val id = db.noteDao().insert(NoteEntity(title = "بایگانی"))

        db.noteDao().setArchived(id, true, 200L)
        assertTrue(db.noteDao().getById(id)!!.isArchived)

        db.noteDao().setArchived(id, false, 300L)
        assertTrue(!db.noteDao().getById(id)!!.isArchived)
    }
}
