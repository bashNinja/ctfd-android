package io.github.miketweaver.ctfd.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CtfdApiTest {
    private val server = MockWebServer()
    private lateinit var api: CtfdApi

    @Before fun setUp() {
        server.start()
        api = CtfdApi(server.url("/"), "ctfd_secret")
    }

    @After fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()
    )

    @Test fun normalizesUrlsLikeTheCli() {
        assertEquals("https://ctf.example.org/", CtfdApi.normalizeUrl("ctf.example.org/").toString())
        assertEquals("http://10.0.2.2:8000/", CtfdApi.normalizeUrl("http://10.0.2.2:8000").toString())
        assertThrows(CtfdException::class.java) { CtfdApi.normalizeUrl("http://ctf.example.org") }
        assertThrows(CtfdException::class.java) { CtfdApi.normalizeUrl("  ") }
    }

    @Test fun namesCtfsAfterTheTellingHostLabel() {
        assertEquals("hackerschallenge", CtfdApi.defaultName(CtfdApi.normalizeUrl("ctf.hackerschallenge.org")))
        assertEquals("forensicsfiles101", CtfdApi.defaultName(CtfdApi.normalizeUrl("forensicsfiles101.ctfd.io")))
        assertEquals("demo", CtfdApi.defaultName(CtfdApi.normalizeUrl("demo.ctfd.io")))
    }

    @Test fun statusInUserModeSkipsTheTeamCall() = runBlocking {
        json("""{"success":true,"data":{"id":5,"name":"pat","team_id":null,"score":0,"place":null,"extra":1}}""")
        val status = api.status()
        assertEquals("pat", status.name)
        assertNull(status.place)
        assertEquals("user", status.accountType)
        val request = server.takeRequest()
        assertEquals("/api/v1/users/me", request.url.encodedPath)
        assertEquals("Token ctfd_secret", request.headers["Authorization"])
        assertEquals(1, server.requestCount)
    }

    @Test fun statusInTeamModeUsesTheTeam() = runBlocking {
        json("""{"success":true,"data":{"id":44,"name":"pat","team_id":33,"score":100,"place":"9th"}}""")
        json("""{"success":true,"data":{"id":33,"name":"Pat's Team","score":750,"place":"3rd","members":[44]}}""")
        val status = api.status()
        assertEquals("Pat's Team", status.name)
        assertEquals(750, status.score)
        assertEquals("3rd", status.place)
        assertEquals("team", status.accountType)
    }

    @Test fun parsesChallengeDetail() = runBlocking {
        json(
            """{"success":true,"data":{"id":12,"name":"One Trick Pony","value":50,"category":"Crypto Descent",
            "description":"Read **it**","connection_info":null,"solves":94,"solved_by_me":false,"attempts":1,
            "max_attempts":3,"files":["/files/ab/handout.zip?token=x"],"hints":[{"id":1,"cost":10},
            {"id":2,"cost":0,"content":"look closer"}],"tags":[],"type":"standard","view":"<div/>","next_id":13}}"""
        )
        val c = api.challenge(12)
        assertEquals("One Trick Pony", c.name)
        assertFalse(c.solved)
        assertEquals(3, c.maxAttempts)
        assertEquals(listOf("/files/ab/handout.zip?token=x"), c.files)
        assertNull(c.hints[0].content)
        assertEquals("look closer", c.hints[1].content)
        assertEquals(13, c.nextId)
        assertEquals(server.url("/files/ab/handout.zip?token=x"), api.fileUrl(c.files[0]))
    }

    @Test fun rateLimitedAttemptIsAResultNotAnError() = runBlocking {
        json("""{"success":true,"data":{"status":"ratelimited","message":"You're submitting flags too fast. Slow down."}}""", 429)
        val result = api.attempt(12, "HC{nope}")
        assertEquals("ratelimited", result.status)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"challenge_id\":12") && body.contains("\"submission\":\"HC{nope}\""))
    }

    @Test fun loginPageMeansBadToken() {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/html").body("<html>login</html>").build())
        val e = assertThrows(CtfdException::class.java) { runBlocking { api.me() } }
        assertTrue(e.message!!.contains("login page"))
    }

    @Test fun forbiddenSaysWhy() {
        json("""{"message":"You don't have the permission to access the requested resource."}""", 403)
        val e = assertThrows(CtfdException::class.java) { runBlocking { api.challenges() } }
        assertTrue(e.message!!.contains("permission"))
    }

    @Test fun imagesOnTheCtfHostGetTheTokenOthersDont() = runBlocking {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body("png").build())
        val (bytes, type) = api.fetch(server.url("/files/ab/card.png"))
        assertEquals("png", String(bytes))
        assertEquals("image/png", type)
        assertEquals("Token ctfd_secret", server.takeRequest().headers["Authorization"])

        val other = MockWebServer().apply { start() }
        other.enqueue(MockResponse.Builder().body("x").build())
        // Same machine, different port: a different origin, so no token.
        api.fetch(other.url("/elsewhere.png"))
        assertNull(other.takeRequest().headers["Authorization"])
        other.close()
    }
}
