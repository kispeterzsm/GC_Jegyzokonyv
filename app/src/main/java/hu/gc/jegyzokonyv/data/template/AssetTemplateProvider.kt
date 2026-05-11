package hu.gc.jegyzokonyv.data.template

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssetTemplateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readTemplate(assetPath: String): String {
        return context.assets.open(assetPath).use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        }
    }
}
