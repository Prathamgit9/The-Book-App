package com.pratham.thebookapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF171613)
private val Forest = Color(0xFF202C25)
private val Forest2 = Color(0xFF2D3A31)
private val Paper = Color(0xFFF1E6D2)
private val OldPaper = Color(0xFFE4D4B9)
private val Brass = Color(0xFFC7A46A)
private val Burgundy = Color(0xFF6F2927)
private val Olive = Color(0xFF68745F)
private val Slate = Color(0xFF918E83)
private val Line = Color(0xFF5B574E)

private val Serif = FontFamily.Serif
private val UI = FontFamily.SansSerif
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)

private enum class BookStatus(val label: String) {
    READING("Currently Reading"),
    UP_NEXT("Up Next"),
    TBR("To Read"),
    HOLD("On Hold"),
    COMPLETED("Completed"),
    DNF("Did Not Finish")
}

private enum class SortMode(val label: String) {
    RECENT("Recently added"),
    TITLE("Title"),
    PROGRESS("Progress"),
    RATING("Rating")
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val pages: Int = 0,
    val cover: String = "",
    val isbn: String = "",
    val publisher: String = "",
    val published: String = "",
    val genres: List<String> = emptyList(),
    val description: String = "",
    val status: String = BookStatus.TBR.name,
    val currentPage: Int = 0,
    val started: String = "",
    val finished: String = "",
    val rating: Int = 0,
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L
)

data class ReadingSession(
    val id: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val startPage: Int,
    val endPage: Int,
    val minutes: Int,
    val note: String = ""
) {
    val pagesRead: Int get() = (endPage - startPage).coerceAtLeast(0)
    val date: String get() = try {
        Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    } catch (_: Throwable) {
        LocalDate.now().toString()
    }
}

data class CustomList(
    val id: String,
    val name: String,
    val description: String = "",
    val bookIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ActiveSession(
    val bookId: String,
    val startedAt: Long,
    val startPage: Int
)

data class AppData(
    val books: List<Book> = emptyList(),
    val sessions: List<ReadingSession> = emptyList(),
    val lists: List<CustomList> = emptyList(),
    val readingGoal: Int = 24,
    val paperTheme: Boolean = false,
    val activeSession: ActiveSession? = null
)

private class Store(context: Context) {
    private val prefs = context.getSharedPreferences("the_book_app", Context.MODE_PRIVATE)

    fun load(): AppData {
        prefs.getString("state", null)?.let {
            try { return fromJson(JSONObject(it)) } catch (_: Throwable) {}
        }
        // Migrate the first prototype.
        prefs.getString("data", null)?.let {
            try {
                val arr = JSONArray(it)
                val books = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Book(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        author = o.optString("author"),
                        pages = o.optInt("pages"),
                        cover = o.optString("cover"),
                        status = o.optString("status", BookStatus.TBR.name),
                        currentPage = o.optInt("current"),
                        started = o.optString("started"),
                        finished = o.optString("finished"),
                        rating = o.optInt("rating"),
                        notes = o.optString("notes")
                    )
                }
                return AppData(books = books)
            } catch (_: Throwable) {}
        }
        return AppData()
    }

    fun save(data: AppData) {
        prefs.edit().putString("state", toJson(data).toString()).apply()
    }

    fun exportJson(data: AppData): String = toJson(data).toString(2)
    fun importJson(raw: String): AppData? = try { fromJson(JSONObject(raw)) } catch (_: Throwable) { null }

    private fun toJson(data: AppData) = JSONObject().apply {
        put("readingGoal", data.readingGoal)
        put("paperTheme", data.paperTheme)
        data.activeSession?.let {
            put("activeSession", JSONObject().apply {
                put("bookId", it.bookId)
                put("startedAt", it.startedAt)
                put("startPage", it.startPage)
            })
        }
        put("books", JSONArray().apply { data.books.forEach { put(bookJson(it)) } })
        put("sessions", JSONArray().apply { data.sessions.forEach { put(sessionJson(it)) } })
        put("lists", JSONArray().apply { data.lists.forEach { put(listJson(it)) } })
    }

    private fun fromJson(root: JSONObject): AppData {
        val booksArray = root.optJSONArray("books") ?: JSONArray()
        val sessionsArray = root.optJSONArray("sessions") ?: JSONArray()
        val listsArray = root.optJSONArray("lists") ?: JSONArray()
        return AppData(
            books = (0 until booksArray.length()).map { bookFromJson(booksArray.getJSONObject(it)) },
            sessions = (0 until sessionsArray.length()).map { sessionFromJson(sessionsArray.getJSONObject(it)) },
            lists = (0 until listsArray.length()).map { listFromJson(listsArray.getJSONObject(it)) },
            readingGoal = root.optInt("readingGoal", 24).coerceIn(1, 365),
            paperTheme = root.optBoolean("paperTheme"),
            activeSession = root.optJSONObject("activeSession")?.let {
                ActiveSession(it.optString("bookId"), it.optLong("startedAt"), it.optInt("startPage"))
            }
        )
    }

    private fun bookJson(b: Book) = JSONObject().apply {
        put("id", b.id); put("title", b.title); put("author", b.author); put("pages", b.pages)
        put("cover", b.cover); put("isbn", b.isbn); put("publisher", b.publisher); put("published", b.published)
        put("genres", JSONArray(b.genres)); put("description", b.description); put("status", b.status)
        put("currentPage", b.currentPage); put("started", b.started); put("finished", b.finished)
        put("rating", b.rating); put("notes", b.notes); put("addedAt", b.addedAt); put("lastReadAt", b.lastReadAt)
    }

    private fun bookFromJson(o: JSONObject) = Book(
        id = o.optString("id"),
        title = o.optString("title"),
        author = o.optString("author"),
        pages = o.optInt("pages"),
        cover = o.optString("cover"),
        isbn = o.optString("isbn"),
        publisher = o.optString("publisher"),
        published = o.optString("published"),
        genres = stringsFrom(o.optJSONArray("genres")),
        description = o.optString("description"),
        status = o.optString("status", BookStatus.TBR.name),
        currentPage = o.optInt("currentPage", o.optInt("current")),
        started = o.optString("started"),
        finished = o.optString("finished"),
        rating = o.optInt("rating"),
        notes = o.optString("notes"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
        lastReadAt = o.optLong("lastReadAt")
    )

    private fun sessionJson(s: ReadingSession) = JSONObject().apply {
        put("id", s.id); put("bookId", s.bookId); put("startedAt", s.startedAt); put("endedAt", s.endedAt)
        put("startPage", s.startPage); put("endPage", s.endPage); put("minutes", s.minutes); put("note", s.note)
    }

    private fun sessionFromJson(o: JSONObject) = ReadingSession(
        id = o.optString("id"),
        bookId = o.optString("bookId"),
        startedAt = o.optLong("startedAt"),
        endedAt = o.optLong("endedAt"),
        startPage = o.optInt("startPage"),
        endPage = o.optInt("endPage"),
        minutes = o.optInt("minutes"),
        note = o.optString("note")
    )

    private fun listJson(l: CustomList) = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("description", l.description)
        put("bookIds", JSONArray(l.bookIds)); put("createdAt", l.createdAt)
    }

    private fun listFromJson(o: JSONObject) = CustomList(
        id = o.optString("id"),
        name = o.optString("name"),
        description = o.optString("description"),
        bookIds = stringsFrom(o.optJSONArray("bookIds")),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun stringsFrom(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TheBookApp(Store(this)) }
    }
}

