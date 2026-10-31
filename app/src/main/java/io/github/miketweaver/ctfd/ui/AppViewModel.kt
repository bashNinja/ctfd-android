package io.github.miketweaver.ctfd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.miketweaver.ctfd.data.AccountStore
import io.github.miketweaver.ctfd.data.ChallengeSummary
import io.github.miketweaver.ctfd.data.CtfdApi
import io.github.miketweaver.ctfd.data.CtfdException
import io.github.miketweaver.ctfd.data.Standing
import io.github.miketweaver.ctfd.data.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoardState(
    val ctfName: String? = null,
    val status: Status? = null,
    val challenges: List<ChallengeSummary> = emptyList(),
    val scoreboard: List<Standing> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = AccountStore(app)

    private val _board = MutableStateFlow(BoardState())
    val board: StateFlow<BoardState> = _board.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun api(): CtfdApi? = store.activeCtf()?.api(store)

    /** Reloads status, challenges and the scoreboard for the CTF in use. */
    fun refresh() {
        val ctf = store.activeCtf()
        refreshJob?.cancel()
        if (ctf == null) {
            _board.value = BoardState()
            return
        }
        if (_board.value.ctfName != ctf.name) _board.value = BoardState(ctfName = ctf.name)
        _board.update { it.copy(loading = true, error = null) }
        refreshJob = viewModelScope.launch {
            val api = ctf.api(store)
            try {
                val status = async { api.status() }
                val challenges = async { api.challenges() }
                val scoreboard = async { runCatching { api.scoreboard() }.getOrDefault(emptyList()) }
                _board.update {
                    it.copy(
                        status = status.await(),
                        challenges = challenges.await(),
                        scoreboard = scoreboard.await(),
                        loading = false,
                        loaded = true,
                    )
                }
            } catch (e: CtfdException) {
                _board.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun select(name: String) {
        store.select(name)
        refresh()
    }

    fun remove(name: String) {
        store.remove(name)
        refresh()
    }

    /** Checks the token with the instance before saving it, as `ctfd add` does. */
    suspend fun add(urlText: String, nameText: String, token: String): String? {
        return try {
            val url = CtfdApi.normalizeUrl(urlText)
            if (token.isBlank()) return "Paste an access token"
            CtfdApi(url, token.trim()).me()
            val name = nameText.trim().ifEmpty { CtfdApi.defaultName(url) }
            store.add(name, url.toString().trimEnd('/'), token.trim())
            refresh()
            null
        } catch (e: CtfdException) {
            e.message ?: "That didn't work"
        }
    }

    /** Marks a challenge solved locally so the board updates before the next refresh. */
    fun markSolved(id: Int) {
        _board.update { b -> b.copy(challenges = b.challenges.map { if (it.id == id) it.copy(solved = true) else it }) }
        refresh()
    }
}
