package com.aegida.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.aegida.core.alert.ContactPick
import com.aegida.core.text.Texts
import com.aegida.platform.contacts.ContactPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens the phone's contact list and reports what she picked. Tests put their own in [LocalContactPickerUi]. */
interface ContactPickerUi {
    /** False when no app on the phone can show the list: then there is no button (T4.21). */
    val available: Boolean
    fun open(onResult: (ContactPick) -> Unit)
}

val LocalContactPickerUi = staticCompositionLocalOf<ContactPickerUi?> { null }

/** "Pick from my contacts": nothing at all when the phone has no contact list. The result is read off the main thread. */
@Composable
fun PickFromContacts(onPicked: (ContactPick) -> Unit) {
    val picker = LocalContactPickerUi.current ?: rememberSystemContactPicker()
    if (picker.available) QuietButton(Texts.Setup.PICK_CONTACT, { picker.open(onPicked) })
}

@Composable
private fun rememberSystemContactPicker(): ContactPickerUi {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf<((ContactPick) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = waiting
        waiting = null
        if (callback != null) {
            val row = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
            scope.launch { callback(withContext(Dispatchers.IO) { ContactPicker.read(context, row) }) }
        }
    }
    return remember(context) {
        object : ContactPickerUi {
            override val available = ContactPicker.isAvailable(context)

            override fun open(onResult: (ContactPick) -> Unit) {
                waiting = onResult
                try {
                    launcher.launch(ContactPicker.pickIntent())
                } catch (ignored: ActivityNotFoundException) {
                    waiting = null // the list went away between drawing the button and the tap: she can still type it
                } catch (ignored: SecurityException) {
                    waiting = null
                }
            }
        }
    }
}
