package io.github.bashninja.ctfd.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bashninja.ctfd.data.AttemptResult
import io.github.bashninja.ctfd.data.ChallengeDetail
import io.github.bashninja.ctfd.data.CtfdApi
import io.github.bashninja.ctfd.data.CtfdException
import io.github.bashninja.ctfd.data.Hint
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(vm: AppViewModel, id: Int, onBack: () -> Unit, onChallenge: (Int) -> Unit) {
    val api = remember { vm.api() }
    val board by vm.board.collectAsState()
    val summary = board.challenges.firstOrNull { it.id == id }
    var detail by remember { mutableStateOf<ChallengeDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, reload) {
        if (api == null) return@LaunchedEffect
        try {
            detail = api.challenge(id)
            error = null
        } catch (e: CtfdException) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: summary?.name ?: "Challenge") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            val d = detail
            if (d != null && api != null) {
                FlagBar(d) { flag ->
                    val result = api.attempt(d.id, flag)
                    if (result.status == "correct" || result.status == "already_solved") {
                        vm.markSolved(d.id)
                    }
                    reload++
                    result
                }
            }
        },
    ) { padding ->
        val d = detail
        when {
            d == null && error != null -> Text(error!!, Modifier.padding(padding).padding(24.dp))
            d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> ChallengeBody(
                d,
                api,
                Modifier.padding(padding),
                onUnlock = { hint ->
                    scope.launch {
                        try {
                            api!!.unlockHint(hint.id)
                            vm.refresh()
                            reload++
                        } catch (e: CtfdException) {
                            error = e.message
                        }
                    }
                },
                onDownload = { context, path ->
                    downloadFile(context, api!!, vm.store.activeCtf()?.name ?: "ctf", d.name, path)
                },
                onNext = onChallenge,
                error = error,
            )
        }
    }
}

@Composable
private fun ChallengeBody(
    d: ChallengeDetail,
    api: CtfdApi?,
    modifier: Modifier,
    onUnlock: (Hint) -> Unit,
    onDownload: (android.content.Context, String) -> String,
    onNext: (Int) -> Unit,
    error: String?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var unlocking by remember { mutableStateOf<Hint?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                points(d.value),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                listOfNotNull(d.category, d.solves?.let { if (it == 1) "1 solve" else "$it solves" }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d.solved) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = SolvedGreen)
                Text("Solved", color = SolvedGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        d.description?.takeIf { it.isNotBlank() }?.let { MarkdownText(it, api, Modifier.fillMaxWidth()) }

        d.connectionInfo?.takeIf { it.isNotBlank() }?.let { info ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(info, fontFamily = Mono, style = MaterialTheme.typography.bodyMedium, letterSpacing = 0.sp)
                    }
                    if (info.trim().matches(Regex("""https?://\S+"""))) {
                        IconButton(onClick = { uriHandler.openUri(info.trim()) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("connection", info))) }
                    }) { Icon(Icons.Filled.ContentCopy, "Copy connection info") }
                }
            }
        }

        if (d.files.isNotEmpty()) {
            Text("Files", style = MaterialTheme.typography.titleMedium)
            d.files.forEach { path ->
                OutlinedButton(
                    onClick = {
                        val where = runCatching { onDownload(context, path) }
                        Toast.makeText(
                            context,
                            where.fold({ "Downloading to $it" }, { "Download failed: ${it.message}" }),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                    Text(fileName(path), Modifier.padding(start = 8.dp).weight(1f))
                }
            }
        }

        if (d.hints.isNotEmpty()) {
            Text("Hints", style = MaterialTheme.typography.titleMedium)
            d.hints.forEach { hint ->
                val label = hint.title?.takeIf { it.isNotBlank() } ?: "Hint"
                if (hint.content != null) {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lightbulb, null, Modifier.size(18.dp))
                                Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            MarkdownText(hint.content, api)
                        }
                    }
                } else {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Lock, null) },
                        headlineContent = { Text(label) },
                        supportingContent = { Text(if (hint.cost == 0) "Free" else "Costs ${hint.cost} points") },
                        trailingContent = { TextButton(onClick = { unlocking = hint }) { Text("Unlock") } },
                    )
                }
            }
        }

        if (d.maxAttempts > 0) {
            Text(
                "${d.attempts} of ${d.maxAttempts} attempts used",
                style = MaterialTheme.typography.bodyMedium,
                color = if (d.attempts >= d.maxAttempts) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        d.attribution?.takeIf { it.isNotBlank() }?.let {
            Text("By $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.nextId?.let { next ->
            if (d.solved) {
                TextButton(onClick = { onNext(next) }) {
                    Text("Next challenge")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(start = 4.dp))
                }
            }
        }
    }

    unlocking?.let { hint ->
        AlertDialog(
            onDismissRequest = { unlocking = null },
            title = { Text("Unlock this hint?") },
            text = {
                Text(
                    if (hint.cost == 0) "This hint is free."
                    else "Unlocking costs ${hint.cost} points. CTFd takes them off your score and you can't undo it."
                )
            },
            confirmButton = {
                TextButton(onClick = { unlocking = null; onUnlock(hint) }) {
                    Text(if (hint.cost == 0) "Unlock" else "Spend ${hint.cost} points")
                }
            },
            dismissButton = { TextButton(onClick = { unlocking = null }) { Text("Cancel") } },
        )
    }
}

/** The flag box pinned above the keyboard. One submission per tap, never retried. */
@Composable
private fun FlagBar(d: ChallengeDetail, submit: suspend (String) -> AttemptResult) {
    var flag by rememberSaveable(d.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember(d.id) { mutableStateOf<AttemptResult?>(null) }
    var failure by remember(d.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val outOfAttempts = d.maxAttempts > 0 && d.attempts >= d.maxAttempts && !d.solved

    fun send() {
        if (flag.isBlank() || busy) return
        busy = true
        failure = null
        scope.launch {
            try {
                result = submit(flag.trim())
                if (result?.status == "correct") flag = ""
            } catch (e: CtfdException) {
                failure = e.message
            }
            busy = false
        }
    }

    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
        Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result?.let { ResultLine(it) }
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = flag,
                    onValueChange = { flag = it },
                    placeholder = {
                        Text(
                            when {
                                d.solved -> "Already solved"
                                outOfAttempts -> "No attempts left"
                                else -> "Flag"
                            }
                        )
                    },
                    singleLine = true,
                    enabled = !outOfAttempts,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = ::send, enabled = flag.isNotBlank() && !busy && !outOfAttempts) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Submit flag")
                }
            }
        }
    }
}

@Composable
private fun ResultLine(result: AttemptResult) {
    val (text, color) = when (result.status) {
        "correct" -> "Correct!" to SolvedGreen
        "already_solved" -> "Already solved" to SolvedGreen
        "incorrect" -> "Incorrect" to MaterialTheme.colorScheme.error
        "partial" -> "Partly right: more flags are needed" to MaterialTheme.colorScheme.tertiary
        "ratelimited" -> "Too fast: wait a minute before trying again" to MaterialTheme.colorScheme.error
        "paused" -> "The CTF is paused" to MaterialTheme.colorScheme.error
        else -> result.status to MaterialTheme.colorScheme.onSurface
    }
    Column {
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        // CTFd's own message may say how many tries remain.
        if (result.message.isNotBlank() && !result.message.equals(text, ignoreCase = true)) {
            Text(result.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
