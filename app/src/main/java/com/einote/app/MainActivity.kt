package com.einote.app

import android.Manifest
import android.app.TimePickerDialog
import android.app.Activity
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.einote.app.security.SecurityManager
import com.einote.app.ui.SecurityViewModel
import com.einote.app.ui.SettingsViewModel
import com.einote.app.ui.EiNoteTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.einote.app.data.BlockType
import com.einote.app.data.NoteBlockEntity
import com.einote.app.data.NoteEntity
import com.einote.app.ui.NoteViewModel
import com.einote.app.ui.FinanceViewModel
import com.einote.app.ui.AttachmentViewModel
import com.einote.app.ui.BackupViewModel
import com.einote.app.data.FinanceTransactionEntity
import com.einote.app.util.PersianFormat
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EiNoteApp() }
    }
}

@Composable
fun EiNoteApp(viewModel: NoteViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel()
    val securityViewModel: SecurityViewModel = viewModel()
    val darkMode by settingsViewModel.darkMode.collectAsState()
    val accent by settingsViewModel.accent.collectAsState()
    val textScale by settingsViewModel.textScale.collectAsState()

    var editingId by remember { mutableStateOf<Long?>(null) }
    var plannerOpen by remember { mutableStateOf(false) }
    var financeOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var securityOpen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var backgroundAt by remember { mutableLongStateOf(0L) }
    val backupViewModel: BackupViewModel = viewModel()
    val financeViewModel: FinanceViewModel = viewModel()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (SecurityManager(context).isLockEnabled() && SecurityManager(context).hasPin()) {
            locked = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (SecurityManager(context).isLockEnabled()) {
                    backgroundAt = System.currentTimeMillis()
                }
            } else if (event == Lifecycle.Event.ON_START && backgroundAt > 0L) {
                val minutes = securityViewModel.autoLockMinutes.value
                if (System.currentTimeMillis() - backgroundAt >= minutes * 60_000L &&
                    SecurityManager(context).isLockEnabled() &&
                    SecurityManager(context).hasPin()
                ) {
                    locked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && settingsViewModel.notifications.value) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    EiNoteTheme(darkMode, accent, textScale) {
        when {
            locked -> LockScreen(
                viewModel = securityViewModel,
                onUnlocked = { locked = false },
                onBiometric = {
                    val activity = context as? Activity
                    if (activity != null) showBiometric(activity) { locked = false }
                }
            )
            editingId != null -> NoteEditor(
                noteId = editingId!!,
                viewModel = viewModel,
                onClose = { editingId = null },
                onReminderScheduled = ::askNotificationPermission
            )
            plannerOpen -> PlannerScreen(viewModel, onBack = { plannerOpen = false })
            financeOpen -> FinanceScreen(financeViewModel, onBack = { financeOpen = false })
            backupOpen -> BackupScreen(backupViewModel, onBack = { backupOpen = false })
            securityOpen -> SecurityScreen(securityViewModel, onBack = { securityOpen = false })
            settingsOpen -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { settingsOpen = false }
            )
            else -> HomeScreen(
                viewModel = viewModel,
                onCreate = { viewModel.createNote { editingId = it } },
                onOpen = { editingId = it },
                onPlanner = { plannerOpen = true },
                onFinance = { financeOpen = true },
                onBackup = { backupOpen = true },
                onSecurity = { securityOpen = true },
                onSettings = { settingsOpen = true }
            )
        }
    }
}

private fun showBiometric(activity: Activity, onSuccess: () -> Unit) {
    val manager = BiometricManager.from(activity)
    val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) return

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن ای‌نوت")
            .setSubtitle("برای ادامه، هویتت را تأیید کن.")
            .setAllowedAuthenticators(authenticators)
            .build()
    )
}

