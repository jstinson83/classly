package com.classly

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*

class FakeUserStore : UserRepository {
    val users = mutableListOf<User>()

    override suspend fun findOrCreateByGoogle(googleSub: String, email: String, name: String): User {
        val existing = users.indexOfFirst { it.googleSub == googleSub }
        val user = if (existing >= 0) users[existing].copy(email = email, name = name) else User("u${users.size + 1}", email, name, googleSub)
        if (existing >= 0) users[existing] = user else users += user
        return user
    }

    override suspend fun find(userId: String) = users.firstOrNull { it.id == userId }
}

/** The app with a fake user store and a `/test-login` route that starts a session (Google's OAuth round trip isn't testable here). */
fun Application.testModule(gemini: GeminiClient, userStore: UserRepository = FakeUserStore()) {
    module(gemini = gemini, userStore = userStore)
    routing {
        get("/test-login") {
            call.sessions.set(SessionData("u1", "student@example.com", "Sam Student"))
            call.respondText("ok")
        }
    }
}

/** A client that's already signed in. */
suspend fun ApplicationTestBuilder.signedInClient(followRedirects: Boolean = true): HttpClient {
    val c = createClient {
        install(HttpCookies)
        this.followRedirects = followRedirects
    }
    c.get("/test-login")
    return c
}
