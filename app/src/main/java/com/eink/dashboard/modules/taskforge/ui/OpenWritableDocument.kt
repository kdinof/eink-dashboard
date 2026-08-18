package com.eink.dashboard.modules.taskforge.ui

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * [ActivityResultContracts.OpenDocument] that starts the system picker inside
 * the device's writable storage root (Internal storage → Documents) instead of
 * the default "Recents"/media view. A pick from the media provider can never be
 * written back — MediaDocumentsProvider throws on write opens — so completion
 * only works when the same file is picked through ExternalStorageProvider.
 */
class OpenWritableDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, DOCUMENTS_ROOT),
        )

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val DOCUMENTS_ROOT = "primary:Documents"
    }
}
