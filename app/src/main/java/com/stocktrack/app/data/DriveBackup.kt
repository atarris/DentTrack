package com.stocktrack.app.data

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Uploads backups to the user's own Google Drive, into a folder called "DentTrack Backups".
 * Uses the narrow `drive.file` scope: the app can only see files it created itself.
 *
 * One-time developer setup is needed in Google Cloud (see README): enable the Drive API and create
 * an OAuth "Android" client for this package name and signing certificate.
 */
object DriveBackup {

    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val FOLDER_NAME = "DentTrack Backups"
    const val MONTHLY_FOLDER_NAME = "DentTrack Monthly Reports"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"

    sealed class Auth {
        class Token(val value: String) : Auth()
        class NeedsConsent(val intent: PendingIntent) : Auth()
        class Failed(val reason: String) : Auth()
    }

    /** Asks Google for an access token. Shows no UI; if consent is needed it says so. */
    suspend fun authorize(context: Context): Auth {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            val pending = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pending != null -> Auth.NeedsConsent(pending)
                token != null -> Auth.Token(token)
                else -> Auth.Failed("Google returned no access token")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Auth.Failed(e.message ?: "Google sign-in failed")
        }
    }

    /** Uploads [bytes] as [fileName] into the DentTrack Backups folder. Throws on failure. */
    suspend fun upload(token: String, fileName: String, bytes: ByteArray, folderName: String = FOLDER_NAME) {
        withContext(Dispatchers.IO) {
            val folderId = findOrCreateFolder(token, folderName)
            val boundary = "denttrack" + System.currentTimeMillis()
            val metadata = JSONObject()
                .put("name", fileName)
                .put("mimeType", XlsxWriter.MIME)
                .put("parents", JSONArray().put(folderId))

            val body = ByteArrayOutputStream()
            fun write(text: String) = body.write(text.toByteArray(Charsets.UTF_8))
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            write(metadata.toString())
            write("\r\n--$boundary\r\nContent-Type: ${XlsxWriter.MIME}\r\n\r\n")
            body.write(bytes)
            write("\r\n--$boundary--")

            http(
                "POST",
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
                token,
                "multipart/related; boundary=$boundary",
                body.toByteArray()
            )
        }
    }

    private fun findOrCreateFolder(token: String, folderName: String): String {
        val query = "name = '$folderName' and mimeType = '$FOLDER_MIME' and trashed = false"
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=" +
            URLEncoder.encode(query, "UTF-8") + "&fields=files(id)&pageSize=1"
        val files = JSONObject(http("GET", listUrl, token, null, null)).optJSONArray("files")
        if (files != null && files.length() > 0) return files.getJSONObject(0).getString("id")

        val create = JSONObject().put("name", folderName).put("mimeType", FOLDER_MIME)
        val created = http(
            "POST", "https://www.googleapis.com/drive/v3/files", token,
            "application/json; charset=UTF-8", create.toString().toByteArray(Charsets.UTF_8)
        )
        return JSONObject(created).getString("id")
    }

    private fun http(method: String, url: String, token: String, contentType: String?, body: ByteArray?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Drive error $code ${text.take(160)}")
            return text
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }
}
