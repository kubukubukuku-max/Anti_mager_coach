package com.example.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.domain.DateTimeHelper
import com.example.model.DumbbellItem
import com.example.model.SessionSource
import com.example.model.SportType
import com.example.model.WorkoutSession
import com.example.ui.theme.AppThemeMode
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = AppThemeMode.WARM_CREAM.storageKey,
    val displayName: String? = null,
    val email: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DumbbellItemDoc(
    val exerciseName: String = "",
    val weightKg: Double = 0.0,
    val sets: Long = 0L,
    val reps: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class WorkoutSessionDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val externalId: String? = null,
    val sportType: String = SportType.WALKING.name,
    val source: String = SessionSource.MANUAL.name,
    val startedAtIso: String = "",
    val durationMin: Long = 1L,
    val distanceKm: Double? = null,
    val avgHr: Long? = null,
    val rpe: Long = 5L,
    val notes: String? = null,
    val dumbbellItems: List<DumbbellItemDoc> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Central repository for Firebase Authentication and Cloud Firestore CRUD operations
 * for [WorkoutSession] records and per-user synced preferences.
 *
 * Always targets the provisioned named Firestore database configured in
 * `R.string.firestore_database_id` under `/users/{userId}` and `/users/{userId}/workouts/{workoutId}`.
 */
class FirebaseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // --- FIREBASE AUTHENTICATION ---

    val currentUser: FirebaseUser?
        get() = try {
            auth.currentUser
        } catch (_: Throwable) {
            null
        }

    /**
     * Reactive stream emitting authentication state changes whenever the user signs in or out.
     */
    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        try {
            auth.addAuthStateListener(listener)
        } catch (_: Throwable) {
            trySend(null)
        }
        awaitClose {
            try {
                auth.removeAuthStateListener(listener)
            } catch (_: Throwable) {
                // Ignore cleanup failure
            }
        }
    }

    /**
     * Exchanges a Google Sign-In ID token (obtained via Jetpack Credential Manager)
     * for an authenticated Firebase Auth session.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            require(idToken.isNotBlank()) { "Google ID token must not be blank" }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
                ?: throw IllegalStateException("Firebase Auth did not return a user session.")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (_: Throwable) {
            // Safe no-op if FirebaseAuth is unavailable
        }
    }

    private fun requireMatchingUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        check(uid == expectedUserId && uid.isNotBlank()) {
            "Sesi pengguna berubah; operasi lintas akun dibatalkan."
        }
        return uid
    }

    private fun userDocRef(userId: String) = db.collection(COLLECTION_USERS).document(userId)

    private fun workoutsCollectionRef(userId: String) =
        userDocRef(userId).collection(COLLECTION_WORKOUTS)

    // --- WORKOUT SESSION CRUD ---

    /**
     * READ (Real-time stream): Observes all [WorkoutSession] records for [userId] in real time.
     * Uses [DocumentSnapshot.ServerTimestampBehavior.ESTIMATE] for pending local writes.
     */
    fun observeWorkoutSessions(userId: String): Flow<List<WorkoutSession>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS"
        emitAll(
            workoutsCollectionRef(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot
                        .toObjects(
                            WorkoutSessionDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        .map { it.toDomain() }
                        .sortedByDescending { session ->
                            DateTimeHelper.parseIsoToMillis(session.startedAtIso) ?: 0L
                        }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * READ (Single): Fetches a single [WorkoutSession] document by ID.
     */
    suspend fun getWorkoutSession(userId: String, sessionId: String): Result<WorkoutSession?> {
        val docPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS/$sessionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(sessionId)) { "Invalid workout document ID" }
            val snapshot = workoutsCollectionRef(uid).document(sessionId).get().await()
            if (!snapshot.exists()) {
                Result.success(null)
            } else {
                val doc = snapshot.toObject(
                    WorkoutSessionDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                Result.success(doc?.toDomain())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    /**
     * CREATE: Writes a new [WorkoutSession] document under `/users/{userId}/workouts/{workoutId}`.
     */
    suspend fun createWorkoutSession(userId: String, session: WorkoutSession): Result<Unit> {
        val docId = sanitizeDocumentId(session.id)
        val docPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS/$docId"
        return try {
            val uid = requireMatchingUserId(userId)
            val docRef = workoutsCollectionRef(uid).document(docId)
            val payload = session.toCreateMap(uid)
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * UPDATE: Partially updates mutable fields of an existing [WorkoutSession],
     * preserving immutable `userId` and `createdAt` fields to satisfy security rules.
     */
    suspend fun updateWorkoutSession(userId: String, session: WorkoutSession): Result<Unit> {
        val docId = session.id.trim()
        val docPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS/$docId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(docId)) { "Invalid workout document ID for update" }
            val docRef = workoutsCollectionRef(uid).document(docId)
            val payload = session.toUpdateMap()
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * DELETE: Removes a single [WorkoutSession] document by ID.
     */
    suspend fun deleteWorkoutSession(userId: String, sessionId: String): Result<Unit> {
        val docPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS/$sessionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(sessionId)) { "Invalid workout document ID" }
            workoutsCollectionRef(uid).document(sessionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    /**
     * DELETE (Batch): Clears all [WorkoutSession] documents for the signed-in user.
     */
    suspend fun clearAllWorkoutSessions(userId: String): Result<Unit> {
        val collectionPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS"
        return try {
            val uid = requireMatchingUserId(userId)
            val snapshot = workoutsCollectionRef(uid).get().await()
            val docs = snapshot.documents
            for (chunk in docs.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, collectionPath)
            Result.failure(e)
        }
    }

    /**
     * BATCH UPSERT: Inserts newly synced/imported sessions and updates existing ones matched by
     * `externalId` or `id`, preserving immutable `createdAt` and `userId`.
     */
    suspend fun upsertSyncedSessions(
        userId: String,
        incomingSessions: List<WorkoutSession>,
        existingSessions: List<WorkoutSession>
    ): Result<Int> {
        if (incomingSessions.isEmpty()) return Result.success(0)
        val collectionPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS"
        return try {
            val uid = requireMatchingUserId(userId)
            val colRef = workoutsCollectionRef(uid)

            val existingById = existingSessions.associateBy { it.id }.toMutableMap()
            val existingByExternalId = existingSessions
                .mapNotNull { session ->
                    session.externalId?.takeIf { it.isNotBlank() }?.let { it to session }
                }
                .toMap()
                .toMutableMap()

            val operations = mutableListOf<Pair<Boolean, Pair<String, Map<String, Any>>>>()
            var newInsertedCount = 0

            for (incoming in incomingSessions) {
                val extId = incoming.externalId?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
                val matchedExisting = (extId?.let { existingByExternalId[it] })
                    ?: existingById[incoming.id]

                if (matchedExisting != null && isValidDocumentId(matchedExisting.id)) {
                    val updateMap = buildMap<String, Any> {
                        put("durationMin", incoming.durationMin.coerceIn(1, 1440))
                        val mergedDistance = (incoming.distanceKm ?: matchedExisting.distanceKm)
                            ?.takeIf { it > 0.0 && it <= 500.0 }
                        if (mergedDistance != null) put("distanceKm", mergedDistance)
                        val mergedHr = (incoming.avgHr ?: matchedExisting.avgHr)
                            ?.takeIf { it in 30..240 }
                        if (mergedHr != null) put("avgHr", mergedHr)
                        val mergedNotes = (matchedExisting.notes ?: incoming.notes)
                            ?.trim()
                            ?.take(500)
                            ?.takeIf { it.isNotEmpty() }
                        if (mergedNotes != null) put("notes", mergedNotes)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    operations.add(false to (matchedExisting.id to updateMap))
                } else {
                    val docId = sanitizeDocumentId(incoming.id)
                    val createMap = incoming.toCreateMap(uid)
                    operations.add(true to (docId to createMap))
                    val stored = incoming.copy(id = docId)
                    existingById[docId] = stored
                    if (extId != null) existingByExternalId[extId] = stored
                    newInsertedCount++
                }
            }

            for (chunk in operations.chunked(MAX_BATCH_OPS)) {
                val batch = db.batch()
                for ((isCreate, docPair) in chunk) {
                    val (docId, payload) = docPair
                    val docRef = colRef.document(docId)
                    if (isCreate) {
                        batch.set(docRef, payload)
                    } else {
                        batch.update(docRef, payload)
                    }
                }
                batch.commit().await()
            }

            Result.success(newInsertedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, collectionPath)
            Result.failure(e)
        }
    }

    /**
     * Forces a fresh read from the Firestore server to verify connectivity and pull latest changes.
     */
    suspend fun forceRefreshFromServer(userId: String): Result<Int> {
        val collectionPath = "$COLLECTION_USERS/$userId/$COLLECTION_WORKOUTS"
        return try {
            val uid = requireMatchingUserId(userId)
            val snapshot = workoutsCollectionRef(uid).get(Source.SERVER).await()
            Result.success(snapshot.size())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, collectionPath)
            Result.failure(e)
        }
    }

    // --- USER PROFILE & SYNCED THEME PREFERENCE ---

    fun observeUserThemeMode(userId: String): Flow<AppThemeMode?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "$COLLECTION_USERS/$userId"
        emitAll(
            userDocRef(userId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) return@map null
                    val doc = snapshot.toObject(
                        UserProfileDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    doc?.themeMode?.let { AppThemeMode.fromStorageKey(it) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String?,
        email: String?,
        fallbackThemeMode: AppThemeMode
    ): Result<Unit> {
        val docPath = "$COLLECTION_USERS/$userId"
        return try {
            val uid = requireMatchingUserId(userId)
            val docRef = userDocRef(uid)
            val snapshot = docRef.get().await()
            val cleanName = displayName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
            val cleanEmail = email?.trim()?.take(254)?.takeIf { it.isNotEmpty() }

            if (!snapshot.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("themeMode", fallbackThemeMode.storageKey)
                    if (cleanName != null) put("displayName", cleanName)
                    if (cleanEmail != null) put("email", cleanEmail)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val existingName = snapshot.getString("displayName")
                val existingEmail = snapshot.getString("email")
                if (cleanName != existingName || cleanEmail != existingEmail) {
                    val updatePayload = buildMap<String, Any> {
                        if (cleanName != null) put("displayName", cleanName)
                        if (cleanEmail != null) put("email", cleanEmail)
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    docRef.update(updatePayload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateThemeMode(
        userId: String,
        mode: AppThemeMode,
        displayName: String?,
        email: String?
    ): Result<Unit> {
        val docPath = "$COLLECTION_USERS/$userId"
        return try {
            val uid = requireMatchingUserId(userId)
            val docRef = userDocRef(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "themeMode" to mode.storageKey,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val cleanName = displayName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }
                val cleanEmail = email?.trim()?.take(254)?.takeIf { it.isNotEmpty() }
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("themeMode", mode.storageKey)
                    if (cleanName != null) put("displayName", cleanName)
                    if (cleanEmail != null) put("email", cleanEmail)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- SERIALIZATION & VALIDATION HELPERS ---

    private fun WorkoutSession.toCreateMap(uid: String): Map<String, Any> {
        val safeStartedIso = startedAtIso.trim().take(40).let {
            if (it.length >= 10) it else DateTimeHelper.formatNowIso()
        }
        val safeDuration = durationMin.coerceIn(1, 1440)
        val safeRpe = rpe.coerceIn(1, 10)
        val safeExternalId = externalId?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
        val safeDistance = distanceKm?.takeIf { it > 0.0 && it <= 500.0 }
        val safeAvgHr = avgHr?.takeIf { it in 30..240 }
        val safeNotes = notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
        val safeItems = dumbbellItems.toFirestoreItemMaps()

        // Never include "id" key in body because WorkoutSessionDoc uses @DocumentId
        return mapOf(
            "userId" to uid,
            "externalId" to safeExternalId,
            "sportType" to sportType.name,
            "source" to source.name,
            "startedAtIso" to safeStartedIso,
            "durationMin" to safeDuration,
            "distanceKm" to safeDistance,
            "avgHr" to safeAvgHr,
            "rpe" to safeRpe,
            "notes" to safeNotes,
            "dumbbellItems" to safeItems,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }.mapValues { it.value as Any }
    }

    private fun WorkoutSession.toUpdateMap(): Map<String, Any> {
        val safeStartedIso = startedAtIso.trim().take(40).let {
            if (it.length >= 10) it else DateTimeHelper.formatNowIso()
        }
        val safeDuration = durationMin.coerceIn(1, 1440)
        val safeRpe = rpe.coerceIn(1, 10)
        val safeDistance = distanceKm?.takeIf { it > 0.0 && it <= 500.0 }
        val safeAvgHr = avgHr?.takeIf { it in 30..240 }
        val safeNotes = notes?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
        val safeItems = if (sportType == SportType.DUMBBELL) {
            dumbbellItems.toFirestoreItemMaps()
        } else {
            emptyList()
        }

        // Matches firestore.rules allowlisted mutable keys while preserving userId & createdAt
        return mapOf(
            "sportType" to sportType.name,
            "startedAtIso" to safeStartedIso,
            "durationMin" to safeDuration,
            "distanceKm" to (safeDistance ?: FieldValue.delete()),
            "avgHr" to (safeAvgHr ?: FieldValue.delete()),
            "rpe" to safeRpe,
            "notes" to (safeNotes ?: FieldValue.delete()),
            "dumbbellItems" to safeItems,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun List<DumbbellItem>.toFirestoreItemMaps(): List<Map<String, Any>> {
        return take(30).map { item ->
            mapOf(
                "exerciseName" to item.exerciseName.trim().take(100),
                "weightKg" to item.weightKg.coerceIn(0.0, 500.0),
                "sets" to item.sets.coerceIn(1, 50),
                "reps" to item.reps.coerceIn(1, 500)
            )
        }
    }

    private fun WorkoutSessionDoc.toDomain(): WorkoutSession {
        val resolvedSport = SportType.entries.firstOrNull { it.name == sportType }
            ?: SportType.WALKING
        val resolvedSource = SessionSource.entries.firstOrNull { it.name == source }
            ?: SessionSource.MANUAL
        val fallbackIso = startedAtIso.takeIf { it.length >= 10 }
            ?: (createdAt ?: Timestamp.now()).toDate().toInstant().toString()

        return WorkoutSession(
            id = id.ifBlank { UUID.randomUUID().toString() },
            externalId = externalId?.takeIf { it.isNotBlank() },
            sportType = resolvedSport,
            source = resolvedSource,
            startedAtIso = fallbackIso,
            durationMin = durationMin.toInt().coerceIn(1, 1440),
            distanceKm = distanceKm?.takeIf { it > 0.0 },
            avgHr = avgHr?.toInt()?.takeIf { it in 30..240 },
            rpe = rpe.toInt().coerceIn(1, 10),
            notes = notes?.takeIf { it.isNotBlank() },
            dumbbellItems = dumbbellItems.map { item ->
                DumbbellItem(
                    exerciseName = item.exerciseName,
                    weightKg = item.weightKg,
                    sets = item.sets.toInt().coerceAtLeast(1),
                    reps = item.reps.toInt().coerceAtLeast(1)
                )
            }
        )
    }

    private fun isValidDocumentId(id: String): Boolean {
        return id.length in 1..128 && DOC_ID_REGEX.matches(id)
    }

    private fun sanitizeDocumentId(rawId: String): String {
        val trimmed = rawId.trim()
        return if (isValidDocumentId(trimmed)) trimmed else UUID.randomUUID().toString()
    }

    companion object {
        private const val TAG = "FirebaseRepository"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_WORKOUTS = "workouts"
        private const val MAX_BATCH_OPS = 450
        private val DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

        /**
         * Safely checks whether a default [FirebaseApp] is configured (i.e. `google-services.json`
         * was present at build time) without crashing when Firebase is unconfigured.
         */
        fun isFirebaseAvailable(context: Context): Boolean {
            return try {
                val appContext = context.applicationContext
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    true
                } else {
                    FirebaseApp.initializeApp(appContext) != null
                }
            } catch (e: Throwable) {
                Log.w(TAG, "FirebaseApp is not configured on this build: ${e.message}")
                false
            }
        }

        /**
         * Creates a [FirebaseRepository] only if Firebase is initialized and valid.
         * Returns `null` cleanly when `google-services.json` is absent so the app launches in Local Mode.
         */
        fun createOrNull(context: Context): FirebaseRepository? {
            val appContext = context.applicationContext
            if (!isFirebaseAvailable(appContext)) return null
            return try {
                val dbIdRes = appContext.resources.getIdentifier(
                    "firestore_database_id",
                    "string",
                    appContext.packageName
                )
                val databaseId = if (dbIdRes > 0) appContext.getString(dbIdRes).trim() else ""
                val firestore = if (databaseId.isNotEmpty()) {
                    FirebaseFirestore.getInstance(databaseId)
                } else {
                    FirebaseFirestore.getInstance()
                }
                val firebaseAuth = FirebaseAuth.getInstance()
                FirebaseRepository(db = firestore, auth = firebaseAuth)
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to initialize Firebase services; falling back to local mode.", e)
                null
            }
        }
    }
}
