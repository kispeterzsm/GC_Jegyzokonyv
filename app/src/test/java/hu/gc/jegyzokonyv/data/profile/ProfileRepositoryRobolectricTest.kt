package hu.gc.jegyzokonyv.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryRobolectricTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("user_profile", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "profile").deleteRecursively()
    }

    @Test
    fun savePersistsProfileAcrossRepositoryInstances() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ProfileRepositoryImpl(context, dispatcher)
        val profile = UserProfile(
            name = "Teszt Elek",
            companyName = "GC",
            phone = "+361",
            email = "test@example.com",
            signaturePath = "/signature.png",
            stampPath = "/stamp.png",
            signatureTransparency = 12f,
            stampTransparency = 34f,
        )

        repository.save(profile)
        val reloaded = ProfileRepositoryImpl(context, dispatcher)

        assertThat(reloaded.profile.value).isEqualTo(
            profile.copy(
                signatureOriginalPath = "/signature.png",
                stampOriginalPath = "/stamp.png",
            ),
        )
    }

    @Test
    fun saveImageCopiesOriginalAndEditImageMakesWhitePixelsTransparent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = ProfileRepositoryImpl(context, dispatcher)
        val source = temporaryFolder.newFile("signature.png")
        writeTwoPixelBitmap(source)

        val (originalPath, editedPath) = repository.saveImage(Uri.fromFile(source), ProfileImageKind.Signature)
        repository.save(
            UserProfile(
                signatureOriginalPath = originalPath,
                signaturePath = editedPath,
            ),
        )

        val transparentPath = repository.editImage(ProfileImageKind.Signature, tolerance = 5f)
        val edited = BitmapFactory.decodeFile(transparentPath)!!

        assertThat(File(originalPath).isFile).isTrue()
        assertThat(File(editedPath).isFile).isFalse()
        assertThat(File(transparentPath).isFile).isTrue()
        assertThat(Color.alpha(edited.getPixel(0, 0))).isEqualTo(0)
        assertThat(Color.alpha(edited.getPixel(1, 0))).isEqualTo(255)
        edited.recycle()
    }

    @Test
    fun missingForSafetyTemplateReportsOnlyRequiredMissingFields() {
        val missing = UserProfile(
            name = "Név",
            companyName = "",
            signaturePath = "/signature.png",
            stampPath = "",
        ).missingForSafetyTemplate()

        assertThat(missing).containsExactly("cégnév", "bélyegző").inOrder()
    }

    private fun writeTwoPixelBitmap(file: File) {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.WHITE)
        bitmap.setPixel(1, 0, Color.BLACK)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
