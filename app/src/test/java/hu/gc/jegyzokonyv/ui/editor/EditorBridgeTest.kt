package hu.gc.jegyzokonyv.ui.editor

import android.webkit.JavascriptInterface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorBridgeTest {
    @Test
    fun javascriptBridgeExposesOnlySaveAndFocusCallbacks() {
        val bridgeClass = Class.forName("hu.gc.jegyzokonyv.ui.editor.EditorBridge")
        val exposedMethods = bridgeClass.declaredMethods
            .filter { it.getAnnotation(JavascriptInterface::class.java) != null }
            .map { it.name }

        assertThat(exposedMethods).containsExactly("save", "editableFocused")
        assertThat(bridgeClass.getDeclaredMethod("save", Int::class.javaPrimitiveType, String::class.java).returnType)
            .isEqualTo(Void.TYPE)
        assertThat(bridgeClass.getDeclaredMethod("editableFocused", Boolean::class.javaPrimitiveType).returnType)
            .isEqualTo(Void.TYPE)
        assertThat(bridgeClass.getDeclaredMethod("attach", android.webkit.WebView::class.java, Int::class.javaObjectType)
            .getAnnotation(JavascriptInterface::class.java))
            .isNull()
    }
}
