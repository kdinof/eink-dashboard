package com.eink.dashboard.modules.taskforge.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenWritableDocumentTest {

    @Test fun pickerStartsInsideTheWritableStorageRoot() {
        val intent = OpenWritableDocument().createIntent(
            ApplicationProvider.getApplicationContext(),
            arrayOf("text/markdown", "text/plain"),
        )

        assertThat(intent.action).isEqualTo(Intent.ACTION_OPEN_DOCUMENT)
        assertThat(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
            .asList().containsExactly("text/markdown", "text/plain")
        val initial = intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI)
        assertThat(initial.toString())
            .isEqualTo("content://com.android.externalstorage.documents/document/primary%3ADocuments")
    }
}
