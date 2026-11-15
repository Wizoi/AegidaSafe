package com.aegida.platform.contacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.aegida.core.alert.ContactPick

/**
 * The phone's own contact list, opened to pick one phone number (T4). Android gives Aegida read access to the one row picked and no
 * more, so no contacts permission is held or asked for (the manifest test bans READ_CONTACTS). Only NUMBER and DISPLAY_NAME are read.
 */
object ContactPicker {
    private val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

    fun pickIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    /** Whether some app can show the list (QUERY_ALL_PACKAGES is held, so this sees them all); with none, no button is drawn (T4.21). */
    fun isAvailable(context: Context): Boolean = pickIntent().resolveActivity(context.packageManager) != null

    /**
     * The row [picked] points at. Null means she backed out. A row that cannot be read for any reason (another app's address, a work
     * profile, no row, a failing provider) is [ContactPick.NoNumber]: she is told and can type it, and nothing crashes (T4.19).
     */
    fun read(context: Context, picked: Uri?): ContactPick {
        if (picked == null) return ContactPick.Cancelled
        if (picked.authority != ContactsContract.AUTHORITY) return ContactPick.NoNumber
        return try {
            context.contentResolver.query(picked, columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContactPick.fromRow(cursor.getString(0), cursor.getString(1))
                } else {
                    ContactPick.NoNumber
                }
            } ?: ContactPick.NoNumber
        } catch (ignored: RuntimeException) {
            // SecurityException, IllegalArgumentException, a dead provider: no reason shown, only "type it below".
            ContactPick.NoNumber
        }
    }
}
