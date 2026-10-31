package io.github.bashninja.ctfd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.bashninja.ctfd.data.CtfdException
import io.github.bashninja.ctfd.data.Solve
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A player's or team's place, score and solves, newest first, like `ctfd player`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    vm: AppViewModel,
    type: String,
    id: Int,
    name: String,
    onBack: () -> Unit,
    onChallenge: (Int) -> Unit,
) {
    val board by vm.board.collectAsState()
    val standing = board.scoreboard.firstOrNull { it.accountId == id && it.accountType == type }
    var solves by remember { mutableStateOf<List<Solve>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(type, id) {
        val api = vm.api() ?: return@LaunchedEffect
        try {
            solves = api.solves(type, id).sortedByDescending { it.date }
        } catch (e: CtfdException) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name.ifBlank { "Player" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        val list = solves
        when {
            list == null && error != null -> Text(error!!, Modifier.padding(padding).padding(24.dp))
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                standing?.let { "#${it.pos} · ${it.score} points" } ?: "Not on the board",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        },
                        supportingContent = {
                            val members = standing?.members.orEmpty()
                            Text(
                                (if (list.size == 1) "1 solve" else "${list.size} solves") +
                                    if (members.size > 1) " · " + members.joinToString { it.name } else ""
                            )
                        },
                    )
                }
                items(list, key = { "${it.challengeId}:${it.date}" }) { solve ->
                    val solved = board.challenges.firstOrNull { it.id == solve.challengeId }
                    ListItem(
                        modifier = Modifier.clickable { onChallenge(solve.challengeId) },
                        headlineContent = { Text(solve.challenge?.name ?: solved?.name ?: "#${solve.challengeId}") },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    solve.challenge?.category ?: solved?.category,
                                    formatDate(solve.date),
                                    solve.user?.name?.takeIf { type == "team" }?.let { "by $it" },
                                ).joinToString(" · ")
                            )
                        },
                        trailingContent = {
                            Text("${solve.challenge?.value ?: solved?.value ?: ""}", style = MaterialTheme.typography.titleMedium)
                        },
                    )
                }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

private fun formatDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(dateFormat)
}.getOrNull()