@Composable
private fun LockScreen(
    viewModel: SecurityViewModel,
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val canBiometric = remember {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("ای‌نوت قفل است", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("رمز ورودت را وارد کن.")
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("رمز") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (viewModel.verify(pin)) { pin = ""; onUnlocked() } },
                enabled = pin.length >= 4,
                modifier = Modifier.fillMaxWidth()
            ) { Text("باز کردن") }
            if (canBiometric) {
                TextButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("باز کردن با اثر انگشت / قفل دستگاه")
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val darkMode by viewModel.darkMode.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val pinnedFirst by viewModel.pinnedFirst.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val compactBlocks by viewModel.compactBlocks.collectAsState()

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(10.dp))
                Text("ظاهر برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("حالت تاریک", fontWeight = FontWeight.Medium)
                                Text("ظاهر آرام‌تر برای استفاده در نور کم.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = darkMode, onCheckedChange = viewModel::setDarkMode)
                        }
                        Text("رنگ تأکیدی", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "blue" to "آبی",
                                "purple" to "بنفش",
                                "green" to "سبز",
                                "orange" to "نارنجی"
                            ).forEach { (value, label) ->
                                FilterChip(
                                    selected = accent == value,
                                    onClick = { viewModel.setAccent(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("اندازه متن", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.9f to "کوچک", 1f to "معمولی", 1.1f to "بزرگ", 1.2f to "خیلی بزرگ").forEach { (value, label) ->
                                FilterChip(
                                    selected = textScale == value,
                                    onClick = { viewModel.setTextScale(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("صفحه اصلی و یادداشت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        SettingSwitchRow(
                            title = "یادداشت‌های سنجاق‌شده اول نمایش داده شوند",
                            checked = pinnedFirst,
                            onCheckedChange = viewModel::setPinnedFirst
                        )
                        SettingSwitchRow(
                            title = "نمایش یادداشت‌های بایگانی‌شده",
                            checked = showArchived,
                            onCheckedChange = viewModel::setShowArchived
                        )
                        SettingSwitchRow(
                            title = "بلوک‌های فشرده",
                            checked = compactBlocks,
                            onCheckedChange = viewModel::setCompactBlocks
                        )
                    }
                }
            }
            item {
                Text("اعلان‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    SettingSwitchRow(
                        title = "اعلان یادآوری‌ها",
                        checked = notifications,
                        onCheckedChange = viewModel::setNotifications
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("فونت", fontWeight = FontWeight.Medium)
                        Text(
                            "پشتیبانی RTL و فارسی فعال است. فونت فارسی Bundled در مرحله بعدی تکمیل انتشار منابع اضافه می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityScreen(viewModel: SecurityViewModel, onBack: () -> Unit) {
    val enabled by viewModel.enabled.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val minutes by viewModel.autoLockMinutes.collectAsState()
    val message by viewModel.message.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var disablePin by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(!hasPin) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("امنیت و قفل") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قفل برنامه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (enabled) "قفل فعال است و پس از مدتی دور بودن از برنامه، ای‌نوت دوباره رمز می‌خواهد." else "با فعال‌کردن قفل، اطلاعات دفتر در برابر دسترسی اتفاقی محافظت می‌شود.")
                }
            }

            if (!enabled || !hasPin) {
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("رمز جدید") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("تکرار رمز") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Button(
                    onClick = { if (viewModel.savePin(pin, confirm)) { pin = ""; confirm = "" } },
                    enabled = pin.length >= 4 && confirm.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("فعال‌کردن قفل") }
            } else {
                Text("قفل فعال است")
                Text("قفل خودکار بعد از $minutes دقیقه")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 15, 30).forEach { value ->
                        FilterChip(selected = minutes == value, onClick = { viewModel.setAutoLock(value) }, label = { Text("$value دقیقه") })
                    }
                }
                OutlinedTextField(
                    value = disablePin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) disablePin = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("رمز برای غیرفعال‌کردن") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedButton(
                    onClick = { if (viewModel.disable(disablePin)) disablePin = "" },
                    enabled = disablePin.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("غیرفعال‌کردن قفل") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    viewModel: NoteViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit,
    onPlanner: () -> Unit, onFinance: () -> Unit, onBackup: () -> Unit,
    onSecurity: () -> Unit, onSettings: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val query by viewModel.searchQuery.collectAsState()
    val pinnedFirst by settingsViewModel.pinnedFirst.collectAsState()
    val showArchived by settingsViewModel.showArchived.collectAsState()
    val pinnedOnly by viewModel.pinnedOnlyFilter.collectAsState()
    val archivedOnly by viewModel.archivedOnlyFilter.collectAsState()
    val selectedTag by viewModel.selectedTagFilter.collectAsState()
    val sort by viewModel.sortFilter.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }

    val allTags = remember(notes) {
        notes.flatMap { it.tags.split(',', '،').map { tag -> tag.trim() }.filter { it.isNotBlank() } }
            .distinctBy { it.replace('ي','ی').replace('ك','ک') }.sorted()
    }
    val visibleNotes = remember(notes, pinnedFirst, showArchived, pinnedOnly, archivedOnly, selectedTag, sort) {
        notes.filter { showArchived || !it.isArchived }
            .filter { !pinnedOnly || it.isPinned }
            .filter { !archivedOnly || it.isArchived }
            .filter { selectedTag == null || it.tags.split(',', '،').any { t -> t.trim().replace('ي','ی').replace('ك','ک') == selectedTag } }
            .let { list -> when(sort) {
                NoteViewModel.SORT_CREATED -> list.sortedByDescending { it.createdAt }
                NoteViewModel.SORT_TITLE -> list.sortedBy { it.title.trim().ifBlank { "یادداشت بدون عنوان" } }
                else -> if(pinnedFirst) list.sortedWith(compareByDescending<NoteEntity>{it.isPinned}.thenByDescending{it.updatedAt}) else list.sortedByDescending{it.updatedAt}
            }}
    }
    val activeFilters = listOf(pinnedOnly, archivedOnly, selectedTag != null, sort != NoteViewModel.SORT_UPDATED).count { it }

    Scaffold(
        topBar = {
            if (searchOpen) TopAppBar(
                title = { OutlinedTextField(query, viewModel::setSearchQuery, Modifier.fillMaxWidth(), singleLine=true, placeholder={Text("جستجو در عنوان، متن، برچسب، کار و پیوست…")}) },
                navigationIcon = { IconButton(onClick={searchOpen=false;viewModel.setSearchQuery("")}){Icon(Icons.Default.ArrowBack,"بازگشت")} },
                actions = { IconButton(onClick={filtersOpen=true}){Icon(Icons.Default.FilterList,"فیلترها")} }
            ) else CenterAlignedTopAppBar(
                title={Text("eiNote",fontWeight=FontWeight.SemiBold)},
                actions={
                    IconButton(onClick=onPlanner){Icon(Icons.Default.CalendarMonth,"برنامه")}
                    IconButton(onClick=onFinance){Icon(Icons.Default.AccountBalanceWallet,"مالی")}
                    IconButton(onClick=onBackup){Icon(Icons.Default.SettingsBackupRestore,"پشتیبان")}
                    IconButton(onClick=onSecurity){Icon(Icons.Default.Lock,"امنیت")}
                    IconButton(onClick=onSettings){Icon(Icons.Default.Settings,"تنظیمات")}
                    IconButton(onClick={filtersOpen::let}){Icon(Icons.Default.FilterList,"فیلترها")}
                    IconButton(onClick={ { searchOpen=true } }){Icon(Icons.Default.Search,"جستجو")}
                }
            )
        },
        floatingActionButton={FloatingActionButton(onClick=onCreate){Icon(Icons.Default.Edit,"یادداشت جدید")}}
    ){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp)){
            Spacer(Modifier.height(18.dp)); Text("دفتر من",style=MaterialTheme.typography.headlineMedium)
            Text(when {query.isNotBlank()->"نتیجه‌های جستجو";activeFilters>0->"یادداشت‌های فیلترشده";else->"هر چیزی که می‌خواهی، همین‌جا."})
            if(selectedTag!=null){Spacer(Modifier.height(8.dp));AssistChip(onClick={viewModel.setSelectedTag(null)},label={Text("#$selectedTag")},trailingIcon={Icon(Icons.Default.Close,"حذف")})}
            Spacer(Modifier.height(14.dp))
            if(visibleNotes.isEmpty()) Text(when {query.isNotBlank()->"چیزی پیدا نشد.";activeFilters>0->"یادداشتی با این فیلترها پیدا نشد.";else->"هنوز یادداشتی نداری."})
            else LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)){items(visibleNotes,key={it.id}){note->
                NoteCard(note,{onOpen(note.id)},{viewModel.togglePin(note)},{viewModel.archive(note)},{viewModel.setSelectedTag(it)})
            }}
        }
    }
    if(filtersOpen) NoteFiltersDialog(pinnedOnly,archivedOnly,selectedTag,sort,allTags,viewModel::setPinnedOnly,viewModel::setArchivedOnly,viewModel::setSelectedTag,viewModel::setSort,viewModel::clearFilters,{filtersOpen=false})
}

@Composable
private fun NoteFiltersDialog(
    pinnedOnly:Boolean, archivedOnly:Boolean, selectedTag:String?, sort:String, tags:List<String>,
    onPinnedOnly:(Boolean)->Unit, onArchivedOnly:(Boolean)->Unit, onTag:(String?)->Unit, onSort:(String)->Unit,
    onClear:()->Unit, onDismiss:()->Unit
){
    AlertDialog(onDismissRequest=onDismiss,title={Text("جستجو و فیلتر")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("وضعیت",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            SettingSwitchRow("فقط یادداشت‌های مهم",pinnedOnly,onPinnedOnly)
            SettingSwitchRow("فقط بایگانی‌شده‌ها",archivedOnly,onArchivedOnly)
            Text("مرتب‌سازی",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                FilterChip(sort==NoteViewModel.SORT_UPDATED,{onSort(NoteViewModel.SORT_UPDATED)},label={Text("آخرین ویرایش")})
                FilterChip(sort==NoteViewModel.SORT_CREATED,{onSort(NoteViewModel.SORT_CREATED)},label={Text("جدیدترین")})
                FilterChip(sort==NoteViewModel.SORT_TITLE,{onSort(NoteViewModel.SORT_TITLE)},label={Text("الفبا")})
            }
            if(tags.isNotEmpty()){
                Text("برچسب‌ها",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
                    tags.take(20).forEach{tag->FilterChip(selectedTag==tag,{onTag(if(selectedTag==tag)null else tag)},label={Text("#$tag",maxLines=1)})}
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("اعمال")}},dismissButton={TextButton(onClick={onClear();onDismiss()}){Text("پاک کردن فیلترها")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.export(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.import(it) }
    }
    var confirmRestore by remember { mutableStateOf(false) }

    BackHandler { if (!busy) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پشتیبان و بازیابی") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.Default.ArrowBack, "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پشتیبان کامل دفتر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("یادداشت‌ها، چک‌لیست‌ها، برنامه‌ریزی، یادآوری‌ها، دخل‌وخرج و همه عکس‌ها، فایل‌ها و صداها در یک فایل آفلاین ذخیره می‌شوند.")
                    Text("فرمت پشتیبان نسخه‌دار است و قبل از بازیابی، ساختار و ارتباط داده‌ها بررسی می‌شود.")
                }
            }

            Button(
                onClick = {
                    val date = PersianFormat.currentJalali().joinToString("-")
                    exportLauncher.launch("eiNote-backup-" + date + ".einote")
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Backup, null)
                Spacer(Modifier.width(8.dp))
                Text("ساخت پشتیبان")
            }

            OutlinedButton(
                onClick = { confirmRestore = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text("بازیابی پشتیبان")
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("در حال انجام عملیات…")
            }

            message?.let {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(it, Modifier.padding(14.dp))
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                "نکته: بازیابی، دفتر فعلی را با نسخه داخل پشتیبان جایگزین می‌کند. اگر فایل خراب یا ناسازگار باشد، بازیابی متوقف می‌شود.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRestore = false },
            title = { Text("بازیابی پشتیبان؟") },
            text = { Text("اطلاعات فعلی این دفتر با اطلاعات موجود در پشتیبان جایگزین می‌شود. قبل از ادامه مطمئن شو که پشتیبان درست را انتخاب می‌کنی.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "application/*"))
                }) { Text("ادامه") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun NoteCard(note:NoteEntity,onOpen:()->Unit,onPin:()->Unit,onArchive:()->Unit,onTagClick:(String)->Unit){
    var menu by remember{mutableStateOf(false)}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),onClick=onOpen){
        Column(Modifier.padding(16.dp)){
            Row(Modifier.fillMaxWidth()){
                Text(note.title.ifBlank{"یادداشت بدون عنوان"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(note.isPinned)Icon(Icons.Default.PushPin,"سنجاق",tint=MaterialTheme.colorScheme.primary)
                IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"بیشتر")}
                DropdownMenu(menu,{menu=false}){
                    DropdownMenuItem(text={Text(if(note.isPinned)"برداشتن سنجاق" else "سنجاق کردن")},onClick={menu=false;onPin()})
                    DropdownMenuItem(text={Text(if(note.isArchived)"از بایگانی خارج کن" else "بایگانی")},onClick={menu=false;onArchive()})
                }
            }
            if(note.content.isNotBlank()){Spacer(Modifier.height(6.dp));Text(note.content.take(180))}
            val tags=note.tags.split(',', '،').map{it.trim()}.filter{it.isNotBlank()}.take(6)
            if(tags.isNotEmpty()){Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){tags.forEach{tag->AssistChip(onClick={onTagClick(tag.replace('ي','ی').replace('ك','ک'))},label={Text("#$tag")})}}}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerScreen(viewModel: NoteViewModel, onBack: () -> Unit) {
    val blocks by viewModel.observePlannedBlocks().collectAsState(initial = emptyList())
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("برنامه من") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } }
            )
        }
    ) { padding ->
        if (blocks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("هنوز کاری برنامه‌ریزی نشده.", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("در یک چک‌لیست، زمان انجام کار را مشخص کن.")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(blocks, key = { it.id }) { block ->
                    PlannedBlockCard(block, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PlannedBlockCard(block: NoteBlockEntity, viewModel: NoteViewModel) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Checkbox(
                checked = block.checked,
                onCheckedChange = {
                    val updated = block.copy(
                        checked = it,
                        completedAt = if (it) System.currentTimeMillis() else null
                    )
                    viewModel.updateBlock(updated)
                    if (it) viewModel.cancelReminder(block.id)
                }
            )
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(block.content.ifBlank { "کار بدون عنوان" }, style = MaterialTheme.typography.titleMedium)
                block.dueAt?.let {
                    Text("انجام: " + PersianFormat.jalaliDateTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                block.reminderAt?.let {
                    Text("یادآوری: " + PersianFormat.jalaliDateTime(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(
    noteId: Long,
    viewModel: NoteViewModel,
    onClose: () -> Unit,
    onReminderScheduled: () -> Unit
) {
    var note by remember(noteId) { mutableStateOf<NoteEntity?>(null) }
    var loaded by remember(noteId) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val blocks by viewModel.observeBlocks(noteId).collectAsState(initial = emptyList())
    val attachmentViewModel: AttachmentViewModel = viewModel()
    val attachments by attachmentViewModel.observe(noteId).collectAsState(initial = emptyList())
    val isRecording by attachmentViewModel.isRecording.collectAsState()
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attachmentViewModel.addFromUri(noteId, it) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { attachmentViewModel.addFromUri(noteId, it) }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) attachmentViewModel.startVoiceRecording(noteId)
    }

    LaunchedEffect(noteId) {
        viewModel.getNote(noteId) { note = it; loaded = true }
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
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "ابزارها") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("متن جدید") },
                            onClick = { menu = false; viewModel.addTextBlock(noteId) }
                        )
                        DropdownMenuItem(
                            text = { Text("چک‌لیست جدید") },
                            onClick = { menu = false; viewModel.addChecklistBlock(noteId) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (current.isPinned) "برداشتن سنجاق" else "سنجاق کردن") },
                            onClick = {
                                menu = false
                                viewModel.togglePin(current)
                                note = current.copy(isPinned = !current.isPinned)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف یادداشت") },
                            onClick = { menu = false; viewModel.deleteNote(current) { onClose() } }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { note = current.copy(title = it) },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text("عنوان") }
                )
            }
            items(blocks, key = { it.id }) { block ->
                BlockEditor(block, viewModel, onReminderScheduled)
            }
            item {
                OutlinedTextField(
                    value = current.tags,
                    onValueChange = { note = current.copy(tags = it) },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("برچسب‌ها، با ویرگول جدا کن") }
                )
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.addTextBlock(noteId) }) { Text("+ متن") }
                    TextButton(onClick = { viewModel.addChecklistBlock(noteId) }) { Text("+ چک‌لیست") }
                    TextButton(onClick = { photoPicker.launch(ActivityResultContracts.PickVisualMedia.ImageOnly) }) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(4.dp))
                        Text("عکس")
                    }
                    TextButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.AttachFile, null)
                        Spacer(Modifier.width(4.dp))
                        Text("فایل")
                    }
                    TextButton(onClick = {
                        if (isRecording) attachmentViewModel.stopVoiceRecording()
                        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) {
                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRecording) "توقف ضبط" else "صدا")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("بستن") }
                }

                if (attachments.isNotEmpty()) {
                    Text("پیوست‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    attachments.forEach { attachment ->
                        when {
                            attachment.mimeType.startsWith("image/") ->
                                ImageAttachmentCard(attachment.localPath, attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                            attachment.mimeType.startsWith("audio/") ->
                                AudioAttachmentCard(attachment.localPath, attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                            else ->
                                AttachmentCard(attachment.fileName, attachment.sizeBytes) {
                                    attachmentViewModel.delete(attachment)
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    path: String,
    fileName: String,
    sizeBytes: Long,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = File(path),
                contentDescription = fileName,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(10.dp))
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(fileName, maxLines = 2)
                    Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف عکس") }
            }
        }
    }
}

@Composable
private fun AudioAttachmentCard(
    path: String,
    fileName: String,
    sizeBytes: Long,
    onDelete: () -> Unit
) {
    var playing by remember(path) { mutableStateOf(false) }
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(path) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(if (playing) Icons.Default.VolumeUp else Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2)
                Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = {
                if (playing) {
                    player?.stop()
                    player?.release()
                    player = null
                    playing = false
                } else {
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(path)
                            prepare()
                            setOnCompletionListener {
                                playing = false
                                release()
                                player = null
                            }
                            start()
                            player = this
                            playing = true
                        }
                    }
                }
            }) {
                Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "پخش/توقف")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف صدا")
            }
        }
    }
}

@Composable
private fun AttachmentCard(fileName: String, sizeBytes: Long, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Default.AttachFile, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 2)
                Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف پیوست") }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return PersianFormat.digits(bytes) + " بایت"
    if (bytes < 1024 * 1024) return PersianFormat.digits(bytes / 1024) + " کیلوبایت"
    return PersianFormat.digits(bytes / (1024 * 1024)) + " مگابایت"
}

@Composable
private fun BlockEditor(
    block: NoteBlockEntity,
    viewModel: NoteViewModel,
    onReminderScheduled: () -> Unit
) {
    var value by remember(block.id, block.content) { mutableStateOf(block.content) }
    var checked by remember(block.id, block.checked) { mutableStateOf(block.checked) }
    var expanded by remember(block.id) { mutableStateOf(true) }
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(if (block.type == BlockType.CHECKLIST.name) "چک‌لیست" else "متن", Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Default.Edit, "باز و بسته") }
                IconButton(onClick = { viewModel.deleteBlock(block) }) { Icon(Icons.Default.Delete, "حذف") }
            }

            if (expanded) {
                if (block.type == BlockType.CHECKLIST.name) {
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                val updated = block.copy(
                                    checked = it,
                                    completedAt = if (it) System.currentTimeMillis() else null
                                )
                                viewModel.updateBlock(updated)
                                if (it) viewModel.cancelReminder(block.id)
                            }
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                viewModel.updateBlock(block.copy(content = it))
                            },
                            Modifier.weight(1f),
                            placeholder = { Text("یک کار بنویس…") }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            pickJalaliDateTime(context, block.dueAt ?: System.currentTimeMillis()) {
                                if (it > System.currentTimeMillis()) {
                                    viewModel.updateBlock(block.copy(dueAt = it, reminderAt = it))
                                    onReminderScheduled()
                                }
                            }
                        }) {
                            Icon(Icons.Default.CalendarMonth, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (block.dueAt == null) "برنامه‌ریزی" else PersianFormat.jalaliDateTime(block.dueAt))
                        }
                        if (block.dueAt != null) {
                            TextButton(onClick = {
                                viewModel.updateBlock(block.copy(dueAt = null, reminderAt = null))
                                viewModel.cancelReminder(block.id)
                            }) { Text("پاک کردن") }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            viewModel.updateBlock(block.copy(content = it))
                        },
                        Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("اینجا بنویس…") }
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState(initial = emptyList())
    val income by viewModel.income.collectAsState(initial = 0L)
    val expense by viewModel.expense.collectAsState(initial = 0L)
    var showAdd by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دخل و هزینه") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "تراکنش جدید") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "تراکنش جدید") } }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("مانده", style = MaterialTheme.typography.labelLarge)
                        Text(PersianFormat.toman(income - expense), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("دریافت: " + PersianFormat.toman(income))
                        Text("پرداخت: " + PersianFormat.toman(expense))
                    }
                }
            }
            items(transactions, key = { it.id }) { transaction ->
                FinanceTransactionCard(transaction, viewModel)
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onSave = { title, amount, type, category, date ->
                viewModel.addTransaction(title, amount, type, category, date)
                showAdd = false
            }
        )
    }
}

@Composable
private fun FinanceTransactionCard(transaction: FinanceTransactionEntity, viewModel: FinanceViewModel) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(transaction.title.ifBlank { transaction.category }, fontWeight = FontWeight.Medium)
                Text(transaction.category, style = MaterialTheme.typography.labelSmall)
                Text(PersianFormat.jalaliDate(transaction.transactionAt), style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text(
                    (if (transaction.type == FinanceTransactionEntity.TYPE_INCOME) "+" else "−") +
                        PersianFormat.toman(transaction.amountToman),
                    color = if (transaction.type == FinanceTransactionEntity.TYPE_INCOME)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { viewModel.delete(transaction) }) { Text("حذف") }
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(FinanceTransactionEntity.TYPE_EXPENSE) }
    var category by remember { mutableStateOf("سایر") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تراکنش جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("عنوان") })
                OutlinedTextField(
                    amount,
                    { amount = it.filter { c -> c.isDigit() || c in '۰'..'۹' } },
                    Modifier.fillMaxWidth(),
                    label = { Text("مبلغ به تومان") },
                    supportingText = { Text("فقط عدد وارد کن") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == FinanceTransactionEntity.TYPE_EXPENSE, { type = FinanceTransactionEntity.TYPE_EXPENSE }, label = { Text("هزینه") })
                    FilterChip(type == FinanceTransactionEntity.TYPE_INCOME, { type = FinanceTransactionEntity.TYPE_INCOME }, label = { Text("درآمد") })
                }
                OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("دسته‌بندی") })
                TextButton(onClick = {
                    pickJalaliDateTime(context, date) { date = it }
                }) { Text("تاریخ: " + PersianFormat.jalaliDate(date)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = PersianFormat.latinDigits(amount).replace("٬", "").replace(",", "").toLongOrNull() ?: 0L
                if (normalized > 0) onSave(title, normalized, type, category.ifBlank { "سایر" }, date)
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun pickJalaliDateTime(
    context: android.content.Context,
    initialMillis: Long,
    onSelected: (Long) -> Unit
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val current = PersianFormat.currentJalali()
    val fields = arrayOf(
        android.widget.EditText(context).apply { hint = "سال شمسی"; setText(current.first.toString()) },
        android.widget.EditText(context).apply { hint = "ماه"; setText(current.second.toString()) },
        android.widget.EditText(context).apply { hint = "روز"; setText(current.third.toString()) }
    )
    val container = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(48, 8, 48, 0)
        fields.forEach { addView(it) }
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("تاریخ شمسی")
        .setView(container)
        .setNegativeButton("انصراف", null)
        .setPositiveButton("تأیید") { _, _ ->
            val y = fields[0].text.toString().toIntOrNull() ?: current.first
            val m = fields[1].text.toString().toIntOrNull() ?: current.second
            val d = fields[2].text.toString().toIntOrNull() ?: current.third
            onSelected(PersianFormat.jalaliToMillis(y, m, d, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE)))
        }
        .show()
}
