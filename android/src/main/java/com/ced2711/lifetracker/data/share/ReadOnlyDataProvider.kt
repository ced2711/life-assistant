package com.ced2711.lifetracker.data.share

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.ced2711.lifetracker.TaskLedgerApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read-only access to todos, the ledger, notes and the diary for the user's own Pocket AI app.
 *
 * Three gates, all required: the manifest's signature permission (only apps signed with this app's
 * key), [ReadAccessGate] (only Pocket AI's package), and the device-local Settings switch, which is
 * off by default. Nothing here writes, and the Vault and Confessional are never read.
 * The URIs and columns are documented in docs/POCKET_AI_READ_ACCESS.md.
 */
class ReadOnlyDataProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        ReadAccessGate.checkCaller(callingPackage)
        val match = matcher.match(uri)
        require(match != UriMatcher.NO_MATCH) { "Unknown Life Assistant URI: $uri" }
        val application = requireNotNull(context).applicationContext as TaskLedgerApplication
        val ready = runBlocking {
            withTimeoutOrNull(STARTUP_TIMEOUT_MILLIS) { application.startupRecoveryCoordinator.awaitReady() }
        }
        check(ready == true) { "Life Assistant is still starting up; try again in a moment." }
        val enabled = runBlocking { application.container.settingsRepository.settings.first().pocketAiReadAccess }
        ReadAccessGate.checkEnabled(enabled)

        val parameters = ReadOnlyQueries.Parameters(
            uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() },
        )
        val queries = ReadOnlyQueries(application.container.database.openHelper.readableDatabase)
        val cursor = when (match) {
            TODOS -> queries.todos(parameters)
            LEDGER -> queries.ledger(parameters)
            LEDGER_SUMMARY -> queries.ledgerSummary(parameters)
            LEDGER_BY_TAG -> queries.ledgerByTag(parameters)
            NOTES -> queries.notes(parameters)
            NOTE -> queries.notes(parameters, id = uri.lastPathSegment!!.toLong())
            else -> queries.diary(parameters)
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        UriMatcher.NO_MATCH -> null
        NOTE -> "vnd.android.cursor.item/vnd.$AUTHORITY.note"
        else -> "vnd.android.cursor.dir/vnd.$AUTHORITY.${uri.pathSegments.joinToString(".")}"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException(READ_ONLY)

    companion object {
        const val AUTHORITY = "com.ced2711.lifetracker.readonly"
        private const val READ_ONLY = "Life Assistant data is read-only here."
        private const val STARTUP_TIMEOUT_MILLIS = 10_000L

        private const val TODOS = 1
        private const val LEDGER = 2
        private const val LEDGER_SUMMARY = 3
        private const val LEDGER_BY_TAG = 4
        private const val NOTES = 5
        private const val NOTE = 6
        private const val DIARY = 7

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "todos", TODOS)
            addURI(AUTHORITY, "ledger", LEDGER)
            addURI(AUTHORITY, "ledger/summary", LEDGER_SUMMARY)
            addURI(AUTHORITY, "ledger/by_tag", LEDGER_BY_TAG)
            addURI(AUTHORITY, "notes", NOTES)
            addURI(AUTHORITY, "notes/#", NOTE)
            addURI(AUTHORITY, "diary", DIARY)
        }
    }
}

/** The checks [ReadOnlyDataProvider] makes beyond the manifest permission. */
object ReadAccessGate {
    /** Pocket AI's release package; other apps signed with the same key are still refused. */
    val ALLOWED_PACKAGES = setOf("com.ced2711.pocketai")

    fun checkCaller(callingPackage: String?) {
        if (callingPackage !in ALLOWED_PACKAGES) {
            throw SecurityException("This app may not read Life Assistant data.")
        }
    }

    fun checkEnabled(enabled: Boolean) {
        if (!enabled) {
            throw SecurityException(
                "Reading is turned off. Turn on Life Assistant → Settings → Security → Let Pocket AI read your data.",
            )
        }
    }
}
