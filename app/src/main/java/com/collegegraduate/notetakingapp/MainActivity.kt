package com.collegegraduate.notetakingapp

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Application class to initialize database
class NotesApplication : Application() {
    val database: NoteDatabase by lazy { NoteDatabase.getDatabase(this) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesAppTheme {
                val app = application as NotesApplication
                val notesViewModel: NotesViewModel = viewModel(
                    factory = NotesViewModelFactory(app)
                )
                NotesApp(notesViewModel)
            }
        }
    }
}

// Room Database Setup
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val colorInt: Int,
    val isPinned: Boolean = false
)

data class Note(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val color: Color = noteColors.random(),
    val isPinned: Boolean = false
) {
    fun toEntity(): NoteEntity {
        return NoteEntity(
            id = id.toString(),
            title = title,
            content = content,
            timestamp = timestamp,
            colorInt = color.toArgb(),
            isPinned = isPinned
        )
    }

    companion object {
        fun fromEntity(entity: NoteEntity): Note {
            return Note(
                id = UUID.fromString(entity.id),
                title = entity.title,
                content = entity.content,
                timestamp = entity.timestamp,
                color = Color(entity.colorInt),
                isPinned = entity.isPinned
            )
        }
    }
}

val noteColors = listOf(
    Color(0xFF1E1E2C),
    Color(0xFF252542),
    Color(0xFF2C2C2C),
    Color(0xFF28282B),
    Color(0xFF202030)
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Application): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val noteDao = (application as NotesApplication).database.noteDao()
    private val _notes = mutableStateListOf<Note>()
    val notes: List<Note> get() = _notes

    init {
        // Load notes from database
        viewModelScope.launch {
            noteDao.getAllNotes().collect { entities ->
                _notes.clear()
                _notes.addAll(entities.map { Note.fromEntity(it) })

                // Add sample notes if empty (only on first launch)
                if (entities.isEmpty()) {
                    addInitialNotes()
                }
            }
        }
    }

    private suspend fun addInitialNotes() {
        val welcomeNote = Note(
            title = "Welcome to Notes App",
            content = "This is a feature-rich notes app built with Jetpack Compose. Tap on a note to view or edit it."
        )
        noteDao.insert(welcomeNote.toEntity())

        val shoppingNote = Note(
            title = "Shopping List",
            content = "- Milk\n- Eggs\n- Bread\n- Fruits\n- Vegetables"
        )
        noteDao.insert(shoppingNote.toEntity())

        val projectNote = Note(
            title = "Project Ideas",
            content = "1. Build a weather app\n2. Create a recipe manager\n3. Design a budget tracker"
        )
        noteDao.insert(projectNote.toEntity())
    }

    fun addNote(title: String, content: String) {
        val newNote = Note(title = title, content = content)
        viewModelScope.launch {
            noteDao.insert(newNote.toEntity())
        }
    }

    fun updateNote(updatedNote: Note) {
        viewModelScope.launch {
            noteDao.update(updatedNote.toEntity())
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note.toEntity())
        }
    }

    fun togglePinStatus(note: Note) {
        val updatedNote = note.copy(isPinned = !note.isPinned)
        viewModelScope.launch {
            noteDao.update(updatedNote.toEntity())
        }
    }
}

class NotesViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(notesViewModel: NotesViewModel) {
    val notes = notesViewModel.notes
    var selectedNote by remember { mutableStateOf<Note?>(null) }
    var isAddingNote by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val filteredNotes = if (searchQuery.isEmpty()) {
        notes
    } else {
        notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notes...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("My Notes")
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isAddingNote = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Note list
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No notes yet. Add one!" else "No matching notes found.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { selectedNote = note },
                            onLongPress = {
                                notesViewModel.togglePinStatus(note)
                                coroutineScope.launch {
                                    val message = if (note.isPinned) "Note unpinned" else "Note pinned"
                                    snackbarHostState.showSnackbar(message)
                                }
                            },
                            onDelete = {
                                notesViewModel.deleteNote(note)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Note deleted")
                                }
                            }
                        )
                    }
                }
            }

            // Add/Edit Note dialog
            AnimatedVisibility(
                visible = isAddingNote || selectedNote != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NoteDialog(
                    note = selectedNote,
                    onDismiss = {
                        selectedNote = null
                        isAddingNote = false
                    },
                    onSave = { title, content ->
                        if (selectedNote != null) {
                            notesViewModel.updateNote(selectedNote!!.copy(title = title, content = content))
                        } else {
                            notesViewModel.addNote(title, content)
                        }
                        selectedNote = null
                        isAddingNote = false
                    },
                    onDelete = {
                        selectedNote?.let { notesViewModel.deleteNote(it) }
                        selectedNote = null
                        isAddingNote = false
                    },
                    onTogglePin = {
                        selectedNote?.let {
                            notesViewModel.togglePinStatus(it)
                            selectedNote = selectedNote!!.copy(isPinned = !selectedNote!!.isPinned)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(note.timestamp) { formatter.format(Date(note.timestamp)) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var longPressDetected by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    longPressDetected = true
                    coroutineScope.launch {
                        delay(800)  // Show options after a short delay
                        if (longPressDetected) {
                            onLongPress()
                        }
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = note.color
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content,
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Delete icon button
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val isEditMode = note != null
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    val focusRequester = remember { FocusRequester() }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }

                Row {
                    if (isEditMode) {
                        // Pin toggle
                        IconButton(onClick = onTogglePin) {
                            Icon(
                                if (note?.isPinned == true) Icons.Default.PushPin else Icons.Filled.PushPin,
                                contentDescription = if (note?.isPinned == true) "Unpin" else "Pin",
                                tint = if (note?.isPinned == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        // Delete button
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (title.isNotBlank() || content.isNotBlank()) {
                                onSave(title, content)
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            }

            // Title field
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Title") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content field
            TextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("Note content") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun NotesAppTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF3B82F6),
        primaryContainer = Color(0xFF1E40AF),
        secondary = Color(0xFF10B981),
        secondaryContainer = Color(0xFF065F46),
        tertiary = Color(0xFFF59E0B),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        error = Color(0xFFEF4444),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.Black,
        onBackground = Color(0xFFE5E5E5),
        onSurface = Color(0xFFE5E5E5),
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content
    )
}