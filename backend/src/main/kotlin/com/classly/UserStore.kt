package com.classly

import com.google.cloud.Timestamp
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import java.time.Instant
import java.util.UUID

data class User(
    val id: String,
    val email: String,
    val name: String,
    val googleSub: String,
    val createdAt: Instant? = null,
)

interface UserRepository {
    /** Finds the account for a Google identity, creating it on first sign-in; refreshes email/name otherwise. */
    suspend fun findOrCreateByGoogle(googleSub: String, email: String, name: String): User
    suspend fun find(userId: String): User?
}

class FirestoreUserStore(firestore: Firestore) : UserRepository {
    private val collection = firestore.collection("users")

    override suspend fun findOrCreateByGoogle(googleSub: String, email: String, name: String): User {
        val existing = collection.whereEqualTo("googleSub", googleSub).limit(1).get().get().documents.firstOrNull()
        if (existing != null) {
            collection.document(existing.id).update(mapOf("email" to email, "name" to name)).get()
            return toUser(existing.id, existing.data).copy(email = email, name = name)
        }
        val id = UUID.randomUUID().toString()
        collection.document(id).set(
            mapOf("email" to email, "name" to name, "googleSub" to googleSub, "createdAt" to FieldValue.serverTimestamp()),
        ).get()
        return User(id, email, name, googleSub, createdAt = Instant.now())
    }

    override suspend fun find(userId: String): User? {
        val doc = collection.document(userId).get().get()
        return if (doc.exists()) toUser(userId, doc.data ?: emptyMap()) else null
    }

    private fun toUser(id: String, data: Map<String, Any?>) = User(
        id = id,
        email = data["email"] as? String ?: "",
        name = data["name"] as? String ?: "",
        googleSub = data["googleSub"] as? String ?: "",
        createdAt = (data["createdAt"] as? Timestamp)?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) },
    )
}