@Composable
private fun TheBookApp(store: Store) {
    var data by remember { mutableStateOf(store.load()) }
    var destination by remember { mutableStateOf("home") }
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    var sessionBookId by remember { mutableStateOf<String?>(null) }
    var detailFrom by remember { mutableStateOf("library") }

    fun save(next: AppData) {
        data = next
        store.save(next)
    }

    val selected = data.books.firstOrNull { it.id == selectedBookId }
    val sessionBook = data.books.firstOrNull { it.id == sessionBookId }

    val scheme = if (data.paperTheme) lightColorScheme(
        primary = Color(0xFF5E4420),
        onPrimary = Paper,
        secondary = Forest,
        onSecondary = Paper,
        background = Paper,
        onBackground = Ink,
        surface = Color(0xFFF7EEDA),
        onSurface = Ink,
        surfaceVariant = OldPaper,
        onSurfaceVariant = Color(0xFF4D493F)
    ) else darkColorScheme(
        primary = Brass,
        onPrimary = Ink,
        secondary = Olive,
        onSecondary = Paper,
        background = Ink,
        onBackground = Paper,
        surface = Forest,
        onSurface = Paper,
        surfaceVariant = Forest2,
        onSurfaceVariant = OldPaper
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold),
            displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold),
            headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontFamily = UI),
            bodyMedium = TextStyle(fontFamily = UI),
            labelLarge = TextStyle(fontFamily = UI, fontWeight = FontWeight.SemiBold)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                sessionBook != null -> ReadingSessionScreen(
                    book = sessionBook,
                    active = data.activeSession?.takeIf { it.bookId == sessionBook.id },
                    onBack = { sessionBookId = null },
                    onFinish = { endPage, note, minutes -> 
                        val active = data.activeSession ?: return@ReadingSessionScreen
                        val now = System.currentTimeMillis()
                        val finished = sessionBook.pages > 0 && endPage >= sessionBook.pages
                        val updated = sessionBook.copy(
                            currentPage = endPage.coerceAtLeast(0),
                            status = if (finished) BookStatus.COMPLETED.name else BookStatus.READING.name,
                            started = sessionBook.started.ifBlank { LocalDate.now().format(dateFormatter) },
                            finished = if (finished) LocalDate.now().format(dateFormatter) else sessionBook.finished,
                            lastReadAt = now
                        )
                        save(
                            data.copy(
                                books = data.books.map { if (it.id == updated.id) updated else it },
                                sessions = data.sessions + ReadingSession(
                                    System.currentTimeMillis().toString(),
                                    sessionBook.id,
                                    active.startedAt,
                                    now,
                                    active.startPage,
                                    endPage,
                                    minutes.coerceAtLeast(1),
                                    note
                                ),
                                activeSession = null
                            )
                        )
                        sessionBookId = null
                    },
                    onPause = { endPage, note, minutes ->
                        val active = data.activeSession ?: return@ReadingSessionScreen
                        val now = System.currentTimeMillis()
                        val updated = sessionBook.copy(
                            currentPage = endPage.coerceAtLeast(0),
                            status = BookStatus.READING.name,
                            started = sessionBook.started.ifBlank { LocalDate.now().format(dateFormatter) },
                            lastReadAt = now
                        )
                        save(
                            data.copy(
                                books = data.books.map { if (it.id == updated.id) updated else it },
                                sessions = data.sessions + ReadingSession(
                                    System.currentTimeMillis().toString(),
                                    sessionBook.id,
                                    active.startedAt,
                                    now,
                                    active.startPage,
                                    endPage,
                                    minutes.coerceAtLeast(1),
                                    note
                                ),
                                activeSession = null
                            )
                        )
                        sessionBookId = null
                    }
                )
                selected != null -> BookDetailScreen(
                    book = selected,
                    sessions = data.sessions.filter { it.bookId == selected.id }.sortedByDescending { it.startedAt },
                    customLists = data.lists,
                    activeSession = data.activeSession,
                    from = detailFrom,
                    onBack = { selectedBookId = null },
                    onBookChanged = { next ->
                        save(data.copy(books = data.books.map { if (it.id == next.id) next else it }))
                    },
                    onDelete = {
                        save(
                            data.copy(
                                books = data.books.filterNot { it.id == selected.id },
                                sessions = data.sessions.filterNot { it.bookId == selected.id },
                                lists = data.lists.map { list -> list.copy(bookIds = list.bookIds.filterNot { id -> id == selected.id }) }
                            )
                        )
                        selectedBookId = null
                    },
                    onStartSession = {
                        val current = data.books.firstOrNull { it.id == selected.id } ?: return@BookDetailScreen
                        save(
                            data.copy(
                                books = data.books.map {
                                    if (it.id == selected.id) it.copy(
                                        status = BookStatus.READING.name,
                                        started = it.started.ifBlank { LocalDate.now().format(dateFormatter) }
                                    ) else it
                                },
                                activeSession = ActiveSession(selected.id, System.currentTimeMillis(), current.currentPage)
                            )
                        )
                        sessionBookId = selected.id
                    },
                    onToggleList = { listId, included ->
                        save(
                            data.copy(
                                lists = data.lists.map { list ->
                                    if (list.id != listId) list
                                    else list.copy(
                                        bookIds = if (included) (list.bookIds + selected.id).distinct()
                                        else list.bookIds.filterNot { it == selected.id }
                                    )
                                }
                            )
                        )
                    }
                )
                else -> MainShell(
                    destination = destination,
                    data = data,
                    onDestination = { destination = it },
                    onOpenBook = { id, from ->
                        selectedBookId = id
                        detailFrom = from
                    },
                    onStartSession = { id ->
                        val current = data.books.firstOrNull { it.id == id } ?: return@MainShell
                        save(
                            data.copy(
                                books = data.books.map {
                                    if (it.id == id) it.copy(
                                        status = BookStatus.READING.name,
                                        started = it.started.ifBlank { LocalDate.now().format(dateFormatter) }
                                    ) else it
                                },
                                activeSession = ActiveSession(id, System.currentTimeMillis(), current.currentPage)
                            )
                        )
                        sessionBookId = id
                    },
                    onDataChange = ::save,
                    store = store
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    destination: String,
    data: AppData,
    onDestination: (String) -> Unit,
    onOpenBook: (String, String) -> Unit,
    onStartSession: (String) -> Unit,
    onDataChange: (AppData) -> Unit,
    store: Store
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                BottomNav("home", "⌂", "Home", destination, onDestination)
                BottomNav("library", "▤", "Library", destination, onDestination)
                BottomNav("lists", "≡", "Lists", destination, onDestination)
                BottomNav("stats", "◷", "Stats", destination, onDestination)
                BottomNav("more", "⋯", "More", destination, onDestination)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (destination) {
                "home" -> HomeScreen(data, onOpenBook, onStartSession)
                "library" -> LibraryScreen(data, onOpenBook, onDataChange)
                "lists" -> ListsScreen(data, onOpenBook, onDataChange)
                "stats" -> StatsScreen(data)
                else -> MoreScreen(data, onDataChange, store)
            }
        }
    }
}

