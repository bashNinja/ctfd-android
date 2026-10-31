package io.github.miketweaver.ctfd.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** CTFd wraps every API answer as {"success": true, "data": ...}. */
@Serializable
data class Envelope<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null,
    val errors: JsonElement? = null,
)

@Serializable
data class Me(
    val id: Int,
    val name: String,
    @SerialName("team_id") val teamId: Int? = null,
    val score: Int? = null,
    val place: String? = null,
)

@Serializable
data class TeamMe(
    val id: Int,
    val name: String,
    val score: Int? = null,
    val place: String? = null,
    @SerialName("captain_id") val captainId: Int? = null,
    val members: List<Int> = emptyList(),
)

@Serializable
data class ChallengeSummary(
    val id: Int,
    val name: String,
    val category: String = "",
    val value: Int = 0,
    val solves: Int? = null,
    @SerialName("solved_by_me") val solved: Boolean = false,
    val type: String = "standard",
    val tags: List<Tag> = emptyList(),
)

@Serializable
data class Tag(val value: String = "")

@Serializable
data class Hint(
    val id: Int,
    val cost: Int = 0,
    val title: String? = null,
    val content: String? = null,
)

@Serializable
data class ChallengeDetail(
    val id: Int,
    val name: String,
    val category: String = "",
    val value: Int = 0,
    val description: String? = null,
    @SerialName("connection_info") val connectionInfo: String? = null,
    val attribution: String? = null,
    val solves: Int? = null,
    @SerialName("solved_by_me") val solved: Boolean = false,
    val attempts: Int = 0,
    @SerialName("max_attempts") val maxAttempts: Int = 0,
    /** Relative URLs such as /files/ab12/evidence.zip?token=... */
    val files: List<String> = emptyList(),
    val hints: List<Hint> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val type: String = "standard",
    @SerialName("next_id") val nextId: Int? = null,
)

@Serializable
data class AttemptRequest(
    @SerialName("challenge_id") val challengeId: Int,
    val submission: String,
)

@Serializable
data class AttemptResult(
    val status: String,
    val message: String = "",
)

@Serializable
data class UnlockRequest(val target: Int, val type: String = "hints")

@Serializable
data class Member(val id: Int, val name: String, val score: Int? = null)

@Serializable
data class Standing(
    val pos: Int,
    @SerialName("account_id") val accountId: Int,
    @SerialName("account_type") val accountType: String = "user",
    val name: String,
    val score: Int = 0,
    val members: List<Member> = emptyList(),
)

@Serializable
data class SolveChallenge(
    val id: Int,
    val name: String,
    val category: String = "",
    val value: Int = 0,
)

@Serializable
data class Solve(
    @SerialName("challenge_id") val challengeId: Int,
    val challenge: SolveChallenge? = null,
    val date: String = "",
    val user: SolveUser? = null,
)

@Serializable
data class SolveUser(val id: Int, val name: String)

/** Where the person stands, the same answer as `ctfd status`. */
data class Status(
    val me: Me,
    val team: TeamMe?,
) {
    val name: String get() = team?.name ?: me.name
    val score: Int get() = team?.score ?: me.score ?: 0
    val place: String? get() = team?.place ?: me.place
    val accountId: Int get() = team?.id ?: me.id
    val accountType: String get() = if (team != null) "team" else "user"
}
