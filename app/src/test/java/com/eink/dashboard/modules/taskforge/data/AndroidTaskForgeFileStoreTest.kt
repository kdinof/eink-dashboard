package com.eink.dashboard.modules.taskforge.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.eink.dashboard.modules.taskforge.model.TaskSourceRef
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Regression coverage for the on-device crash: MediaDocumentsProvider refuses
 * write opens with IllegalArgumentException("Media is read-only") rather than
 * FileNotFoundException, which used to escape [AndroidTaskForgeFileStore.complete]
 * and kill the app.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidTaskForgeFileStoreTest {

    @Test fun providerThrowingOnWriteOpenIsReadOnlyNotACrash() {
        Robolectric.setupContentProvider(ReadOnlyMediaProvider::class.java, AUTHORITY)
        val store = AndroidTaskForgeFileStore(ApplicationProvider.getApplicationContext())
        val source = TaskSourceRef(lineHash = "irrelevant", occurrence = 0, checkboxByteOffset = 3L)

        val result = store.complete("content://$AUTHORITY/document/2301", source, ' ')

        assertThat(result).isEqualTo(FileCompletionResult.ReadOnly)
    }
}

private const val AUTHORITY = "com.android.providers.media.documents"

class ReadOnlyMediaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if ("w" in mode) throw IllegalArgumentException("Media is read-only")
        val file = File.createTempFile("taskforge", ".md").apply { writeText("- [ ] task") }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "text/markdown"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
