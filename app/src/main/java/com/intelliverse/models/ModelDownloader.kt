package com.intelliverse.models

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Streams a single file from [RemoteArtifact.downloadUrl] into a `.part`
 * file, resumable via HTTP Range -- ported from rmant7/AI's own
 * ModelDownloader.kt. Plain HttpURLConnection, no OkHttp/WorkManager
 * dependency; caller is responsible for renaming the `.part` file once this
 * returns successfully.
 */
class ModelDownloader {

    class CancelledException : Exception("Download cancelled")

    fun download(
        artifact: RemoteArtifact,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        var resumeFrom = destination.length()
        var consecutiveFailures = 0
        var backoffMs = 1_000L

        while (true) {
            if (isCancelled()) throw CancelledException()
            try {
                val connection = openConnection(artifact.downloadUrl, resumeFrom)
                val status = connection.responseCode
                val supportsResume = status == HttpURLConnection.HTTP_PARTIAL
                if (!supportsResume) resumeFrom = 0L

                RandomAccessFile(destination, "rw").use { output ->
                    output.seek(resumeFrom)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = resumeFrom
                        var sinceLastReport = 0L
                        var lastReportTime = System.currentTimeMillis()
                        while (true) {
                            if (isCancelled()) throw CancelledException()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            sinceLastReport += read
                            val now = System.currentTimeMillis()
                            if (sinceLastReport >= REPORT_EVERY_BYTES || now - lastReportTime >= REPORT_INTERVAL_MS) {
                                onProgress(totalRead, artifact.sizeBytes)
                                sinceLastReport = 0
                                lastReportTime = now
                            }
                        }
                        onProgress(totalRead, artifact.sizeBytes)
                        if (artifact.sizeBytes > 0 && totalRead < artifact.sizeBytes) {
                            throw IllegalStateException("Stream ended early: $totalRead/${artifact.sizeBytes} bytes")
                        }
                    }
                }
                return
            } catch (e: CancelledException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) throw e
                resumeFrom = destination.length()
                Thread.sleep(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    /** HttpURLConnection doesn't follow a cross-host redirect on its own
     * (every HF download 302s to a CDN host) -- followed by hand here. */
    private fun openConnection(urlString: String, resumeFrom: Long, redirectsLeft: Int = 5): HttpURLConnection {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
        val status = connection.responseCode
        if (status in 300..399 && redirectsLeft > 0) {
            val location = connection.getHeaderField("Location") ?: return connection
            return openConnection(location, resumeFrom, redirectsLeft - 1)
        }
        return connection
    }

    private companion object {
        const val REPORT_EVERY_BYTES = 1_000_000L
        const val REPORT_INTERVAL_MS = 200L
        const val MAX_CONSECUTIVE_FAILURES = 6
    }
}
