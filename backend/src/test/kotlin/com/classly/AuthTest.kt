package com.classly

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {
    private val noGemini = object : GeminiClient {
        override suspend fun generate(prompt: String) = ""
    }

    @Test
    fun signedOutPagesRedirectToSplashAndApiIs401() = testApplication {
        application { testModule(noGemini) }
        val c = createClient { followRedirects = false }
        for (page in listOf("/", "/calendar.html", "/chat.html", "/schedule.html")) {
            val r = c.get(page)
            assertEquals(HttpStatusCode.Found, r.status, page)
            assertEquals("/welcome.html", r.headers[HttpHeaders.Location], page)
        }
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/chat").status)
        assertEquals(HttpStatusCode.Unauthorized, c.post("/api/hello-gemini").status)
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/me").status)
    }

    @Test
    fun splashHasGoogleSignInAndAssetsStayPublic() = testApplication {
        application { testModule(noGemini) }
        val c = createClient { followRedirects = false }
        val splash = c.get("/welcome.html")
        assertEquals(HttpStatusCode.OK, splash.status)
        assertTrue(splash.bodyAsText().contains("""href="/auth/google""""))
        assertEquals(HttpStatusCode.OK, c.get("/app.js").status)
    }

    @Test
    fun signedInSeesAppAndSplashSendsThemHome() = testApplication {
        application { testModule(noGemini) }
        val c = signedInClient(followRedirects = false)
        assertEquals(HttpStatusCode.OK, c.get("/").status)
        assertEquals(HttpStatusCode.OK, c.get("/calendar.html").status)
        val splash = c.get("/welcome.html")
        assertEquals(HttpStatusCode.Found, splash.status)
        assertEquals("/", splash.headers[HttpHeaders.Location])
        val me = c.get("/api/me")
        assertEquals(HttpStatusCode.OK, me.status)
        assertTrue(me.bodyAsText().contains("Sam Student"))
    }

    @Test
    fun logoutClearsSession() = testApplication {
        application { testModule(noGemini) }
        val c = signedInClient(followRedirects = false)
        val r = c.post("/logout")
        assertEquals("/welcome.html", r.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Unauthorized, c.get("/api/me").status)
    }

    @Test
    fun forgedSessionCookieIsRejected() = testApplication {
        application { testModule(noGemini) }
        val c = createClient { followRedirects = false }
        val r = c.get("/api/me") { header(HttpHeaders.Cookie, "__session=userId%3D%23su1%26email%3D%23sa%40b.c%26name%3D%23sMe") }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun googleSignInRedirectsToGoogle() = testApplication {
        application { testModule(noGemini) }
        val r = createClient { followRedirects = false }.get("/auth/google")
        assertEquals(HttpStatusCode.Found, r.status)
        assertTrue(r.headers[HttpHeaders.Location]!!.startsWith("https://accounts.google.com/o/oauth2/auth"))
    }

    @Test
    fun userStoreReusesAccountForSameGoogleIdentity() = kotlinx.coroutines.runBlocking {
        val store = FakeUserStore()
        val a = store.findOrCreateByGoogle("sub1", "a@x.com", "A")
        val b = store.findOrCreateByGoogle("sub1", "a2@x.com", "A2")
        assertEquals(a.id, b.id)
        assertEquals(1, store.users.size)
        assertEquals("a2@x.com", store.users[0].email)
    }
}
