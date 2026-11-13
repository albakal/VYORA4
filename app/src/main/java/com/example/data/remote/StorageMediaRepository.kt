package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed interface UploadStatus {
    data class Progress(val percent: Int) : UploadStatus
    data class Completed(val downloadUrl: String, val thumbnailUrl: String = "") : UploadStatus
    data class Failed(val message: String) : UploadStatus
}

class StorageMediaRepository(
    private val appContext: Context,
    private val supabase: SupabaseClient = SupabaseClient.getInstance(appContext)
) {
    fun uploadVideoWithThumbnail(
        context: Context,
        userId: String,
        videoUri: Uri,
        customCoverUri: Uri? = null
    ): Flow<UploadStatus> = flow {
        val fileId = UUID.randomUUID().toString()
        emit(UploadStatus.Progress(10))

        val videoBytes = withContext(Dispatchers.IO) { readUriBytes(context, videoUri) }
        if (videoBytes == null || videoBytes.isEmpty()) {
            emit(UploadStatus.Failed("Unable to read selected video file"))
            return@flow
        }

        emit(UploadStatus.Progress(35))
        val videoUrl = try {
            supabase.uploadStorageObject(
                objectPath = "videos/$userId/$fileId.mp4",
                bytes = videoBytes,
                contentType = context.contentResolver.getType(videoUri) ?: "video/mp4"
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(
                UploadStatus.Failed(
                    e.localizedMessage ?: "Video upload failed (verify Supabase Storage bucket vyora-media)"
                )
            )
            return@flow
        }

        emit(UploadStatus.Progress(80))
        val thumbBytes = withContext(Dispatchers.IO) {
            if (customCoverUri != null) {
                readUriBytes(context, customCoverUri)
            } else {
                extractVideoFrameBytes(context, videoUri)
            }
        }

        val thumbUrl = if (thumbBytes != null && thumbBytes.isNotEmpty()) {
            runCatching {
                supabase.uploadStorageObject(
                    objectPath = "thumbnails/$userId/$fileId.jpg",
                    bytes = thumbBytes,
                    contentType = "image/jpeg"
                )
            }.getOrDefault("")
        } else {
            ""
        }

        emit(UploadStatus.Progress(100))
        emit(UploadStatus.Completed(downloadUrl = videoUrl, thumbnailUrl = thumbUrl))
    }

    suspend fun uploadImage(userId: String, folder: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readUriBytes(appContext, uri)
                ?: throw IllegalStateException("Unable to read image file")
            supabase.uploadStorageObject(
                objectPath = "$folder/$userId/${UUID.randomUUID()}.jpg",
                bytes = bytes,
                contentType = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            )
        }
    }

    suspend fun uploadMediaFile(
        userId: String,
        folder: String,
        uri: Uri,
        extension: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readUriBytes(appContext, uri)
                ?: throw IllegalStateException("Unable to read media file")
            val mimeType = when (extension.lowercase()) {
                "mp4" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                else -> "image/jpeg"
            }
            supabase.uploadStorageObject(
                objectPath = "$folder/$userId/${UUID.randomUUID()}.$extension",
                bytes = bytes,
                contentType = mimeType
            )
        }
    }

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun extractVideoFrameBytes(context: Context, videoUri: Uri): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                val targetHeight = (480 * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(480)
                val scaled = Bitmap.createScaledBitmap(bitmap, 480, targetHeight, true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                baos.toByteArray()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
