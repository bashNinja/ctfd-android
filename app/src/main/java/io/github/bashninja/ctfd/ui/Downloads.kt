package io.github.bashninja.ctfd.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import io.github.bashninja.ctfd.data.CtfdApi

/** The file name in a CTFd file URL such as /files/ab12/evidence.zip?token=... */
fun fileName(path: String): String =
    Uri.parse(path).lastPathSegment?.takeIf { it.isNotBlank() } ?: "file"

/**
 * Hands a challenge file to the system downloader, into Downloads/ctfd/<ctf>/<challenge>/.
 * Returns where it will land.
 */
fun downloadFile(context: Context, api: CtfdApi, ctfName: String, challenge: String, path: String): String {
    val url = api.fileUrl(path) ?: error("Bad file URL")
    val name = fileName(path)
    val dir = listOf("ctfd", ctfName, challenge).joinToString("/") { safe(it) }
    // The URL carries its own per-account file token. No Authorization header, because
    // hosted CTFd redirects downloads to cloud storage, which must never see the API token.
    val request = DownloadManager.Request(Uri.parse(url.toString()))
        .setTitle(name)
        .setDescription(challenge)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$dir/${safe(name)}")
    context.getSystemService(DownloadManager::class.java).enqueue(request)
    return "Downloads/$dir/${safe(name)}"
}

private fun safe(part: String) = part.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "_" }
