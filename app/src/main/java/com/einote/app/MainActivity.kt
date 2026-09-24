package com.einote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteEntity
import com.einote.app.ui.NoteViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EiNoteApp() }
    }
}

@Composable
fun EiNoteApp(noteViewModel: NoteViewModel = viewModel()) {
    var editingId by remember { mutableStateOf<Long?>(null) }

    MaterialTheme {
        if (editingId != null) {
            NoteEditor(
                noteId = editingId!!,
                viewModel = noteViewModel,
                onClose = { editingId = null }
            )
        } else {
            HomeScreen(
                viewModel = noteViewModel,
                onCreate = {
                    noteViewModel.createNote { editingId = it }
                },
                onOpen = { editingId = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: NoteViewModel,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val query by viewModel.searchQuery.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (searchOpen) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("جستجو در دفتر…") }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            searchOpen = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, "بازگشت")
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("eiNote", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, "جستجو")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Edit, "یادداشت جدید")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text("دفتر من", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (query.isBlank()) "هر چیزی که می‌خواهی، همین‌جا."
                else "نتیجه‌های جستجو",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))

            if (notes.isEmpty()) {
                EmptyNotes(query.isNotBlank())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { onOpen(note.id) },
                            onPin = { viewModel.togglePin(note) },
                            onArchive = { viewModel.archive(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotes(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 70.dp)
    ) {
        Text(
            if (isSearching) "چیزی پیدا نشد." else "هنوز یادداشتی نداری.",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isSearching) "عنوان، متن یا برچسب دیگری را امتحان کن."
            else "با دکمه پایین، اولین صفحه دفترت را بساز.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        onClick = onOpen
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (note.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "سنجاق شده",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = note.title.ifBlank { "یادداشت بدون عنوان" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "بیشتر")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (note.isPinned) "برداشتن سنجاق" else "سنجاق کردن") },
                        leadingIcon = { Icon(Icons.Default.Star, null) },
                        onClick = {
                            menuOpen = false
                            onPin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("بایگانی") },
                        leadingIcon = { Icon(Icons.Default.Archive, null) },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        }
                    )
                }
            }

            if (note.content.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = note.content.take(180),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (note.tags.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = note.tags.split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .joinToString("  •  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    noteId: Long,
    viewModel: NoteViewModel,
    onClose: () -> Unit
) {
    var note by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var loaded by remember(noteId) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val blocks by viewModel.observeBlocks(noteId).collectAsState(initial = emptyList())

    LaunchedEffect(noteId) {
        viewModel.getNote(noteId) {
            note = it
            loaded = true
        }
    }

    BackHandler { onClose() }

    if (!loaded || note == null) {
        Surface(Modifier.fillMaxSize()) {}
        return
    }

    val current = note!!

    LaunchedEffect(current.title, current.tags) {
        delay(500)
        viewModel.saveNote(current)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title.ifBlank { "یادداشت جدید" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "بازگشت")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "ابزارها")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("متن جدید") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.addTextBlock(noteId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("چک‌لیست جدید") },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.addChecklistBlock(noteId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (current.isPinned) "برداشتن سنجاق" else "سنجاق کردن") },
                            leadingIcon = { Icon(Icons.Default.PushPin, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.togglePin(current)
                                note = current.copy(isPinned = !current.isPinned)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف یادداشت") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.deleteNote(current) { onClose() }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { note = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text("عنوان") }
                )
            }

            items(blocks, key = { it.id }) { block ->
                BlockEditor(
                    block = block,
                    viewModel = viewModel
                )
            }

            item {
                OutlinedTextField(
                    value = current.tags,
                    onValueChange = { note = current.copy(tags = it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("برچسب‌ها، با ویرگول جدا کن") }
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { viewModel.addTextBlock(noteId) }) {
                        Text("+ متن")
                    }
                    TextButton(onClick = { viewModel.addChecklistBlock(noteId) }) {
                        Text("+ چک‌لیست")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) {
                        Text("بستن")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun BlockEditor(
    block: NoteBlockEntity,
    viewModel: NoteViewModel
) {
    var value by remember(block.id, block.content) { mutableStateOf(block.content) }
    var checked by remember(block.id, block.checked) { mutableStateOf(block.checked) }
    var expanded by remember(block.id) { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (block.type == BlockType.CHECKLIST.name) "چک‌لیست" else "متن",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.Close else Icons.Default.Edit,
                        "نمایش"
                    )
                }
                IconButton(onClick = { viewModel.deleteBlock(block) }) {
                    Icon(Icons.Default.Delete, "حذف بلوک")
                }
            }

            if (expanded) {
                if (block.type == BlockType.CHECKLIST.name) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                viewModel.updateBlock(block.copy(checked = it))
                            }
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                viewModel.updateBlock(block.copy(content = it))
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("یک کار بنویس…") }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            viewModel.updateBlock(block.copy(content = it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("اینجا بنویس…") }
                    )
                }
            }
        }
    }
}
