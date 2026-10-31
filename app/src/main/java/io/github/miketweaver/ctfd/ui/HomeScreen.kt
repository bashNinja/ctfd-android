package io.github.miketweaver.ctfd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miketweaver.ctfd.data.ChallengeSummary
import io.github.miketweaver.ctfd.data.Standing
import io.github.miketweaver.ctfd.data.Status
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { Challenges("Challenges"), Scoreboard("Scoreboard") }
private enum class Show { All, Unsolved, Solved }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onChallenge: (Int) -> Unit,
    onAccount: (type: String, id: Int, name: String) -> Unit,
    onAddCtf: () -> Unit,
) {
    val board by vm.board.collectAsState()
    val ctfs by vm.store.ctfs.collectAsState()
    val active by vm.store.active.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Challenges) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    // null while the search box is closed; search lives in the top bar to keep the list tall.
    var query by rememberSaveable { mutableStateOf<String?>(null) }
    val searching = query != null && tab == Tab.Challenges
    var jumping by remember { mutableStateOf(false) }

    BackHandler(enabled = searching) { query = null }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (searching) IconButton(onClick = { query = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                    }
                },
                title = {
                    if (searching) {
                        SearchField(query.orEmpty()) { query = it }
                    } else {
                        Row(
                            Modifier.clickable { menuOpen = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(active ?: "CTFd", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Filled.ArrowDropDown, "Switch CTF")
                        }
                        CtfMenu(
                            open = menuOpen,
                            names = ctfs.map { it.name },
                            active = active,
                            onDismiss = { menuOpen = false },
                            onSelect = { menuOpen = false; vm.select(it) },
                            onRemove = { menuOpen = false; confirmRemove = it },
                            onAdd = { menuOpen = false; onAddCtf() },
                        )
                    }
                },
                actions = {
                    if (searching) {
                        if (query!!.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, "Clear search")
                        }
                    } else {
                        if (tab == Tab.Challenges) {
                            IconButton(onClick = { jumping = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, "Jump to a category")
                            }
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Search, "Search challenges") }
                        }
                        IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(if (it == Tab.Challenges) Icons.Filled.Flag else Icons.Filled.EmojiEvents, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = board.loading,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (tab) {
                Tab.Challenges -> ChallengesTab(
                    board,
                    if (searching) query.orEmpty() else "",
                    jumping = jumping,
                    onJumping = { jumping = it },
                    onChallenge = onChallenge,
                )
                Tab.Scoreboard -> ScoreboardTab(board) { onAccount(it.accountType, it.accountId, it.name) }
            }
        }
    }

    confirmRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove $name?") },
            text = { Text("Its saved token is deleted from this phone. The token stays valid on the CTF until you delete it there.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    vm.remove(name)
                    if (vm.store.ctfs.value.isEmpty()) onAddCtf()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Search challenges") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun CtfMenu(
    open: Boolean,
    names: List<String>,
    active: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    DropdownMenu(open, onDismissRequest = onDismiss) {
        names.forEach { name ->
            DropdownMenuItem(
                text = { Text(name) },
                onClick = { onSelect(name) },
                leadingIcon = {
                    if (name == active) Icon(Icons.Filled.Check, "In use") else Spacer(Modifier.size(24.dp))
                },
                trailingIcon = {
                    IconButton(onClick = { onRemove(name) }) { Icon(Icons.Filled.Delete, "Remove $name") }
                },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text("Add a CTF") }, onClick = onAdd, leadingIcon = { Icon(Icons.Filled.Add, null) })
    }
}

@Composable
private fun ChallengesTab(
    board: BoardState,
    query: String,
    jumping: Boolean,
    onJumping: (Boolean) -> Unit,
    onChallenge: (Int) -> Unit,
) {
    var show by rememberSaveable { mutableStateOf(Show.All) }
    val filtered = board.challenges.filter {
        when (show) {
            Show.All -> true
            Show.Unsolved -> !it.solved
            Show.Solved -> it.solved
        } && (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true))
    }
    // Categories in the order CTFd lists their first challenge, as on the site's board.
    val categories = board.challenges.groupBy { it.category }
    // The unsolved challenge most others have solved: the same pick as `ctfd status`'s next.
    val next = board.challenges.filter { !it.solved }.maxByOrNull { it.solves ?: -1 }
    val grouped = filtered.groupBy { it.category }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Where each category's heading sits in the list, counting the rows above the board.
    val showNext = query.isBlank() && next != null && show != Show.Solved
    val lead = (if (query.isBlank()) 1 else 0) + (if (showNext) 1 else 0) + 1 +
        (if (board.loaded && filtered.isEmpty()) 1 else 0)
    val headingIndex = buildMap {
        var index = lead
        grouped.forEach { (name, rows) -> put(name, index); index += 1 + rows.size }
    }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 16.dp)) {
        if (query.isBlank()) {
            item(key = "status") { StatusHeader(board) }
            if (showNext) item(key = "next") { NextCard(next) { onChallenge(next.id) } }
        }
        item(key = "show") {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Show.entries.forEach {
                    FilterChip(selected = show == it, onClick = { show = it }, label = { Text(it.name) })
                }
            }
        }
        if (board.loaded && filtered.isEmpty()) {
            item(key = "empty") {
                Text(
                    when {
                        board.challenges.isEmpty() -> "No challenges are visible yet. That's normal before a CTF starts."
                        show == Show.Unsolved && query.isBlank() -> "Everything is solved. Nice work!"
                        else -> "Nothing matches."
                    },
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        grouped.forEach { (name, rows) ->
            // Pinned while its challenges scroll past; tapping it opens the category list.
            stickyHeader(key = "cat:$name") {
                val all = categories[name].orEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(onClickLabel = "Jump to a category") { onJumping(true) }
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        name.ifBlank { "Uncategorised" }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    // Solved out of the whole category, whatever the filters hide.
                    Text(
                        "${all.count { it.solved }}/${all.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = Mono,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rows, key = { it.id }) { ChallengeRow(it) { onChallenge(it.id) } }
        }
    }

    if (jumping) {
        CategorySheet(
            categories = categories,
            shown = grouped.keys,
            onDismiss = { onJumping(false) },
            onPick = { name ->
                onJumping(false)
                headingIndex[name]?.let { scope.launch { list.animateScrollToItem(it) } }
            },
        )
    }
}

/** Every category with its progress; picking one scrolls the board to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    categories: Map<String, List<ChallengeSummary>>,
    shown: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Categories",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(categories.entries.toList(), key = { it.key }) { (name, rows) ->
                val solved = rows.count { it.solved }
                // A category the filters or search hide has nowhere to jump to.
                val reachable = name in shown
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = reachable) { onPick(name) }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "Uncategorised" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (reachable) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LinearProgressIndicator(
                            progress = { if (rows.isEmpty()) 0f else solved.toFloat() / rows.size },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = SolvedGreen,
                            drawStopIndicator = {},
                        )
                    }
                    Text(
                        "$solved/${rows.size}",
                        fontFamily = Mono,
                        letterSpacing = 0.sp,
                        color = if (solved == rows.size) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp).widthIn(min = 40.dp),
                    )
                }
            }
        }
    }
}

/** Two lines of where you stand, so the board starts above the fold. */
@Composable
private fun StatusHeader(board: BoardState) {
    val status = board.status
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        when {
            board.error != null && status == null -> {
                Text("Couldn't load ${board.ctfName ?: "the CTF"}", style = MaterialTheme.typography.titleMedium)
                Text(board.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            status == null -> Text("Loading…", style = MaterialTheme.typography.titleMedium)
            else -> {
                val solved = board.challenges.count { it.solved }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status.place ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = Mono,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(
                            "${status.name} · ${status.score} pts · $solved/${board.challenges.size} solved",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            leaderLine(board.scoreboard, status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (board.challenges.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { solved.toFloat() / board.challenges.size },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = SolvedGreen,
                        drawStopIndicator = {},
                    )
                }
                board.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun leaderLine(scoreboard: List<Standing>, status: Status): String {
    val leader = scoreboard.firstOrNull() ?: return if (status.place == null) "Not on the board yet" else ""
    return when {
        leader.accountId == status.accountId -> "You're in the lead"
        status.place == null -> "Not on the board yet · ${leader.name} leads with ${leader.score}"
        else -> "${leader.name} leads by ${leader.score - status.score}"
    }
}

@Composable
private fun NextCard(challenge: ChallengeSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TRY NEXT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(challenge.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        challenge.category,
                        "${challenge.value} pts",
                        challenge.solves?.let { if (it == 1) "1 solve" else "$it solves" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChallengeRow(challenge: ChallengeSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (challenge.solved) Icon(Icons.Filled.CheckCircle, "Solved", tint = SolvedGreen)
            else Icon(Icons.Outlined.Circle, "Unsolved", tint = MaterialTheme.colorScheme.outline)
        },
        headlineContent = { Text(challenge.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            challenge.solves?.let { Text(if (it == 1) "1 solve" else "$it solves") }
        },
        trailingContent = { Points(challenge.value, solved = challenge.solved) },
        colors = if (challenge.solved) {
            ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.onSurfaceVariant)
        } else ListItemDefaults.colors(),
    )
}

@Composable
private fun Points(value: Int, solved: Boolean) {
    Text(
        points(value),
        style = MaterialTheme.typography.labelLarge,
        color = if (solved) SolvedGreen else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun ScoreboardTab(board: BoardState, onAccount: (Standing) -> Unit) {
    val me = board.status
    val myIndex = board.scoreboard.indexOfFirst { me != null && isMe(it, me) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            if (board.loaded && board.scoreboard.isEmpty()) {
                item {
                    Text(
                        "The scoreboard is empty or hidden. Early in an event that's normal.",
                        Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(board.scoreboard, key = { "${it.accountType}:${it.accountId}" }) { standing ->
                StandingRow(standing, mine = me != null && isMe(standing, me)) { onAccount(standing) }
            }
        }
        // You stay in view however far down the board you are, or when you aren't on it yet.
        if (me != null && board.loaded) {
            HorizontalDivider()
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                val line = when {
                    myIndex < 0 -> "Not on the board yet: solve anything to get a place"
                    myIndex == 0 -> board.scoreboard.getOrNull(1)
                        ?.let { "In the lead by ${board.scoreboard[0].score - it.score}" } ?: "In the lead"
                    else -> board.scoreboard[myIndex - 1].let { "${it.score - me.score} behind ${it.name}" }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (myIndex < 0) "—" else "${board.scoreboard[myIndex].pos}",
                        fontFamily = Mono,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.widthIn(min = 36.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("You · ${me.name}", style = MaterialTheme.typography.titleSmall)
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text("${me.score}", fontFamily = Mono,
        letterSpacing = 0.sp, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun isMe(standing: Standing, me: Status) =
    standing.accountId == me.accountId && standing.accountType == me.accountType

@Composable
private fun StandingRow(standing: Standing, mine: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                Modifier
                    .widthIn(min = 36.dp)
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${standing.pos}",
                    fontFamily = Mono,
                    letterSpacing = 0.sp,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = {
            Text(
                standing.name + if (mine) " (you)" else "",
                fontWeight = if (mine) FontWeight.Bold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (standing.members.size > 1) {
            { Text(standing.members.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null,
        trailingContent = { Text("${standing.score}", fontFamily = Mono,
        letterSpacing = 0.sp, style = MaterialTheme.typography.titleMedium) },
        colors = if (mine) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else ListItemDefaults.colors(),
    )
}
