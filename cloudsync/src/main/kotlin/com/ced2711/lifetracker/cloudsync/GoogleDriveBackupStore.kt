package com.ced2711.lifetracker.cloudsync

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

fun interface AccessTokenProvider {
    suspend fun accessToken(): String
}

interface CloudBackupStore {
    /** limit is the page size; all pages are read so older competing heads cannot be hidden. */
    suspend fun listRevisions(limit: Int = 20): List<CloudRevision>
    suspend fun uploadRevision(source: File, revision: NewCloudRevision): CloudRevision
    suspend fun downloadRevision(revision: CloudRevision, destination: File)
    suspend fun deleteRevision(fileId: String)
}

class GoogleDriveBackupStore(
    private val tokenProvider: AccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiBaseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) : CloudBackupStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listRevisions(limit: Int): List<CloudRevision> = withContext(ioDispatcher) {
        require(limit in 1..100)
        val query = "appProperties has { key='protocol' and value='$CLOUD_SYNC_PROTOCOL' } and trashed = false"
        val result = mutableListOf<CloudRevision>()
        var pageToken: String? = null
        val seenTokens = mutableSetOf<String>()
        do {
            val url = apiBaseUrl.newBuilder().addPathSegments("drive/v3/files")
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("pageSize", limit.toString())
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime,version,size,appProperties)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }.build()
            val root = executeJson(Request.Builder().url(url).get())
            result += root["files"]?.jsonArray.orEmpty().map { element ->
                parseRevision(element) ?: throw CloudTransportException("Cloud revision metadata is invalid.")
            }
            if (result.size > 10_000) throw CloudTransportException("Cloud history is too large to sync safely.")
            pageToken = root.string("nextPageToken")?.takeIf(String::isNotBlank)
            if (pageToken != null && !seenTokens.add(pageToken!!)) {
                throw CloudTransportException("Google Drive repeated a history page.")
            }
        } while (pageToken != null)
        // Preserve server ordering. Device clocks and per-file versions are not comparable.
        result.distinctBy { it.fileId }
    }

    override suspend fun uploadRevision(
        source: File,
        revision: NewCloudRevision,
    ): CloudRevision = withContext(ioDispatcher) {
        require(source.isFile) { "Backup source does not exist." }
        val metadata = buildMetadata(revision).toRequestBody(JSON_MEDIA_TYPE)
        val media = source.asRequestBody(CLOUD_BACKUP_MIME_TYPE.toMediaType())
        val body = MultipartBody.Builder()
            .setType(MULTIPART_RELATED)
            .addPart(metadata)
            .addPart(media)
            .build()
        val fields = "id,name,modifiedTime,version,size,appProperties".urlEncoded()
        val result = executeJson(
            Request.Builder()
                .url(apiBaseUrl.resolve("upload/drive/v3/files?uploadType=multipart&fields=$fields")!!)
                .post(body),
        )
        parseRevision(result) ?: throw CloudTransportException("Google Drive returned invalid backup metadata.")
    }

    override suspend fun downloadRevision(
        revision: CloudRevision,
        destination: File,
    ) = withContext(ioDispatcher) {
        requireSafeId(revision.fileId)
        val parent = requireNotNull(destination.absoluteFile.parentFile)
        parent.mkdirs()
        val maximum = minOf(MAX_DOWNLOAD_BYTES, (parent.usableSpace - 32L * 1024L * 1024L).coerceAtLeast(0))
        if (revision.sizeBytes < 0 || revision.sizeBytes > maximum) {
            throw CloudTransportException("The cloud backup exceeds available local storage.")
        }
        val temporary = File.createTempFile("life-tracker-download-", ".part", parent)
        try {
            val request = authorized(
                Request.Builder()
                    .url(apiBaseUrl.resolve("drive/v3/files/${revision.fileId}?alt=media")!!)
                    .get(),
            ).build()
            client.newCall(request).execute().use { response ->
                ensureSuccess(response.code, response.message)
                val body = response.body ?: throw CloudTransportException("Google Drive returned an empty backup.")
                temporary.outputStream().buffered().use { output -> body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maximum || total > revision.sizeBytes) {
                            throw CloudTransportException("The cloud backup exceeds its declared size.")
                        }
                        output.write(buffer, 0, count)
                    }
                } }
            }
            if (revision.sizeBytes >= 0 && temporary.length() != revision.sizeBytes) {
                throw CloudTransportException("The downloaded backup size did not match Google Drive metadata.")
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

    override suspend fun deleteRevision(fileId: String) = withContext(ioDispatcher) {
        requireSafeId(fileId)
        val request = authorized(
            Request.Builder()
                .url(apiBaseUrl.resolve("drive/v3/files/$fileId")!!)
                .delete(),
        ).build()
        client.newCall(request).execute().use { response -> ensureSuccess(response.code, response.message) }
    }

    private suspend fun executeJson(builder: Request.Builder): JsonObject {
        val request = authorized(builder).build()
        try {
            client.newCall(request).execute().use { response ->
                ensureSuccess(response.code, response.message)
                val text = response.body?.string()
                    ?: throw CloudTransportException("Google Drive returned an empty response.")
                return json.parseToJsonElement(text).jsonObject
            }
        } catch (error: CloudAuthorizationException) {
            throw error
        } catch (error: CloudTransportException) {
            throw error
        } catch (error: IOException) {
            throw CloudTransportException("Could not reach Google Drive.", error)
        } catch (error: IllegalArgumentException) {
            throw CloudTransportException("Google Drive returned malformed data.", error)
        }
    }

    private suspend fun authorized(builder: Request.Builder): Request.Builder {
        val token = tokenProvider.accessToken()
        if (token.isBlank()) throw CloudAuthorizationException("Google Drive authorization is unavailable.")
        return builder.header("Authorization", "Bearer $token")
    }

    private fun ensureSuccess(code: Int, message: String) {
        when {
            code in 200..299 -> Unit
            code == 401 || code == 403 -> throw CloudAuthorizationException(
                "Google Drive authorization expired or was revoked.",
            )
            else -> throw CloudTransportException("Google Drive request failed ($code $message).")
        }
    }

    private fun parseRevision(element: JsonElement): CloudRevision? {
        val objectValue = element.jsonObject
        val properties = objectValue["appProperties"]?.jsonObject ?: return null
        if (properties.string("protocol") != CLOUD_SYNC_PROTOCOL) return null
        val id = objectValue.string("id") ?: return null
        if (!id.matches(SAFE_ID)) return null
        val name = objectValue.string("name") ?: return null
        val createdAt = properties.string("createdAt")?.toLongOrNull() ?: return null
        val deviceId = properties.string("deviceId") ?: return null
        val fingerprint = properties.string("contentFingerprint") ?: return null
        if (!fingerprint.matches(Regex("[0-9a-f]{64}")) || createdAt < 0) return null
        val parents = properties.entries.filter { it.key.startsWith("mergeParent") }
            .mapNotNull { it.value.jsonPrimitive.contentOrNull }
        val base = properties.string("baseRevisionId")?.takeIf(String::isNotBlank)
        if (parents.any { !it.matches(SAFE_ID) } || (base != null && !base.matches(SAFE_ID))) return null
        return CloudRevision(
            fileId = id,
            fileName = name,
            createdAt = createdAt,
            deviceId = deviceId,
            baseRevisionId = base,
            contentFingerprint = fingerprint,
            driveVersion = objectValue.string("version")?.toLongOrNull() ?: 0,
            modifiedTime = objectValue.string("modifiedTime").orEmpty(),
            sizeBytes = objectValue.string("size")?.toLongOrNull() ?: -1,
            mergedRevisionIds = parents,
        )
    }

    private fun buildMetadata(revision: NewCloudRevision): String = json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                "name" to revision.fileName.asJson(),
                "parents" to kotlinx.serialization.json.JsonArray(listOf("appDataFolder".asJson())),
                "appProperties" to JsonObject(
                    mapOf(
                        "protocol" to CLOUD_SYNC_PROTOCOL.asJson(),
                        "createdAt" to revision.createdAt.toString().asJson(),
                        "deviceId" to revision.deviceId.asJson(),
                        "baseRevisionId" to revision.baseRevisionId.orEmpty().asJson(),
                        "contentFingerprint" to revision.contentFingerprint.asJson(),
                    ) + revision.mergedRevisionIds.distinct().mapIndexed { index, id -> "mergeParent$index" to id.asJson() },
                ),
            ),
        ),
    )

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

    private fun String.urlEncoded(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.asJson() = kotlinx.serialization.json.JsonPrimitive(this)

    private fun requireSafeId(id: String) = require(id.matches(SAFE_ID)) { "Invalid Drive file ID." }

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9_-]{1,100}")
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L * 1024L
        val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
        val MULTIPART_RELATED = "multipart/related".toMediaType()
    }
}
