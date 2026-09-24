package com.einote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class NoteItem(val title:String, val preview:String)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EiNoteApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EiNoteApp() {
    var showTools by remember { mutableStateOf(false) }
    val notes = remember { mutableStateListOf(
        NoteItem("اولین یادداشت", "اینجا می‌توانی هر چیزی که می‌خواهی بنویسی…"),
        NoteItem("لیست امروز", "☐ خرید  ☐ تماس  ☐ برنامه‌ریزی")
    ) }

    MaterialTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("eiNote") },
                    actions = { IconButton(onClick={}) { Icon(Icons.Default.Search, "جستجو") } }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick={ showTools = !showTools }) {
                    Icon(if(showTools) Icons.Default.Close else Icons.Default.Add, "ابزار")
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=20.dp)) {
                Spacer(Modifier.height(16.dp))
                Text("دفتر من", style=MaterialTheme.typography.headlineMedium)
                Text("هر چیزی که می‌خواهی، همین‌جا.", style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                if(showTools) {
                    ToolGrid()
                    Spacer(Modifier.height(20.dp))
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(notes) { note ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(note.title, style=MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(note.preview, style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolGrid() {
    val tools=listOf(
        "یادداشت" to Icons.Default.Edit,
        "چک‌لیست" to Icons.Default.Check,
        "برنامه" to Icons.Default.CalendarMonth,
        "هزینه" to Icons.Default.AccountBalanceWallet,
        "صدا" to Icons.Default.Mic,
        "عکس" to Icons.Default.Photo
    )
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        tools.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach { (label,icon) ->
                    AssistChip(
                        onClick={},
                        label={Text(label)},
                        leadingIcon={Icon(icon,null)}
                    )
                }
            }
        }
    }
}