@Composable
private fun BottomNav(key: String, symbol: String, label: String, selected: String, onSelected: (String) -> Unit) {
    NavigationBarItem(
        selected = key == selected,
        onClick = { onSelected(key) },
        icon = { Text(symbol, fontFamily = Serif, fontSize = 21.sp) },
        label = { Text(label, fontSize = 10.sp) }
    )
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun BrandHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SmallLabel(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = Slate, modifier = Modifier.padding(top = 2.dp))
        }
        trailing?.invoke()
    }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text.uppercase(Locale.ENGLISH), fontFamily = UI, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp, color = Brass)
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(34.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Brass,
            topLeft = androidx.compose.ui.geometry.Offset(w * .20f, h * .16f),
            size = androidx.compose.ui.geometry.Size(w * .60f, h * .70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .10f),
            style = Stroke(width = 2.2f)
        )
        drawLine(Brass, androidx.compose.ui.geometry.Offset(w * .50f, h * .34f), androidx.compose.ui.geometry.Offset(w * .50f, h * .73f), 1.8f)
        drawLine(Brass, androidx.compose.ui.geometry.Offset(w * .50f, h * .53f), androidx.compose.ui.geometry.Offset(w * .65f, h * .44f), 1.8f)
        drawLine(Brass, androidx.compose.ui.geometry.Offset(w * .50f, h * .53f), androidx.compose.ui.geometry.Offset(w * .38f, h * .42f), 1.5f)
    }
}

