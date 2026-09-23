package com.pratham.thebookapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API =
    "https://api.github.com/repos/Prathamgit9/The-Book-App/releases/latest"

private data class UpdateInfo(
    val version: String,
    val assetName: String,
    val assetUrl: String
)

private sealed interface UpdateState {
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

@Composable
internal fun UpdateSection() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }

    LaunchedEffect(state is UpdateState.Checking) {
        if (state !is UpdateState.Checking) return@LaunchedEffect
        state = try {
            checkForUpdate()
        } catch (_: Throwable) {
            UpdateState.Error("Couldn't check GitHub Releases right now.")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("UPDATES", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            "The app checks its GitHub Releases feed for a newer version. Android still asks you to confirm the installation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                when (val current = state) {
                    UpdateState.Checking -> {
                        Text("Checking for updates…", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    UpdateState.Current -> {
                        Text(
                            "You're up to date · " + BuildConfig.VERSION_NAME,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = { state = UpdateState.Checking },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CHECK AGAIN")
                        }
                    }

                    is UpdateState.Available -> {
                        Text(
                            "Version " + current.info.version + " is available.",
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = { state = UpdateState.Downloading(current.info, 0) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DOWNLOAD UPDATE")
                        }
                        Text(
                            "Downloaded directly from this app's GitHub Release.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            "Downloading " + current.info.version + "…",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { current.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        LaunchedEffect(current.info.version) {
                            state = try {
                                val file = downloadApk(context, current.info) { progress ->
                                    state = UpdateState.Downloading(current.info, progress)
                                }
                                UpdateState.Ready(current.info, file)
                            } catch (_: Throwable) {
                                UpdateState.Error("The update download failed. Please try again.")
                            }
                        }
                    }

                    is UpdateState.Ready -> {
                        Text("Update downloaded and ready to install.", color = MaterialTheme.colorScheme.onSurface)
                        Button(
                            onClick = { installApk(context, current.file) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("INSTALL UPDATE")
                        }
                    }

                    is UpdateState.Error -> {
                        Text(current.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { state = UpdateState.Checking },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TRY AGAIN")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun checkForUpdate(): UpdateState = withContext(Dispatchers.IO) {
    val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "The-Book-App/" + BuildConfig.VERSION_NAME)
    }

    connection.inputStream.use { stream ->
        val release = JSONObject(stream.bufferedReader().readText())
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            return@withContext UpdateState.Current
        }

        val tag = release.optString("tag_name").removePrefix("v").trim()
        if (tag.isBlank() || !isNewerVersion(tag, BuildConfig.VERSION_NAME)) {
            return@withContext UpdateState.Current
        }

        val assets = release.optJSONArray("assets") ?: return@withContext UpdateState.Current
        var asset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val candidate = assets.optJSONObject(i) ?: continue
            if (candidate.optString("name").endsWith(".apk", ignoreCase = true)) {
                asset = candidate
                break
            }
        }

        val apk = asset ?: return@withContext UpdateState.Error(
            "A newer release exists, but no APK was attached."
        )
        UpdateState.Available(
            UpdateInfo(
                version = tag,
                assetName = apk.optString("name").ifBlank { "The-Book-App-v" + tag + ".apk" },
                assetUrl = apk.optString("browser_download_url")
            )
        )
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    fun parts(value: String): List<Int> =
        value.trim().removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val left = a.getOrElse(i) { 0 }
        val right = b.getOrElse(i) { 0 }
        if (left != right) return left > right
    }
    return false
}

private suspend fun downloadApk(
    context: Context,
    info: UpdateInfo,
    onProgress: (Int) -> Unit
): File = withContext(Dispatchers.IO) {
    val safeName = info.assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
    val destination = File(updateDir, safeName)
    if (destination.exists()) destination.delete()

    val connection = (URL(info.assetUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/octet-stream")
        setRequestProperty("User-Agent", "The-Book-App/" + BuildConfig.VERSION_NAME)
    }

    val total = connection.contentLengthLong
    var downloaded = 0L
    connection.inputStream.use { input ->
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                downloaded += count
                if (total > 0) {
                    onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                }
            }
        }
    }

    if (!destination.exists() || destination.length() == 0L) {
        throw IllegalStateException("Empty APK download")
    }
    destination
}

private fun installApk(context: Context, file: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        val settings = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)
        )
        context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }

    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
