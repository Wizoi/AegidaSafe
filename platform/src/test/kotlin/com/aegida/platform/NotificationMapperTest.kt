package com.aegida.platform

import android.app.Notification
import android.app.Person
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aegida.platform.notify.NotificationMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class NotificationMapperTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val postedAt = Instant.now().minus(Duration.ofMinutes(2))

    private fun builder(channel: String? = "shell_cmd") =
        (if (channel == null) Notification.Builder(context) else Notification.Builder(context, channel))
            .setSmallIcon(android.R.drawable.ic_dialog_info)

    private fun map(n: Notification, key: String = "k", pkg: String = "p") =
        NotificationMapper.toEvent(key, pkg, n, postedAt.toEpochMilli())

    @Test fun theRealChromeSiteNotificationKeepsItsChannelTitleAndTextOnce() { // as posted on the emulator, findings #18
        val channel = "web:http://localhost:8765;13435952006713133"
        val text = "Tap now to clean your device (Aegida test fixture)"
        val n = builder(channel).setContentTitle("VIRUS DETECTED! Your phone is infected").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).build()
        val event = map(n, key = "0|com.android.chrome|-1|p#http://localhost:8765/#1aegida-fixture|10150", pkg = "com.android.chrome")
        assertEquals(channel, event.channelId)
        assertEquals("VIRUS DETECTED! Your phone is infected", event.title)
        assertEquals(text, event.text) // body and long body are the same text: once, not twice
        assertEquals("com.android.chrome", event.packageName)
        assertEquals(postedAt.toEpochMilli(), event.postedAt.toEpochMilli())
    }

    /** Finding #92: what the sending app says it is, and what the phone does with it, so ad-like notices can be told from real ones. */
    @Test fun theCategoryAndSteadyFlagsAreRead() {
        val promo = builder().setContentTitle("Deal").setCategory(Notification.CATEGORY_PROMO).build()
        assertEquals("promo", map(promo).category)
        assertEquals(false, map(promo).ongoing)
        val player = builder().setContentTitle("Playing").setOngoing(true).build()
        assertEquals(true, map(player).ongoing)
        val service = builder().setContentTitle("Syncing").build().apply { flags = flags or Notification.FLAG_FOREGROUND_SERVICE }
        assertEquals(true, map(service).ongoing)
        assertEquals(null, map(builder().setContentTitle("Hi").build()).category)
    }

    @Test fun whatThePhoneKnowsAboutTheNoticeIsPassedThrough() {
        val facts = com.aegida.platform.notify.RankingFacts(channelName = "Special offers", channelDescription = "Deals", importance = 4, conversation = false, sentimentNegative = true)
        val event = NotificationMapper.toEvent("k", "p", builder().setContentTitle("Hi").build(), postedAt.toEpochMilli(), facts)
        assertEquals("Special offers", event.channelName)
        assertEquals("Deals", event.channelDescription)
        assertEquals(4, event.importance)
        assertEquals(true, event.sentimentNegative)
        assertEquals("without it the notice reads as plain", 0, map(builder().setContentTitle("Hi").build()).importance)
    }

    @Test fun aLongBodyAndTheShortOneAreBothKept() {
        val n = builder().setContentTitle("Bank").setContentText("Your account is locked")
            .setStyle(Notification.BigTextStyle().bigText("Your account is locked. Verify at examp1e-bank.test/login")).build()
        assertEquals("Your account is locked\nYour account is locked. Verify at examp1e-bank.test/login", map(n).text)
    }

    @Test fun chatMessagesAreReadSoALinkInTheLatestOneIsSeen() {
        val sender = Person.Builder().setName("Delivery").build()
        val style = Notification.MessagingStyle(Person.Builder().setName("Me").build())
            .addMessage("Hi, your parcel is held", postedAt.toEpochMilli(), sender)
            .addMessage("Pay the fee at usps-redelivery.example/track", postedAt.toEpochMilli(), sender)
        val event = map(builder().setContentTitle("Delivery").setStyle(style).build())
        // Android decides the order; what matters is that every message is read.
        assertEquals(setOf("Hi, your parcel is held", "Pay the fee at usps-redelivery.example/track"), event.text.lines().toSet())
    }

    @Test fun inboxLinesAreRead() {
        val style = Notification.InboxStyle().addLine("one example.org").addLine("two bad.example")
        assertEquals("one example.org\ntwo bad.example", map(builder().setContentTitle("2 messages").setStyle(style).build()).text)
    }

    @Test fun aNotificationWithNothingToReadIsEmptyTextNotACrash() {
        val event = map(builder(channel = null).build())
        assertEquals("", event.title)
        assertEquals("", event.text)
        assertEquals(null, event.channelId)
    }
}