@Composable
private fun Cover(book: Book, width: Dp, height: Dp) {
    Box(
        Modifier.width(width).height(height).clip(RoundedCornerShape(10.dp)).background(Forest2)
    ) {
        if (book.cover.isBlank()) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                BrandMark()
                Column {
                    Text(book.title, fontFamily = Serif, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = OldPaper, fontSize = 10.sp, maxLines = 2)
                }
            }
        } else {
            AsyncImage(model = book.cover, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun CoverRatio(book: Book, modifier: Modifier, ratio: Float = 1.47f) {
    Box(
        modifier.aspectRatio(1f / ratio).clip(RoundedCornerShape(10.dp)).background(Forest2)
    ) {
        if (book.cover.isBlank()) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                BrandMark()
                Column {
                    Text(book.title, fontFamily = Serif, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = OldPaper, fontSize = 10.sp, maxLines = 2)
                }
            }
        } else {
            AsyncImage(model = book.cover, contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun BookMiniRow(book: Book, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(book, 48.dp, 70.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, fontFamily = Serif, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = Slate, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.pages > 0) {
                val pct = (book.currentPage.toFloat() / book.pages).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                    color = Brass,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun HomeScreen(data: AppData, onOpenBook: (String, String) -> Unit, onStartSession: (String) -> Unit) {
    val current = data.books.firstOrNull { it.status == BookStatus.READING.name }
    val upNext = data.books.filter { it.status == BookStatus.UP_NEXT.name || it.status == BookStatus.TBR.name }.sortedBy { it.addedAt }
    val completed = data.books.count { it.status == BookStatus.COMPLETED.name }

    PageColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Column {
                SmallLabel("THE BOOK APP")
                Text("A quieter place for your stories.", color = Slate, fontSize = 12.sp)
            }
        }

        Text("Good " + dayPart() + ".", style = MaterialTheme.typography.displayMedium)
        Text("Read slowly. Remember more.", color = OldPaper, fontFamily = Serif, fontSize = 18.sp)
        HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 10.dp))

        SmallLabel("CURRENTLY READING")
        if (current == null) {
            EmptyPanel("Your next chapter is waiting.", "Choose something from the library and begin a reading session.")
        } else {
            HeroReadingCard(current, onOpenBook, onStartSession)
        }

        SmallLabel("UP NEXT")
        if (upNext.isEmpty()) {
            EmptyPanel("Nothing queued yet.", "Add a book from the Library and mark it Up Next.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(upNext.take(10), key = { it.id }) { book ->
                    Column(Modifier.width(116.dp).clickable { onOpenBook(book.id, "home") }) {
                        Cover(book, 116.dp, 174.dp)
                        Text(book.title, fontFamily = Serif, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        Text(book.author, color = Slate, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        SmallLabel("RECENT ACTIVITY")
        if (data.sessions.isEmpty()) {
            Text("Your reading history will appear here.", color = Slate)
        } else {
            data.sessions.sortedByDescending { it.startedAt }.take(6).forEach { session ->
                data.books.firstOrNull { it.id == session.bookId }?.let { book ->
                    BookMiniRow(
                        book,
                        { onOpenBook(book.id, "home") },
                        trailing = { Text("+" + session.pagesRead + "p", color = Brass, fontFamily = Serif, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HomeStat("Books", data.books.size.toString())
            HomeStat("Completed", completed.toString())
            HomeStat("Days", data.sessions.map { it.date }.distinct().size.toString())
            HomeStat("Goal", data.readingGoal.toString())
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Serif, fontSize = 23.sp, color = Brass, fontWeight = FontWeight.Bold)
        Text(label, color = Slate, fontSize = 10.sp)
    }
}

@Composable
private fun HeroReadingCard(book: Book, onOpenBook: (String, String) -> Unit, onStartSession: (String) -> Unit) {
    val pct = if (book.pages > 0) (book.currentPage.toFloat() / book.pages).coerceIn(0f, 1f) else 0f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable { onOpenBook(book.id, "home") }.padding(16.dp)
    ) {
        Cover(book, 112.dp, 168.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(book.title, fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = OldPaper)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Brass, trackColor = MaterialTheme.colorScheme.background)
            Text(
                if (book.pages > 0) book.currentPage.toString() + " / " + book.pages + " pages · " + (pct * 100).toInt() + "%"
                else book.currentPage.toString() + " pages",
                fontSize = 11.sp,
                color = Slate
            )
            Button(onClick = { onStartSession(book.id) }, modifier = Modifier.fillMaxWidth()) { Text("START SESSION") }
        }
    }
}

@Composable
private fun LibraryScreen(
    data: AppData,
    onOpenBook: (String, String) -> Unit,
    onDataChange: (AppData) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var sort by remember { mutableStateOf(SortMode.RECENT) }
    var grid by remember { mutableStateOf(true) }
    var showAdd by remember { mutableStateOf(false) }

    val shown = data.books
        .filter { filter == "ALL" || it.status == filter }
        .filter { (it.title + " " + it.author + " " + it.isbn).contains(query, true) }
        .let { source ->
            when (sort) {
                SortMode.RECENT -> source.sortedByDescending { it.addedAt }
                SortMode.TITLE -> source.sortedBy { it.title.lowercase(Locale.ENGLISH) }
                SortMode.PROGRESS -> source.sortedByDescending { if (it.pages > 0) it.currentPage.toFloat() / it.pages else 0f }
                SortMode.RATING -> source.sortedByDescending { it.rating }
            }
        }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp)) {
            BrandHeader(
                "YOUR SHELF",
                "Library",
                data.books.size.toString() + " books",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton({ grid = true }) { Text("GRID", color = if (grid) Brass else Slate, fontSize = 10.sp) }
                        TextButton({ grid = false }) { Text("LIST", color = if (!grid) Brass else Slate, fontSize = 10.sp) }
                    }
                }
            )

            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                singleLine = true, label = { Text("Search your shelf") }, placeholder = { Text("Title, author or ISBN") }
            )

            LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { FilterChip(filter == "ALL", { filter = "ALL" }, label = { Text("All") }) }
                BookStatus.values().forEach { status ->
                    item { FilterChip(filter == status.name, { filter = status.name }, label = { Text(status.shortLabel()) }) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SortSelector(sort) { sort = it }
                Text(shown.size.toString() + " shown", color = Slate, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            }

            if (shown.isEmpty()) {
                EmptyPanel(
                    if (data.books.isEmpty()) "Your shelves are empty." else "Nothing matches that search.",
                    "Use + ADD BOOK to find a title from Open Library."
                )
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                ) {
                    items(shown, key = { it.id }) { book ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenBook(book.id, "library") }) {
                            CoverRatio(book, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(7.dp))
                            Text(book.title, fontFamily = Serif, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(book.author, color = Slate, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (book.rating > 0) Text("★".repeat(book.rating), color = Brass, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)) {
                    items(shown, key = { it.id }) { book ->
                        BookMiniRow(book, { onOpenBook(book.id, "library") })
                        HorizontalDivider(color = Line.copy(alpha = .45f))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
            containerColor = Brass,
            contentColor = Ink
        ) { Text("+", fontSize = 27.sp) }
    }

    if (showAdd) {
        AddBookDialog(
            existing = data.books,
            onDismiss = { showAdd = false },
            onAdd = { book -> onDataChange(data.copy(books = data.books + book)); showAdd = false }
        )
    }
}

@Composable
private fun SortSelector(selected: SortMode, onSelect: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
            Text("SORT · " + selected.label, fontSize = 10.sp)
        }
        DropdownMenu(open, { open = false }) {
            SortMode.values().forEach { mode ->
                DropdownMenuItem(text = { Text(mode.label) }, onClick = { onSelect(mode); open = false })
            }
        }
    }
}

@Composable
private fun AddBookDialog(existing: List<Book>, onDismiss: () -> Unit, onAdd: (Book) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Book>>(emptyList()) }
    var selected by remember { mutableStateOf<Book?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find a book", fontFamily = Serif, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title, author or ISBN") })
                Button(
                    onClick = {
                        if (query.isBlank()) return@Button
                        loading = true; error = ""
                        scope.launch {
                            results = searchOpenLibrary(query)
                            loading = false
                            if (results.isEmpty()) error = "No matches. Try a more specific title."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "SEARCHING…" else "SEARCH OPEN LIBRARY") }

                selected?.let { chosen ->
                    Text("Selected edition", color = Brass, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    BookSelectionRow(chosen, false) { selected = null }
                } ?: LazyColumn(Modifier.heightIn(max = 330.dp)) {
                    items(results, key = { it.id }) { result ->
                        val duplicate = existing.any { it.id == result.id || (it.title.equals(result.title, true) && it.author.equals(result.author, true)) }
                        BookSelectionRow(result, duplicate) { if (!duplicate) selected = result }
                    }
                }
                if (error.isNotBlank()) Text(error, color = Burgundy, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onAdd(it.copy(status = BookStatus.TBR.name, addedAt = System.currentTimeMillis())) } },
                enabled = selected != null
            ) { Text("ADD TO SHELF", color = Brass) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
private fun BookSelectionRow(book: Book, disabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !disabled, onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(book, 46.dp, 68.dp)
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text(book.title, fontFamily = Serif, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author, color = Slate, fontSize = 11.sp)
            val info = listOfNotNull(
                book.published.takeIf(String::isNotBlank),
                book.pages.takeIf { it > 0 }?.let { it.toString() + " p" },
                book.publisher.takeIf(String::isNotBlank)
            ).joinToString(" · ")
            if (info.isNotBlank()) Text(info, color = Slate, fontSize = 10.sp, maxLines = 2)
        }
        if (disabled) Text("ADDED", color = Olive, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BookDetailScreen(
    book: Book,
    sessions: List<ReadingSession>,
    customLists: List<CustomList>,
    activeSession: ActiveSession?,
    from: String,
    onBack: () -> Unit,
    onBookChanged: (Book) -> Unit,
    onDelete: () -> Unit,
    onStartSession: () -> Unit,
    onToggleList: (String, Boolean) -> Unit
) {
    var currentPage by remember(book.id, book.currentPage) { mutableIntStateOf(book.currentPage) }
    var rating by remember(book.id, book.rating) { mutableIntStateOf(book.rating) }
    var notes by remember(book.id, book.notes) { mutableStateOf(book.notes) }
    var started by remember(book.id, book.started) { mutableStateOf(book.started) }
    var finished by remember(book.id, book.finished) { mutableStateOf(book.finished) }
    var status by remember(book.id, book.status) { mutableStateOf(book.status) }
    var showDelete by remember { mutableStateOf(false) }

    PageColumn {
        Text("‹  " + if (from == "home") "Home" else if (from == "lists") "Lists" else "Library", color = Brass, modifier = Modifier.clickable { onBack() })

        Row(Modifier.fillMaxWidth()) {
            Cover(book, 148.dp, 222.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(book.title, fontFamily = Serif, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text(book.author, color = OldPaper, fontSize = 16.sp)
                StatusMenu(status) { status = it }
                if (book.pages > 0) Text(book.pages.toString() + " pages", color = Slate)
                if (book.published.isNotBlank()) Text(book.published, color = Slate, fontSize = 12.sp)
                if (book.publisher.isNotBlank()) Text(book.publisher, color = Slate, fontSize = 12.sp)
            }
        }

        if (activeSession?.bookId == book.id) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SmallLabel("SESSION IN PROGRESS")
                        Text("Continue reading", fontFamily = Serif, fontSize = 18.sp)
                    }
                    Button(onClick = onStartSession) { Text("RESUME") }
                }
            }
        } else {
            Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) { Text("START READING SESSION") }
        }

        SmallLabel("PROGRESS")
        if (book.pages > 0) {
            val pct = (currentPage.toFloat() / book.pages).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Brass, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(currentPage.toString() + " / " + book.pages + " pages · " + (pct * 100).toInt() + "%", color = Slate)
            Slider(currentPage.toFloat(), { currentPage = it.toInt() }, valueRange = 0f..book.pages.toFloat())
        } else {
            OutlinedTextField(currentPage.toString(), { currentPage = it.filter(Char::isDigit).toIntOrNull() ?: 0 }, label = { Text("Current page") }, singleLine = true)
        }

        SmallLabel("STATUS")
        StatusChips(status) { status = it }

        SmallLabel("DATES")
        OutlinedTextField(started, { started = it }, label = { Text("Started · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(finished, { finished = it }, label = { Text("Finished · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        SmallLabel("RATING")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..5).forEach { star ->
                Text(if (star <= rating) "★" else "☆", color = Brass, fontSize = 31.sp, modifier = Modifier.clickable { rating = star })
            }
            if (rating > 0) Text("clear", color = Slate, modifier = Modifier.clickable { rating = 0 }.padding(top = 9.dp))
        }

        SmallLabel("NOTES")
        OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, placeholder = { Text("What stayed with you?") })

        if (book.description.isNotBlank()) {
            SmallLabel("ABOUT THIS EDITION")
            Text(book.description, color = OldPaper, lineHeight = 22.sp)
        }

        if (book.isbn.isNotBlank() || book.publisher.isNotBlank()) {
            SmallLabel("EDITION")
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (book.isbn.isNotBlank()) Text("ISBN  " + book.isbn, color = OldPaper)
                    if (book.publisher.isNotBlank()) Text(book.publisher, color = Slate)
                    if (book.published.isNotBlank()) Text("Published " + book.published, color = Slate)
                }
            }
        }

        if (book.genres.isNotEmpty()) {
            SmallLabel("SUBJECTS")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                book.genres.take(8).forEach { genre -> SuggestionChip(onClick = {}, label = { Text(genre, fontSize = 11.sp) }) }
            }
        }

        if (customLists.isNotEmpty()) {
            SmallLabel("YOUR LISTS")
            customLists.forEach { list ->
                val included = list.bookIds.contains(book.id)
                Row(Modifier.fillMaxWidth().clickable { onToggleList(list.id, !included) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(included, { onToggleList(list.id, it) })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(list.name, fontFamily = Serif, fontSize = 16.sp)
                        if (list.description.isNotBlank()) Text(list.description, color = Slate, fontSize = 11.sp)
                    }
                }
            }
        }

        SmallLabel("READING LOG")
        if (sessions.isEmpty()) Text("No sessions logged yet.", color = Slate)
        sessions.take(12).forEach { session ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(formatEpoch(session.startedAt), fontFamily = Serif, fontWeight = FontWeight.SemiBold)
                    Text(session.startPage.toString() + " → " + session.endPage + " · " + session.minutes + " min", color = Slate, fontSize = 12.sp)
                    if (session.note.isNotBlank()) Text("“" + session.note + "”", color = OldPaper, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Text("+" + session.pagesRead + "p", color = Brass, fontFamily = Serif, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Line.copy(alpha = .4f))
        }

        Button(
            onClick = {
                onBookChanged(
                    book.copy(
                        currentPage = currentPage.coerceAtLeast(0),
                        rating = rating,
                        notes = notes,
                        started = started,
                        finished = finished,
                        status = status
                    )
                )
                onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("SAVE CHANGES") }

        OutlinedButton(
            onClick = { showDelete = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Burgundy)
        ) { Text("REMOVE FROM SHELF") }

        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("Remove this book?") },
                text = { Text("Its reading sessions and list memberships will also be removed.") },
                confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }) { Text("REMOVE", color = Burgundy) } },
                dismissButton = { TextButton(onClick = { showDelete = false }) { Text("CANCEL") } }
            )
        }
    }
}

