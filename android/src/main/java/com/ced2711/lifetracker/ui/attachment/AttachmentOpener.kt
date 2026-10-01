package com.ced2711.lifetracker.ui.attachment

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ced2711.lifetracker.data.attachment.AttachmentStore
import com.ced2711.lifetracker.data.attachment.isStrictlyInside
import com.ced2711.lifetracker.data.local.AttachmentEntity
import java.io.File
import java.io.FileNotFoundException

class AttachmentOpener(private val context: Context) {
    fun createOpenIntent(attachment: AttachmentEntity): Intent {
        require(attachment.pendingDeleteAt == null) { "This attachment was removed." }
        val root = File(context.filesDir, AttachmentStore.ATTACHMENT_DIRECTORY)
        val file = File(attachment.privatePath)
        if (!file.isStrictlyInside(root) || !file.isFile) {
            throw FileNotFoundException("The attachment is unavailable.")
        }

        val uri = FileProvider.getUriForFile(
            context,
            attachmentProviderAuthority(context),
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType.ifBlank { DEFAULT_MIME_TYPE })
            clipData = ClipData.newRawUri(attachment.originalName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun open(attachment: AttachmentEntity): Result<Unit> = runCatching {
        val intent = createOpenIntent(attachment)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}

@Composable
fun rememberAttachmentOpener(
    onError: (String) -> Unit = {},
): (AttachmentEntity) -> Unit {
    val context = LocalContext.current
    val currentOnError = rememberUpdatedState(onError)
    val opener = remember(context) { AttachmentOpener(context) }
    return remember(opener) {
        { attachment ->
            opener.open(attachment).onFailure { error ->
                currentOnError.value(error.message ?: "No app can open this attachment.")
            }
        }
    }
}

fun attachmentProviderAuthority(context: Context): String =
    "${context.packageName}$ATTACHMENT_PROVIDER_AUTHORITY_SUFFIX"

const val ATTACHMENT_PROVIDER_AUTHORITY_SUFFIX = ".attachments"
