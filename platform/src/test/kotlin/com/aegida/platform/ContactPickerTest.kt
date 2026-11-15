package com.aegida.platform

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.aegida.core.alert.ContactPick
import com.aegida.core.alert.PickedContact
import com.aegida.platform.contacts.ContactPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Release B, T4.2-T4.6, T4.18-T4.21: the phone's own contact list, one picked row, no contacts permission. */
@RunWith(RobolectricTestRunner::class)
class ContactPickerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val row = Uri.parse("content://com.android.contacts/data/phones/7")

    /** A contacts provider that returns what a test says and records what it was asked for. */
    class FakeContacts : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? {
            asked += (projection ?: emptyArray()).toList()
            return when (mode) {
                "null" -> null
                "badcolumn" -> { require(uri.pathSegments.contains("phones")) { "Invalid column data1" }; rowCursor() }
                "denied" -> throw SecurityException("work profile")
                "empty" -> MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                else -> rowCursor()
            }
        }
        private fun rowCursor() = MatrixCursor(arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            .also { it.addRow(arrayOf(number, "Sam")) }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        companion object {
            var mode = "row"
            var number = "+1 555 010 0199"
            val asked = mutableListOf<String>()
        }
    }

    private fun provide(mode: String, number: String = "+1 555 010 0199") {
        FakeContacts.mode = mode
        FakeContacts.number = number
        FakeContacts.asked.clear()
        Robolectric.buildContentProvider(FakeContacts::class.java).create("com.android.contacts")
    }

    @Test fun t4_2_aPickedRowGivesItsNumberAndNameAndAsksForNothingElse() {
        provide("row")
        val result = ContactPicker.read(context, row)
        assertEquals(ContactPick.Picked(PickedContact("+1 555 010 0199", "Sam")), result)
        assertEquals(setOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), FakeContacts.asked.toSet())
    }

    @Test fun t4_3_backingOutIsCancelledAndTouchesNothing() {
        provide("row")
        assertEquals(ContactPick.Cancelled, ContactPicker.read(context, null))
        assertEquals(emptyList<String>(), FakeContacts.asked)
    }

    @Test fun t4_18_aContactWhoseNumberIsAStarCodeIsNoNumber() {
        provide("row", "*86")
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, row))
    }

    @Test fun t4_19_aProviderThatThrowsSecurityExceptionIsNoNumberNeverACrash() {
        provide("denied")
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, row))
    }

    @Test fun t4_19_aNullCursorAndAnEmptyCursorAreNoNumber() {
        provide("null")
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, row))
        provide("empty")
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, row))
    }

    @Test fun t4_19_aUriThatIsNotAContactsRowIsNoNumber() {
        provide("row")
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, Uri.parse("content://elsewhere/anything")))
    }

    @Test fun t4_19_aRealContactsContactsRowNotAPhoneRowIsNoNumberNeverACrash() {
        provide("badcolumn")
        val contactRow = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, 7)
        assertEquals(ContactPick.NoNumber, ContactPicker.read(context, contactRow))
    }

    @Test fun t4_2_theIntentPicksAPhoneNumberRowAndNeedsNoPermission() {
        val intent = ContactPicker.pickIntent()
        assertEquals(Intent.ACTION_PICK, intent.action)
        assertEquals(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, intent.data)
    }

    @Test fun t4_21_noContactListOnThePhoneMeansNoPickerAndAContactListMeansOne() {
        assertFalse(ContactPicker.isAvailable(context))
        val found = android.content.pm.ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.example.contacts"; name = "Pick"; exported = true }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(ContactPicker.pickIntent(), found)
        assertTrue(ContactPicker.isAvailable(context))
    }
}