@Composable
private fun StatusMenu(value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(BookStatus.values().firstOrNull { it.name == value }?.label ?: value) })
        DropdownMenu(open, { open = false }) {
            BookStatus.values().forEach { status ->
                DropdownMenuItem(text = { Text(status.label) }, onClick = { onChange(status.name); open = false })
            }
        }
    }
}

@Composable
private fun StatusChips(value: String, onChange: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        BookStatus.values().forEach { status ->
            item { FilterChip(value == status.name, { onChange(status.name) }, label = { Text(status.shortLabel()) }) }
        }
    }
}

private fun BookStatus.shortLabel(): String = when (this) {
    BookStatus.READING -> "Reading"
    BookStatus.UP_NEXT -> "Up Next"
    BookStatus.TBR -> "TBR"
    BookStatus.HOLD -> "Hold"
    BookStatus.COMPLETED -> "Done"
    BookStatus.DNF -> "DNF"
}

@Composable
private fun ListsScreen(data: AppData, onOpenBook: (String, String) -> Unit, onDataChange: (AppData) -> Unit) {
    var selected by remember { mutableStateOf("reading") }
    var showCreate by remember { mutableStateOf(false) }
    val system = listOf(
        "reading" to "Reading",
        "up_next" to "Up Next",
        "tbr" to "To Read",
        "hold" to "On Hold",
        "completed" to "Completed",
        "dnf" to "Did Not Finish"
    )
    val currentCustom = data.lists.firstOrNull { it.id == selected }

    PageColumn {
        BrandHeader("ORGANISE", "Lists", "Keep intention separate from history.", trailing = {
            TextButton({ showCreate = true }) { Text("+ NEW", color = Brass) }
        })

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            system.forEach { (id, label) -> item { FilterChip(selected == id, { selected = id }, label = { Text(label) }) } }
            data.lists.forEach { list -> item { FilterChip(selected == list.id, { selected = list.id }, label = { Text(list.name) }) } }
        }

        val books = when {
            currentCustom != null -> data.books.filter { currentCustom.bookIds.contains(it.id) }
            selected == "reading" -> data.books.filter { it.status == BookStatus.READING.name }
            selected == "up_next" -> data.books.filter { it.status == BookStatus.UP_NEXT.name }
            selected == "tbr" -> data.books.filter { it.status == BookStatus.TBR.name }
            selected == "hold" -> data.books.filter { it.status == BookStatus.HOLD.name }
            selected == "completed" -> data.books.filter { it.status == BookStatus.COMPLETED.name }
            else -> data.books.filter { it.status == BookStatus.DNF.name }
        }

        currentCustom?.description?.takeIf(String::isNotBlank)?.let { Text(it, color = Slate) }

        if (books.isEmpty()) EmptyPanel("This list is quiet.", "Books you place here will stay grouped together.")
        else books.forEach { book ->
            BookMiniRow(book, { onOpenBook(book.id, "lists") })
            HorizontalDivider(color = Line.copy(alpha = .45f))
        }

        if (currentCustom != null) {
            OutlinedButton(
                onClick = {
                    onDataChange(data.copy(lists = data.lists.filterNot { it.id == currentCustom.id }))
                    selected = "reading"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("DELETE LIST") }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New list", fontFamily = Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val id = "list-" + System.currentTimeMillis()
                        onDataChange(data.copy(lists = data.lists + CustomList(id, name.trim(), desc.trim())))
                        selected = id
                        showCreate = false
                    }
                ) { Text("CREATE", color = Brass) }
            },
            dismissButton = { TextButton({ showCreate = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun StatsScreen(data: AppData) {
    val completed = data.books.count { it.status == BookStatus.COMPLETED.name }
    val pagesRead = data.sessions.sumOf { it.pagesRead }
    val minutes = data.sessions.sumOf { it.minutes }
    val readingDays = data.sessions.map { it.date }.distinct().size
    val genreCounts = data.books.flatMap { it.genres }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(8)
    val months = (0L..5L).map { YearMonth.now().minusMonths(5L - it) }
    val values = months.map { month ->
        data.sessions.filter {
            try { YearMonth.from(LocalDate.parse(it.date)) == month } catch (_: Throwable) { false }
        }.sumOf { it.pagesRead }
    }
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1

    PageColumn {
        BrandHeader("THE RECORD", "Your reading", "A record, not a competition.")

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Completed", completed.toString(), Modifier.weight(1f))
            MetricCard("Pages", pagesRead.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Reading days", readingDays.toString(), Modifier.weight(1f))
            MetricCard("Minutes", minutes.toString(), Modifier.weight(1f))
        }

        SmallLabel("GOAL")
        val goalPct = (completed.toFloat() / data.readingGoal).coerceIn(0f, 1f)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(completed.toString() + " of " + data.readingGoal + " books", fontFamily = Serif, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text((goalPct * 100).toInt().toString() + "%", color = Brass)
                }
                LinearProgressIndicator(progress = { goalPct }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Brass, trackColor = MaterialTheme.colorScheme.background)
            }
        }

        SmallLabel("PAGES READ · LAST SIX MONTHS")
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(180.dp).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEachIndexed { index, value ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height((110f * value / maxValue).dp.coerceAtLeast(4.dp))
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (value > 0) Brass else MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(months[index].month.name.take(3), color = Slate, fontSize = 9.sp)
                    }
                }
            }
        }

        SmallLabel("SHELF BY STATUS")
        MetricRow("Currently reading", data.books.count { it.status == BookStatus.READING.name }.toString())
        MetricRow("Up next", data.books.count { it.status == BookStatus.UP_NEXT.name }.toString())
        MetricRow("To read", data.books.count { it.status == BookStatus.TBR.name }.toString())
        MetricRow("On hold", data.books.count { it.status == BookStatus.HOLD.name }.toString())
        MetricRow("DNF", data.books.count { it.status == BookStatus.DNF.name }.toString())

        SmallLabel("TOP SUBJECTS")
        if (genreCounts.isEmpty()) Text("Subjects will appear as you add books from Open Library.", color = Slate)
        genreCounts.forEach { (genre, count) -> MetricRow(genre, count.toString()) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = Brass, fontFamily = Serif, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Slate, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = OldPaper)
        Text(value, color = Brass, fontFamily = Serif, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreScreen(data: AppData, onDataChange: (AppData) -> Unit, store: Store) {
    var showGoal by remember { mutableStateOf(false) }
    var goal by remember(data.readingGoal) { mutableIntStateOf(data.readingGoal) }
    var showAbout by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                raw?.let { store.importJson(it) }?.let(onDataChange)
            } catch (_: Throwable) {}
        }
    }

    PageColumn {
        BrandHeader("THE BOOK APP", "More", "Quiet controls for the quiet parts.")

        SettingsSection("READING", "A book goal gives the year a little shape.") {
            MetricRow("Yearly goal", data.readingGoal.toString() + " books")
            Button(onClick = { showGoal = true }, modifier = Modifier.fillMaxWidth()) { Text("CHANGE GOAL") }
        }

        SettingsSection("APPEARANCE", "The default is ink and forest. Paper mode is brighter.") {
            Row(Modifier.fillMaxWidth().clickable { onDataChange(data.copy(paperTheme = !data.paperTheme)) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(data.paperTheme, { onDataChange(data.copy(paperTheme = it)) })
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Paper mode", fontFamily = Serif, fontSize = 17.sp)
                    Text(if (data.paperTheme) "Warm paper background" else "Dark ink background", color = Slate, fontSize = 11.sp)
                }
            }
        }

        SettingsSection("BACKUP", "Your shelf, sessions, lists and settings stay on this device until you export them.") {
            Button(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "The Book App backup")
                        putExtra(Intent.EXTRA_TEXT, store.exportJson(data))
                    }
                    context.startActivity(Intent.createChooser(share, "Export backup"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("EXPORT BACKUP") }

            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("IMPORT BACKUP")
            }
        }

        SettingsSection("DATA", "Book metadata comes from Open Library when you search for a title. Personal reading data is stored separately in the app.") {
            Text("Books: " + data.books.size, color = OldPaper)
            Text("Sessions: " + data.sessions.size, color = OldPaper)
            Text("Custom lists: " + data.lists.size, color = OldPaper)
        }

        SettingsSection("ABOUT", "The Book App · 0.2.0") {
            OutlinedButton(onClick = { showAbout = true }, modifier = Modifier.fillMaxWidth()) { Text("ABOUT THE APP") }
        }
    }

    if (showGoal) {
        AlertDialog(
            onDismissRequest = { showGoal = false },
            title = { Text("Yearly reading goal", fontFamily = Serif, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(goal.toString() + " books", fontFamily = Serif, fontSize = 26.sp, color = Brass)
                    Slider(goal.toFloat(), { goal = it.toInt() }, valueRange = 1f..120f)
                    Text("Choose a goal that leaves room for life.", color = Slate, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { onDataChange(data.copy(readingGoal = goal)); showGoal = false }) { Text("SAVE", color = Brass) }
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(Modifier.width(10.dp))
                    Text("The Book App", fontFamily = Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "A personal reading journal designed around books rather than dashboards. " +
                        "Your library, progress, notes, ratings and reading sessions are yours.",
                    color = OldPaper,
                    lineHeight = 21.sp
                )
            },
            confirmButton = { TextButton({ showAbout = false }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallLabel(title)
        Text(body, color = Slate, fontSize = 12.sp)
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
        }
    }
}

@Composable
private fun ReadingSessionScreen(
    book: Book,
    active: ActiveSession?,
    onBack: () -> Unit,
    onFinish: (Int, String, Int) -> Unit,
    onPause: (Int, String, Int) -> Unit
) {
    var currentPage by remember(book.id, book.currentPage) { mutableIntStateOf(book.currentPage) }
    var note by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active?.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val startedAt = active?.startedAt ?: System.currentTimeMillis()
    val elapsedSeconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    val elapsedMinutes = (elapsedSeconds / 60).toInt()
    val displaySeconds = (elapsedSeconds % 60).toInt()
    val startPage = active?.startPage ?: book.currentPage
    val pages = (currentPage - startPage).coerceAtLeast(0)
    val progress = if (book.pages > 0) (currentPage.toFloat() / book.pages).coerceIn(0f, 1f) else 0f

    PageColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Brass, fontSize = 30.sp, modifier = Modifier.clickable { onBack() })
            Spacer(Modifier.width(7.dp))
            Column {
                SmallLabel("READING SESSION")
                Text(book.title, fontFamily = Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Cover(book, 126.dp, 188.dp) }

        Text(
            String.format(Locale.ENGLISH, "%02d:%02d", elapsedMinutes, displaySeconds),
            fontFamily = Serif,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Brass
        )
        Text(pages.toString() + " pages this session", color = Slate, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        SmallLabel("WHERE ARE YOU?")
        if (book.pages > 0) {
            Text(currentPage.toString() + " / " + book.pages, fontFamily = Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Brass, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Slider(currentPage.toFloat(), { currentPage = it.toInt() }, valueRange = 0f..book.pages.toFloat())
        } else {
            OutlinedTextField(currentPage.toString(), { currentPage = it.filter(Char::isDigit).toIntOrNull() ?: 0 }, label = { Text("Current page") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        SmallLabel("SESSION NOTE")
        OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text("A line, a thought, a passage to remember…") })

        Button(onClick = { onFinish(currentPage, note, elapsedMinutes) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (book.pages > 0 && currentPage >= book.pages) "FINISH BOOK" else "END SESSION")
        }
        OutlinedButton(onClick = { onPause(currentPage, note, elapsedMinutes) }, modifier = Modifier.fillMaxWidth()) { Text("PAUSE & SAVE") }

        Text(
            "Started at page " + startPage + " · " + formatEpoch(startedAt),
            color = Slate,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontFamily = Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Slate, lineHeight = 20.sp)
        }
    }
}

private fun dayPart(): String = when (LocalTime.now().hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    in 17..21 -> "evening"
    else -> "night"
}

private fun formatEpoch(epoch: Long): String = try {
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()).format(dateTimeFormatter)
} catch (_: Throwable) {
    "Unknown time"
}

private suspend fun searchOpenLibrary(query: String): List<Book> = withContext(Dispatchers.IO) {
    try {
        val url = URL(
            "https://openlibrary.org/search.json?q=" + Uri.encode(query) +
                "&limit=14&fields=key,title,author_name,cover_i,number_of_pages_median,first_publish_year,isbn,publisher,subject"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 9000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        connection.inputStream.use { stream ->
            val root = JSONObject(stream.bufferedReader().readText())
            val docs = root.optJSONArray("docs") ?: return@withContext emptyList()
            (0 until docs.length()).mapNotNull { i ->
                val o = docs.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val authors = o.optJSONArray("author_name")
                val author = if (authors != null && authors.length() > 0) authors.optString(0) else "Unknown author"
                val isbns = o.optJSONArray("isbn")
                val isbn = if (isbns != null && isbns.length() > 0) isbns.optString(0) else ""
                val coverId = o.optInt("cover_i")
                val subjects = o.optJSONArray("subject")
                val genres = if (subjects == null) emptyList() else (0 until minOf(subjects.length(), 6))
                    .mapNotNull { subjects.optString(it).takeIf(String::isNotBlank) }.distinct()
                val key = o.optString("key").ifBlank { title + "_" + author + "_" + i }
                Book(
                    id = key + "|" + isbn,
                    title = title,
                    author = author,
                    pages = o.optInt("number_of_pages_median"),
                    cover = if (coverId > 0) "https://covers.openlibrary.org/b/id/" + coverId + "-L.jpg" else "",
                    isbn = isbn,
                    publisher = o.optJSONArray("publisher")?.optString(0) ?: "",
                    published = o.optInt("first_publish_year").takeIf { it > 0 }?.toString() ?: "",
                    genres = genres
                )
            }
        }
    } catch (_: Throwable) {
        emptyList()
    }
}
