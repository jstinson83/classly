package com.classly

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

// Named __session (not something friendlier) to match foodie: Firebase Hosting, if it's ever put in
// front of this service, forwards no other cookie to Cloud Run.
const val SESSION_COOKIE = "__session"
const val GOOGLE_OAUTH_PROVIDER_NAME = "auth-google"
const val SPLASH_PATH = "/welcome.html"

data class SessionData(val userId: String, val email: String, val name: String)

@Serializable
data class GoogleUserInfo(val sub: String, val email: String, val name: String = "")

@Serializable
data class MeResponse(val name: String, val email: String)

fun Application.installSessionCookie(sessionSecret: String) {
    install(Sessions) {
        cookie<SessionData>(SESSION_COOKIE) {
            cookie.path = "/"
            cookie.httpOnly = true
            // Cloud Run sets K_SERVICE on every revision; nothing in local dev does.
            cookie.secure = System.getenv("K_SERVICE") != null
            cookie.extensions["SameSite"] = "Lax"
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 30
            // Without this the cookie is plain URL-encoded text anyone could edit to claim any identity.
            transform(SessionTransportTransformerMessageAuthentication(sessionSecret.toByteArray()))
        }
    }
}

fun Application.installGoogleOAuth(oauthHttpClient: HttpClient, redirectBaseUrl: String) {
    install(Authentication) {
        oauth(GOOGLE_OAUTH_PROVIDER_NAME) {
            // Must match an Authorized redirect URI on the Google OAuth client.
            urlProvider = { "$redirectBaseUrl/auth/google/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://oauth2.googleapis.com/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID") ?: "",
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
                    defaultScopes = listOf("openid", "email", "profile"),
                )
            }
            client = oauthHttpClient
        }
    }
}

suspend fun fetchGoogleUserInfo(client: HttpClient, accessToken: String): GoogleUserInfo =
    client.get("https://www.googleapis.com/oauth2/v3/userinfo") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
    }.body()

private fun isPage(path: String) = path == "/" || path.endsWith(".html")

/**
 * Gate for the whole app: pages bounce to the splash screen and the `/api` routes answer 401 until signed in
 * (the AI endpoints cost money, so they must not be open). Static assets (js/css) and the `/auth` routes stay public.
 */
fun Application.installSignInGate() {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        val signedIn = call.sessions.get<SessionData>() != null
        when {
            path.startsWith("/api/") && !signedIn -> {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Sign in first."))
                finish()
            }
            path == SPLASH_PATH && signedIn -> {
                call.respondRedirect("/")
                finish()
            }
            path != SPLASH_PATH && isPage(path) && !signedIn -> {
                call.respondRedirect(SPLASH_PATH)
                finish()
            }
        }
    }
}

fun Route.authRoutes(oauthHttpClient: HttpClient, userStore: UserRepository) {
    authenticate(GOOGLE_OAUTH_PROVIDER_NAME) {
        // Ktor redirects to Google before this body runs.
        get("/auth/google") {}

        get("/auth/google/callback") {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
            if (principal == null) {
                call.respondRedirect(SPLASH_PATH)
                return@get
            }
            try {
                val info = fetchGoogleUserInfo(oauthHttpClient, principal.accessToken)
                val user = userStore.findOrCreateByGoogle(info.sub, info.email, info.name.ifBlank { info.email.substringBefore("@") })
                call.sessions.set(SessionData(user.id, user.email, user.name))
                call.respondRedirect("/")
            } catch (e: Exception) {
                call.application.environment.log.error("google sign-in failed", e)
                call.respondRedirect("$SPLASH_PATH?error=1")
            }
        }
    }

    get("/api/me") {
        val s = call.sessions.get<SessionData>()!! // the gate guarantees a session on API routes
        call.respond(MeResponse(s.name, s.email))
    }

    post("/logout") {
        call.sessions.clear<SessionData>()
        call.respondRedirect(SPLASH_PATH)
    }
}
