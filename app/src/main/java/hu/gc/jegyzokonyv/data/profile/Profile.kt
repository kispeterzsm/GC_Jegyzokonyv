package hu.gc.jegyzokonyv.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.gc.jegyzokonyv.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val name: String = "",
    val companyName: String = "",
    val phone: String = "",
    val email: String = "",
    val signaturePath: String = "",
    val stampPath: String = "",
    val signatureOriginalPath: String = "",
    val stampOriginalPath: String = "",
    val signatureTransparency: Float = 0f,
    val stampTransparency: Float = 0f,
) {
    fun missingForSafetyTemplate(): List<String> = buildList {
        if (name.isBlank()) add("név")
        if (companyName.isBlank()) add("cégnév")
        if (signaturePath.isBlank()) add("aláírás")
        if (stampPath.isBlank()) add("bélyegző")
    }
}

class MissingProfileInfoException(val missing: List<String>) : IllegalStateException(
    "Állítsd be a profilban a következő adatokat: ${missing.joinToString(", ")}"
)

interface ProfileRepository {
    val profile: StateFlow<UserProfile>
    suspend fun save(profile: UserProfile)
    suspend fun saveImage(uri: Uri, kind: ProfileImageKind): Pair<String, String>
    suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String
    fun imageFile(path: String): File?
}

enum class ProfileImageKind(val fileName: String) { Signature("signature"), Stamp("stamp") }

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ProfileRepository {
    private val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    private val profileDir: File get() = File(context.filesDir, "profile").apply { mkdirs() }
    private val _profile = MutableStateFlow(readProfile())
    override val profile: StateFlow<UserProfile> = _profile

    override suspend fun save(profile: UserProfile) = withContext(io) {
        prefs.edit()
            .putString(KEY_NAME, profile.name)
            .putString(KEY_COMPANY, profile.companyName)
            .putString(KEY_PHONE, profile.phone)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_SIGNATURE, profile.signaturePath)
            .putString(KEY_STAMP, profile.stampPath)
            .putString(KEY_SIGNATURE_ORIGINAL, profile.signatureOriginalPath)
            .putString(KEY_STAMP_ORIGINAL, profile.stampOriginalPath)
            .putFloat(KEY_SIGNATURE_TRANSPARENCY, profile.signatureTransparency)
            .putFloat(KEY_STAMP_TRANSPARENCY, profile.stampTransparency)
            .apply()
        _profile.value = profile
    }

    override suspend fun saveImage(uri: Uri, kind: ProfileImageKind): Pair<String, String> = withContext(io) {
        val extension = context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.length <= 5 } ?: "jpg"
        val original = File(profileDir, "${kind.fileName}_original.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            original.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Nem sikerült megnyitni a képet")
        val edited = File(profileDir, "${kind.fileName}_edited.png")
        original.copyTo(edited, overwrite = true)
        original.absolutePath to edited.absolutePath
    }

    override suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String = withContext(io) {
        val current = profile.value
        val originalPath = when (kind) {
            ProfileImageKind.Signature -> current.signatureOriginalPath.ifBlank { current.signaturePath }
            ProfileImageKind.Stamp -> current.stampOriginalPath.ifBlank { current.stampPath }
        }
        val original = imageFile(originalPath) ?: error("Nincs eredeti kép")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDIT_IMAGE_SIZE, MAX_EDIT_IMAGE_SIZE)
        val source = BitmapFactory.decodeFile(
            original.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: error("Nem sikerült betölteni a képet")
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val maxDistance = (tolerance.coerceIn(0f, 100f) * 2.55f).toInt()
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = color ushr 24 and 0xff
            val r = color ushr 16 and 0xff
            val g = color ushr 8 and 0xff
            val b = color and 0xff
            val distanceFromWhite = maxOf(255 - r, 255 - g, 255 - b)
            pixels[i] = if (distanceFromWhite <= maxDistance) {
                color and 0x00ffffff
            } else {
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val edited = File(profileDir, "${kind.fileName}_edited_${System.currentTimeMillis()}.png")
        FileOutputStream(edited).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        profileDir.listFiles { file ->
            file.name.startsWith("${kind.fileName}_edited") && file.name != edited.name
        }?.forEach { it.delete() }
        source.recycle()
        output.recycle()
        edited.absolutePath
    }

    override fun imageFile(path: String): File? = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }

    private fun calculateInSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        if (width <= 0 || height <= 0) return sampleSize
        while (width / (sampleSize * 2) >= targetWidth || height / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun readProfile() = UserProfile(
        name = prefs.getString(KEY_NAME, "").orEmpty(),
        companyName = prefs.getString(KEY_COMPANY, "").orEmpty(),
        phone = prefs.getString(KEY_PHONE, "").orEmpty(),
        email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        signaturePath = prefs.getString(KEY_SIGNATURE, "").orEmpty(),
        stampPath = prefs.getString(KEY_STAMP, "").orEmpty(),
        signatureOriginalPath = prefs.getString(KEY_SIGNATURE_ORIGINAL, "").orEmpty().ifBlank { prefs.getString(KEY_SIGNATURE, "").orEmpty() },
        stampOriginalPath = prefs.getString(KEY_STAMP_ORIGINAL, "").orEmpty().ifBlank { prefs.getString(KEY_STAMP, "").orEmpty() },
        signatureTransparency = prefs.getFloat(KEY_SIGNATURE_TRANSPARENCY, 0f),
        stampTransparency = prefs.getFloat(KEY_STAMP_TRANSPARENCY, 0f),
    )

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_COMPANY = "company"
        const val KEY_PHONE = "phone"
        const val KEY_EMAIL = "email"
        const val KEY_SIGNATURE = "signature"
        const val KEY_STAMP = "stamp"
        const val KEY_SIGNATURE_ORIGINAL = "signatureOriginal"
        const val KEY_STAMP_ORIGINAL = "stampOriginal"
        const val KEY_SIGNATURE_TRANSPARENCY = "signatureTransparency"
        const val KEY_STAMP_TRANSPARENCY = "stampTransparency"
        const val MAX_EDIT_IMAGE_SIZE = 1800
    }
}
