package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.data.model.DiaryEntry
import com.example.data.model.TaskItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CloudSyncManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val _syncState = MutableStateFlow(CloudSyncState())
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    private val knownRemoteTaskIds = ConcurrentHashMap.newKeySet<String>()
    private val knownRemoteNoteIds = ConcurrentHashMap.newKeySet<String>()
    private val knownRemoteDiaryIds = ConcurrentHashMap.newKeySet<String>()

    private val activeOperations = AtomicInteger(0)

    @Volatile
    private var hasPendingTaskWrites = false

    @Volatile
    private var hasPendingNoteWrites = false

    @Volatile
    private var hasPendingDiaryWrites = false

    @Volatile
    private var initialBootstrapActive = false

    @Volatile
    private var lastSyncedAtMillis: Long? = null

    @Volatile
    private var lastErrorMessage: String? = null

    private fun userTasksCollection(uid: String) =
        db.collection("users").document(uid).collection("tasks")

    private fun userDailyNotesCollection(uid: String) =
        db.collection("users").document(uid).collection("daily_notes")

    private fun userDiaryCollection(uid: String) =
        db.collection("users").document(uid).collection("diary_entries")

    fun resetStateOnSignOut() {
        knownRemoteTaskIds.clear()
        knownRemoteNoteIds.clear()
        knownRemoteDiaryIds.clear()
        activeOperations.set(0)
        hasPendingTaskWrites = false
        hasPendingNoteWrites = false
        hasPendingDiaryWrites = false
        initialBootstrapActive = false
        lastErrorMessage = null
        _syncState.value = CloudSyncState(
            status = CloudSyncStatus.SYNCING,
            lastSyncedAtMillis = null,
            errorMessage = null,
        )
    }

    fun markSyncStarted() {
        initialBootstrapActive = true
        lastErrorMessage = null
        refreshSyncState()
    }

    private fun beginWriteOperation() {
        activeOperations.incrementAndGet()
        lastErrorMessage = null
        refreshSyncState()
    }

    private fun endWriteOperation(error: Throwable? = null) {
        activeOperations.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (error == null) {
            lastErrorMessage = null
            lastSyncedAtMillis = System.currentTimeMillis()
        } else {
            lastErrorMessage = friendlyErrorMessage(error)
        }
        refreshSyncState()
    }

    private fun refreshSyncState() {
        val pending = hasPendingTaskWrites || hasPendingNoteWrites || hasPendingDiaryWrites
        val inProgress = activeOperations.get() > 0 || initialBootstrapActive || pending
        val status = when {
            inProgress -> CloudSyncStatus.SYNCING
            lastErrorMessage != null -> CloudSyncStatus.ERROR
            else -> CloudSyncStatus.SYNCED
        }
        _syncState.value = CloudSyncState(
            status = status,
            lastSyncedAtMillis = lastSyncedAtMillis,
            errorMessage = lastErrorMessage,
        )
    }

    private fun friendlyErrorMessage(error: Throwable): String {
        val fsError = extractFirestoreException(error)
        return when (fsError?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Sin conexión a internet. Los cambios se sincronizarán al reconectar."
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permisos insuficientes en la nube. Verifica tu sesión de Google."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Cuota diaria de Firebase alcanzada temporalmente."
            else -> error.localizedMessage ?: "Error al sincronizar con la nube"
        }
    }

    fun observeTasks(uid: String): Flow<List<TaskItem>> =
        userTasksCollection(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                knownRemoteTaskIds.clear()
                knownRemoteTaskIds.addAll(snapshot.documents.map { it.id })
                hasPendingTaskWrites = snapshot.metadata.hasPendingWrites()
                if (!snapshot.metadata.isFromCache && !hasPendingTaskWrites) {
                    lastSyncedAtMillis = System.currentTimeMillis()
                    lastErrorMessage = null
                }
                refreshSyncState()
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDomain()
                }
            }
            .catch { error ->
                val fsError = extractFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, "users/$uid/tasks")
                lastErrorMessage = friendlyErrorMessage(error)
                refreshSyncState()
            }

    fun observeDailyNotes(uid: String): Flow<Map<String, String>> =
        userDailyNotesCollection(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                knownRemoteNoteIds.clear()
                knownRemoteNoteIds.addAll(snapshot.documents.map { it.id })
                hasPendingNoteWrites = snapshot.metadata.hasPendingWrites()
                if (!snapshot.metadata.isFromCache && !hasPendingNoteWrites) {
                    lastSyncedAtMillis = System.currentTimeMillis()
                    lastErrorMessage = null
                }
                refreshSyncState()
                buildMap {
                    for (doc in snapshot.documents) {
                        val note = doc.toObject(
                            CloudDailyNote::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (note != null && note.fecha.isNotBlank() && note.texto.isNotBlank()) {
                            put(note.fecha, note.texto)
                        }
                    }
                }
            }
            .catch { error ->
                val fsError = extractFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, "users/$uid/daily_notes")
                lastErrorMessage = friendlyErrorMessage(error)
                refreshSyncState()
            }

    fun observeDiaryEntries(uid: String): Flow<List<DiaryEntry>> =
        userDiaryCollection(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                knownRemoteDiaryIds.clear()
                knownRemoteDiaryIds.addAll(snapshot.documents.map { it.id })
                hasPendingDiaryWrites = snapshot.metadata.hasPendingWrites()
                if (!snapshot.metadata.isFromCache && !hasPendingDiaryWrites) {
                    lastSyncedAtMillis = System.currentTimeMillis()
                    lastErrorMessage = null
                }
                refreshSyncState()
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudDiaryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDomain()
                }
            }
            .catch { error ->
                val fsError = extractFirestoreException(error)
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, "users/$uid/diary_entries")
                lastErrorMessage = friendlyErrorMessage(error)
                refreshSyncState()
            }

    suspend fun bootstrapAndMergeOnSignIn(
        uid: String,
        localTasks: List<TaskItem>,
        localNotes: Map<String, String>,
        localDiary: List<DiaryEntry>,
        canUploadExistingLocalData: Boolean,
    ): Result<Unit> {
        markSyncStarted()
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Usuario no autenticado")
            check(currentAuthUid == uid) { "La cuenta activa no coincide con el sincronizador" }

            val remoteTasksSnap = userTasksCollection(uid).get().await()
            val remoteNotesSnap = userDailyNotesCollection(uid).get().await()
            val remoteDiarySnap = userDiaryCollection(uid).get().await()

            knownRemoteTaskIds.clear()
            knownRemoteTaskIds.addAll(remoteTasksSnap.documents.map { it.id })
            knownRemoteNoteIds.clear()
            knownRemoteNoteIds.addAll(remoteNotesSnap.documents.map { it.id })
            knownRemoteDiaryIds.clear()
            knownRemoteDiaryIds.addAll(remoteDiarySnap.documents.map { it.id })

            val remoteHasAnyData = remoteTasksSnap.documents.isNotEmpty() ||
                remoteNotesSnap.documents.isNotEmpty() ||
                remoteDiarySnap.documents.isNotEmpty()

            if (canUploadExistingLocalData) {
                val tasksToUpload = localTasks.filter { task ->
                    val safeId = sanitizeFirestoreDocId(task.id)
                    val isSampleTask = task.id.startsWith("ej-") || task.titulo.startsWith("[Ejemplo]")
                    !knownRemoteTaskIds.contains(safeId) && (!remoteHasAnyData || !isSampleTask)
                }
                val notesToUpload = localNotes.filter { (fecha, texto) ->
                    val safeId = sanitizeFirestoreDocId(fecha)
                    texto.isNotBlank() && !knownRemoteNoteIds.contains(safeId)
                }
                val diaryToUpload = localDiary.filter { entry ->
                    val safeId = sanitizeFirestoreDocId(entry.id)
                    val isSampleDiary = entry.id.startsWith("d-") &&
                        entry.texto.contains("Inicio de jornada: revisión de tareas diarias")
                    !knownRemoteDiaryIds.contains(safeId) && (!remoteHasAnyData || !isSampleDiary)
                }

                uploadBatchCreates(uid, tasksToUpload, notesToUpload, diaryToUpload)
            }

            initialBootstrapActive = false
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            initialBootstrapActive = false
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            initialBootstrapActive = false
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    private suspend fun uploadBatchCreates(
        uid: String,
        tasks: List<TaskItem>,
        notes: Map<String, String>,
        diaryEntries: List<DiaryEntry>,
    ) {
        val operations = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
        tasks.forEach { task ->
            val safeId = sanitizeFirestoreDocId(task.id)
            operations.add(userTasksCollection(uid).document(safeId) to task.toCloudCreateMap(uid))
        }
        notes.forEach { (fecha, texto) ->
            val safeId = sanitizeFirestoreDocId(fecha)
            operations.add(
                userDailyNotesCollection(uid).document(safeId) to dailyNoteCreateMap(uid, fecha, texto)
            )
        }
        diaryEntries.forEach { entry ->
            val safeId = sanitizeFirestoreDocId(entry.id)
            operations.add(userDiaryCollection(uid).document(safeId) to entry.toCloudCreateMap(uid))
        }

        for (chunk in operations.chunked(400)) {
            val batch = db.batch()
            chunk.forEach { (ref, map) -> batch.set(ref, map) }
            batch.commit().await()
            chunk.forEach { (ref, _) ->
                when (ref.parent.id) {
                    "tasks" -> knownRemoteTaskIds.add(ref.id)
                    "daily_notes" -> knownRemoteNoteIds.add(ref.id)
                    "diary_entries" -> knownRemoteDiaryIds.add(ref.id)
                }
            }
        }
    }

    suspend fun forceSyncNow(
        uid: String,
        localTasks: List<TaskItem>,
        localNotes: Map<String, String>,
        localDiary: List<DiaryEntry>,
    ): Result<Unit> {
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; cancelando sincronización." }

            // Fetch fresh state from server so connectivity/sync is verified
            val remoteTasks = userTasksCollection(uid).get(Source.SERVER).await()
            val remoteNotes = userDailyNotesCollection(uid).get(Source.SERVER).await()
            val remoteDiary = userDiaryCollection(uid).get(Source.SERVER).await()

            knownRemoteTaskIds.clear()
            knownRemoteTaskIds.addAll(remoteTasks.documents.map { it.id })
            knownRemoteNoteIds.clear()
            knownRemoteNoteIds.addAll(remoteNotes.documents.map { it.id })
            knownRemoteDiaryIds.clear()
            knownRemoteDiaryIds.addAll(remoteDiary.documents.map { it.id })

            val missingTasks = localTasks.filter { !knownRemoteTaskIds.contains(sanitizeFirestoreDocId(it.id)) }
            val missingNotes = localNotes.filter { (fecha, texto) ->
                texto.isNotBlank() && !knownRemoteNoteIds.contains(sanitizeFirestoreDocId(fecha))
            }
            val missingDiary = localDiary.filter { !knownRemoteDiaryIds.contains(sanitizeFirestoreDocId(it.id)) }

            if (missingTasks.isNotEmpty() || missingNotes.isNotEmpty() || missingDiary.isNotEmpty()) {
                uploadBatchCreates(uid, missingTasks, missingNotes, missingDiary)
            }

            // Brief visual window so manual sync feedback ("Sincronizando...") is clearly perceived before green tick
            delay(350)
            initialBootstrapActive = false
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid")
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun upsertTask(uid: String, task: TaskItem, preferCreate: Boolean = false): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(task.id)
        val path = "users/$uid/tasks/$safeId"
        beginWriteOperation()
        return try {
            upsertDocument(
                uid = uid,
                docRef = userTasksCollection(uid).document(safeId),
                knownIds = knownRemoteTaskIds,
                preferCreate = preferCreate,
                createMap = { task.toCloudCreateMap(uid) },
                updateMap = { task.toCloudUpdateMap() },
            )
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun upsertTasks(uid: String, tasks: List<TaskItem>): Result<Unit> {
        if (tasks.isEmpty()) return Result.success(Unit)
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            for (chunk in tasks.chunked(400)) {
                val batch = db.batch()
                val addedIds = mutableListOf<String>()
                for (task in chunk) {
                    val safeId = sanitizeFirestoreDocId(task.id)
                    val ref = userTasksCollection(uid).document(safeId)
                    if (knownRemoteTaskIds.contains(safeId)) {
                        batch.update(ref, task.toCloudUpdateMap())
                    } else {
                        batch.set(ref, task.toCloudCreateMap(uid))
                        addedIds.add(safeId)
                    }
                }
                batch.commit().await()
                knownRemoteTaskIds.addAll(addedIds)
            }
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/tasks")
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(uid: String, taskId: String): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(taskId)
        val path = "users/$uid/tasks/$safeId"
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            userTasksCollection(uid).document(safeId).delete().await()
            knownRemoteTaskIds.remove(safeId)
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun deleteTasksByIds(uid: String, taskIds: List<String>): Result<Unit> {
        if (taskIds.isEmpty()) return Result.success(Unit)
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            for (chunk in taskIds.chunked(400)) {
                val batch = db.batch()
                val safeIds = chunk.map { sanitizeFirestoreDocId(it) }
                safeIds.forEach { safeId ->
                    batch.delete(userTasksCollection(uid).document(safeId))
                }
                batch.commit().await()
                knownRemoteTaskIds.removeAll(safeIds.toSet())
            }
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/tasks")
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun upsertDailyNote(uid: String, fecha: String, texto: String): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(fecha)
        val path = "users/$uid/daily_notes/$safeId"
        beginWriteOperation()
        return try {
            upsertDocument(
                uid = uid,
                docRef = userDailyNotesCollection(uid).document(safeId),
                knownIds = knownRemoteNoteIds,
                preferCreate = false,
                createMap = { dailyNoteCreateMap(uid, fecha, texto) },
                updateMap = { dailyNoteUpdateMap(fecha, texto) },
            )
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun deleteDailyNote(uid: String, fecha: String): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(fecha)
        val path = "users/$uid/daily_notes/$safeId"
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            userDailyNotesCollection(uid).document(safeId).delete().await()
            knownRemoteNoteIds.remove(safeId)
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun upsertDiaryEntry(uid: String, entry: DiaryEntry, preferCreate: Boolean = false): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(entry.id)
        val path = "users/$uid/diary_entries/$safeId"
        beginWriteOperation()
        return try {
            upsertDocument(
                uid = uid,
                docRef = userDiaryCollection(uid).document(safeId),
                knownIds = knownRemoteDiaryIds,
                preferCreate = preferCreate,
                createMap = { entry.toCloudCreateMap(uid) },
                updateMap = { entry.toCloudUpdateMap() },
            )
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun deleteDiaryEntry(uid: String, entryId: String): Result<Unit> {
        val safeId = sanitizeFirestoreDocId(entryId)
        val path = "users/$uid/diary_entries/$safeId"
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            userDiaryCollection(uid).document(safeId).delete().await()
            knownRemoteDiaryIds.remove(safeId)
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    suspend fun replaceAllFromBackup(
        uid: String,
        tasks: List<TaskItem>,
        notes: Map<String, String>,
        diaryEntries: List<DiaryEntry>,
    ): Result<Unit> {
        beginWriteOperation()
        return try {
            val currentAuthUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
            check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

            // Delete previous remote docs not present or overwrite all cleanly
            val oldTaskIds = userTasksCollection(uid).get().await().documents.map { it.id }
            val oldNoteIds = userDailyNotesCollection(uid).get().await().documents.map { it.id }
            val oldDiaryIds = userDiaryCollection(uid).get().await().documents.map { it.id }

            val deleteRefs = mutableListOf<DocumentReference>()
            oldTaskIds.forEach { deleteRefs.add(userTasksCollection(uid).document(it)) }
            oldNoteIds.forEach { deleteRefs.add(userDailyNotesCollection(uid).document(it)) }
            oldDiaryIds.forEach { deleteRefs.add(userDiaryCollection(uid).document(it)) }

            for (chunk in deleteRefs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }

            knownRemoteTaskIds.clear()
            knownRemoteNoteIds.clear()
            knownRemoteDiaryIds.clear()

            uploadBatchCreates(uid, tasks, notes, diaryEntries)
            endWriteOperation(null)
            Result.success(Unit)
        } catch (e: CancellationException) {
            endWriteOperation(null)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            endWriteOperation(e)
            Result.failure(e)
        }
    }

    private suspend fun upsertDocument(
        uid: String,
        docRef: DocumentReference,
        knownIds: MutableSet<String>,
        preferCreate: Boolean,
        createMap: () -> Map<String, Any>,
        updateMap: () -> Map<String, Any>,
    ) {
        val currentAuthUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para sincronizar.")
        check(currentAuthUid == uid) { "La sesión activa cambió; operación cancelada." }

        if (knownIds.contains(docRef.id)) {
            try {
                docRef.update(updateMap()).await()
                return
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
                knownIds.remove(docRef.id)
            }
        }

        if (!preferCreate) {
            val snap = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }
            if (snap != null && snap.exists()) {
                docRef.update(updateMap()).await()
                knownIds.add(docRef.id)
                return
            }
        }

        docRef.set(createMap()).await()
        knownIds.add(docRef.id)
    }
}
