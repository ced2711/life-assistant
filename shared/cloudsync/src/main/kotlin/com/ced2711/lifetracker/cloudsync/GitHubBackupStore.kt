package com.ced2711.lifetracker.cloudsync

import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

/** A private repository written as `owner/name`. */
data class GitHubRepository(val owner: String, val name: String) {
    init {
        require(owner.matches(OWNER) && name.matches(NAME)) { "Enter the repository as owner/name." }
    }

    val fullName: String get() = "$owner/$name"

    companion object {
        private val OWNER = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")
        private val NAME = Regex("[A-Za-z0-9._-]{1,100}")

        fun parse(value: String): GitHubRepository {
            val parts = value.trim().removePrefix("https://github.com/").removeSuffix(".git").split('/')
            require(parts.size == 2) { "Enter the repository as owner/name." }
            return GitHubRepository(parts[0], parts[1])
        }
    }
}

@Serializable
internal data class GitHubIndexEntry(val revision: CloudRevision, val blobSha: String)

@Serializable
internal data class GitHubSyncIndex(
    val protocol: String = CLOUD_SYNC_PROTOCOL,
    val revisions: List<GitHubIndexEntry> = emptyList(),
)

/**
 * Encrypted snapshots stored on a dedicated branch of a private GitHub repository.
 *
 * The branch holds `revisions/<id>.tlb` files and an `index.json` with their revision metadata,
 * newest first. Every change is one commit and the branch only moves by fast-forward, so two
 * devices racing each other retry instead of overwriting one another.
 */
class GitHubBackupStore(
    private val tokenProvider: AccessTokenProvider,
    private val repository: GitHubRepository,
    private val branch: String = DEFAULT_BRANCH,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiBaseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : CloudBackupStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    @Volatile private var blobShas: Map<String, String> = emptyMap()

    // Each public operation fetches the token once; the sync engines serialize operations.
    @Volatile private var token: String = ""

    private suspend fun <T> withToken(block: () -> T): T = withContext(ioDispatcher) {
        token = tokenProvider.accessToken()
        if (token.isBlank()) throw CloudAuthorizationException("GitHub is not connected.")
        block()
    }

    override suspend fun listRevisions(limit: Int): List<CloudRevision> = retryTransient { listRevisionsOnce(limit) }

    private suspend fun listRevisionsOnce(limit: Int): List<CloudRevision> = withToken {
        require(limit in 1..100)
        val head = readHead() ?: return@withToken emptyList()
        readIndex(head.commitSha).also(::remember).revisions.map(GitHubIndexEntry::revision)
    }

    override suspend fun uploadRevision(source: File, revision: NewCloudRevision): CloudRevision =
        withToken {
            require(source.isFile) { "Backup source does not exist." }
            require(source.length() <= MAX_BLOB_BYTES) { "The backup is larger than GitHub's 100 MB file limit." }
            ensureRepositoryInitialized()
            val blobSha = createBlob(source)
            val id = "${revision.createdAt}-${revision.deviceId.filter(Char::isLetterOrDigit).take(8)}-" +
                ByteArray(4).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            val created = CloudRevision(
                fileId = id,
                fileName = "revisions/$id.tlb",
                createdAt = revision.createdAt,
                deviceId = revision.deviceId,
                baseRevisionId = revision.baseRevisionId,
                contentFingerprint = revision.contentFingerprint,
                driveVersion = 0,
                modifiedTime = Instant.ofEpochMilli(now()).toString(),
                sizeBytes = source.length(),
                mergedRevisionIds = revision.mergedRevisionIds.distinct(),
            )
            commitWithRetry("Add backup ${created.createdAt}") { head, index ->
                val next = index.copy(revisions = listOf(GitHubIndexEntry(created, blobSha)) + index.revisions)
                TreeChange(
                    baseTree = head?.treeSha,
                    files = mapOf(created.fileName to blobSha),
                    index = next,
                )
            }
            created
        }

    override suspend fun downloadRevision(revision: CloudRevision, destination: File) = withToken {
        val sha = blobShas[revision.fileId]
            ?: readHead()?.let { readIndex(it.commitSha).also(::remember) }
                ?.revisions?.firstOrNull { it.revision.fileId == revision.fileId }?.blobSha
            ?: throw CloudTransportException("The GitHub backup no longer exists.")
        val parent = requireNotNull(destination.absoluteFile.parentFile)
        parent.mkdirs()
        val temporary = File.createTempFile("life-tracker-download-", ".part", parent)
        try {
            val request = authorized(repoUrl("git/blobs/$sha")).header("Accept", RAW).get().build()
            execute(request).use { response ->
                val body = response.body ?: throw CloudTransportException("GitHub returned an empty backup.")
                temporary.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BLOB_BYTES || (revision.sizeBytes >= 0 && total > revision.sizeBytes)) {
                                throw CloudTransportException("The cloud backup exceeds its declared size.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            if (revision.sizeBytes >= 0 && temporary.length() != revision.sizeBytes) {
                throw CloudTransportException("The downloaded backup size did not match GitHub metadata.")
            }
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Unit
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    override suspend fun deleteRevision(fileId: String) = withToken {
        commitWithRetry("Remove old backup") { head, index ->
            val entry = index.revisions.firstOrNull { it.revision.fileId == fileId } ?: return@commitWithRetry null
            TreeChange(
                baseTree = head?.treeSha,
                files = mapOf(entry.revision.fileName to null),
                index = index.copy(revisions = index.revisions - entry),
            )
        }
        Unit
    }

    private data class Head(val commitSha: String, val treeSha: String)

    /** [files] maps a path to a blob SHA, or to null to delete that path. */
    private data class TreeChange(
        val baseTree: String?,
        val files: Map<String, String?>,
        val index: GitHubSyncIndex,
    )

    private fun commitWithRetry(message: String, change: (Head?, GitHubSyncIndex) -> TreeChange?) {
        repeat(MAX_COMMIT_ATTEMPTS) {
            val head = readHead()
            val index = head?.let { readIndex(it.commitSha) } ?: GitHubSyncIndex()
            val planned = change(head, index) ?: return
            val tree = createTree(planned)
            val commit = createCommit(message, tree, listOfNotNull(head?.commitSha))
            if (moveBranch(head, commit)) {
                remember(planned.index)
                return
            }
        }
        throw CloudTransportException("GitHub kept changing while saving. Sync again.")
    }

    private fun remember(index: GitHubSyncIndex) {
        blobShas = index.revisions.associate { it.revision.fileId to it.blobSha }
    }

    private fun readHead(): Head? {
        val request = authorized(repoUrl("git/ref/heads/$branch")).get().build()
        val commitSha = client.newCall(request).executeSafely().use { response ->
            when (response.code) {
                404, 409 -> return null // No sync branch yet, or a repository without any commit.
                else -> ensureSuccess(response)
            }
            parse(response).objectAt("object").string("sha")
        }
        val commit = getJson(repoUrl("git/commits/$commitSha"))
        return Head(commitSha, commit.objectAt("tree").string("sha"))
    }

    private fun readIndex(commitSha: String): GitHubSyncIndex {
        val url = repoUrl("contents/$INDEX_PATH").newBuilder().addQueryParameter("ref", commitSha).build()
        val request = authorized(url).header("Accept", RAW).get().build()
        val text = client.newCall(request).executeSafely().use { response ->
            if (response.code == 404) return GitHubSyncIndex()
            ensureSuccess(response)
            response.body?.string() ?: throw CloudTransportException("GitHub returned an empty sync index.")
        }
        val index = try {
            json.decodeFromString(GitHubSyncIndex.serializer(), text)
        } catch (error: IllegalArgumentException) {
            throw CloudTransportException("The GitHub sync index is damaged.", error)
        }
        if (index.protocol != CLOUD_SYNC_PROTOCOL) throw CloudTransportException("The GitHub branch belongs to another app version.")
        if (index.revisions.size > 10_000) throw CloudTransportException("Cloud history is too large to sync safely.")
        index.revisions.forEach { entry ->
            val revision = entry.revision
            val valid = revision.fileId.matches(SAFE_ID) &&
                entry.blobSha.matches(GIT_SHA) &&
                revision.contentFingerprint.matches(FINGERPRINT) &&
                (revision.baseRevisionId?.matches(SAFE_ID) ?: true) &&
                revision.mergedRevisionIds.all { it.matches(SAFE_ID) } &&
                revision.fileName == "revisions/${revision.fileId}.tlb"
            if (!valid) throw CloudTransportException("Cloud revision metadata is invalid.")
        }
        return index
    }

    /** Git's data API cannot write to a repository with no commits, so create a first one. */
    private fun ensureRepositoryInitialized() {
        val request = authorized(repoUrl("git/ref/heads/$branch")).get().build()
        val empty = client.newCall(request).executeSafely().use { it.code == 409 }
        if (!empty) return
        val body = JsonObject(
            mapOf(
                "message" to JsonPrimitive("Initialize Life Assistant sync"),
                "content" to JsonPrimitive(Base64.getEncoder().encodeToString(README.encodeToByteArray())),
            ),
        )
        val put = authorized(repoUrl("contents/README.md")).put(body.toString().toRequestBody(JSON_TYPE)).build()
        client.newCall(put).executeSafely().use { response ->
            if (response.code != 422) ensureSuccess(response) // 422: another device initialized it first.
        }
    }

    private fun createBlob(source: File): String {
        val request = authorized(repoUrl("git/blobs")).post(Base64FileJsonBody(source)).build()
        return client.newCall(request).executeSafely().use { response ->
            ensureSuccess(response)
            parse(response).string("sha")
        }
    }

    private fun createTree(change: TreeChange): String {
        val entries = change.files.map { (path, sha) ->
            JsonObject(
                mapOf(
                    "path" to JsonPrimitive(path),
                    "mode" to JsonPrimitive("100644"),
                    "type" to JsonPrimitive("blob"),
                    "sha" to (sha?.let(::JsonPrimitive) ?: JsonNull),
                ),
            )
        } + JsonObject(
            mapOf(
                "path" to JsonPrimitive(INDEX_PATH),
                "mode" to JsonPrimitive("100644"),
                "type" to JsonPrimitive("blob"),
                "content" to JsonPrimitive(json.encodeToString(GitHubSyncIndex.serializer(), change.index)),
            ),
        )
        val body = JsonObject(
            buildMap {
                change.baseTree?.let { put("base_tree", JsonPrimitive(it)) }
                put("tree", JsonArray(entries))
            },
        )
        return postJson(repoUrl("git/trees"), body).string("sha")
    }

    private fun createCommit(message: String, tree: String, parents: List<String>): String {
        val body = JsonObject(
            mapOf(
                "message" to JsonPrimitive(message),
                "tree" to JsonPrimitive(tree),
                "parents" to JsonArray(parents.map(::JsonPrimitive)),
            ),
        )
        return postJson(repoUrl("git/commits"), body).string("sha")
    }

    /** Returns false when another device moved the branch first. */
    private fun moveBranch(head: Head?, commit: String): Boolean {
        val request = if (head == null) {
            val body = JsonObject(mapOf("ref" to JsonPrimitive("refs/heads/$branch"), "sha" to JsonPrimitive(commit)))
            authorized(repoUrl("git/refs")).post(body.toString().toRequestBody(JSON_TYPE)).build()
        } else {
            val body = JsonObject(mapOf("sha" to JsonPrimitive(commit), "force" to JsonPrimitive(false)))
            authorized(repoUrl("git/refs/heads/$branch")).patch(body.toString().toRequestBody(JSON_TYPE)).build()
        }
        return client.newCall(request).executeSafely().use { response ->
            if (response.code == 422 || response.code == 409) return false
            ensureSuccess(response)
            true
        }
    }

    private fun getJson(url: HttpUrl): JsonObject =
        client.newCall(authorized(url).get().build()).executeSafely().use { response ->
            ensureSuccess(response)
            parse(response)
        }

    private fun postJson(url: HttpUrl, body: JsonObject): JsonObject =
        client.newCall(authorized(url).post(body.toString().toRequestBody(JSON_TYPE)).build()).executeSafely().use { response ->
            ensureSuccess(response)
            parse(response)
        }

    private fun execute(request: Request): Response {
        val response = client.newCall(request).executeSafely()
        try {
            ensureSuccess(response)
        } catch (error: Throwable) {
            response.close()
            throw error
        }
        return response
    }

    private fun repoUrl(path: String): HttpUrl = apiBaseUrl.newBuilder()
        .addPathSegments("repos/${repository.owner}/${repository.name}/$path")
        .build()

    private fun authorized(url: HttpUrl): Request.Builder {
        return Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
    }

    private fun okhttp3.Call.executeSafely(): Response = try {
        execute()
    } catch (error: IOException) {
        throw unreachable(error)
    }

    private fun ensureSuccess(response: Response) {
        val code = response.code
        if (code in 200..299) return
        val message = response.peekBody(4_096).string().let { text ->
            runCatching { json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        }.orEmpty()
        throw when {
            code == 401 -> GitHubSignInExpiredException()
            code == 403 && message.contains("rate limit", ignoreCase = true) ->
                CloudTransportException("GitHub's rate limit was reached. Try again in a few minutes.")
            code == 403 || code == 404 -> CloudAuthorizationException(
                "The GitHub app cannot write to ${repository.fullName}. Install it on that repository " +
                    "with Contents read and write permission.",
            )
            else -> CloudTransportException("GitHub request failed ($code${if (message.isBlank()) "" else ": $message"}).")
        }
    }

    private fun parse(response: Response): JsonObject {
        val text = response.body?.string() ?: throw CloudTransportException("GitHub returned an empty response.")
        return try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: IllegalArgumentException) {
            throw CloudTransportException("GitHub returned malformed data.", error)
        }
    }

    private fun JsonObject.objectAt(name: String): JsonObject =
        this[name]?.jsonObject ?: throw CloudTransportException("GitHub returned incomplete data.")

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull ?: throw CloudTransportException("GitHub returned incomplete data.")

    /** Streams `{"encoding":"base64","content":"…"}` without holding the backup in memory. */
    private class Base64FileJsonBody(private val source: File) : RequestBody() {
        override fun contentType() = JSON_TYPE

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("{\"encoding\":\"base64\",\"content\":\"")
            val keepOpen = object : FilterOutputStream(sink.outputStream()) {
                override fun close() = flush()
            }
            Base64.getEncoder().wrap(keepOpen).use { encoder -> source.inputStream().use { it.copyTo(encoder) } }
            sink.writeUtf8("\"}")
        }
    }

    companion object {
        const val DEFAULT_BRANCH = "life-assistant-sync"
        private const val INDEX_PATH = "index.json"
        private const val API_VERSION = "2022-11-28"
        private const val RAW = "application/vnd.github.raw"
        private const val MAX_BLOB_BYTES = 100L * 1024L * 1024L
        private const val MAX_COMMIT_ATTEMPTS = 4
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,100}")
        private val GIT_SHA = Regex("[0-9a-f]{40}")
        private val FINGERPRINT = Regex("[0-9a-f]{64}")
        private const val README = "Encrypted Life Assistant backups live on the life-assistant-sync branch.\n"
    }
}

