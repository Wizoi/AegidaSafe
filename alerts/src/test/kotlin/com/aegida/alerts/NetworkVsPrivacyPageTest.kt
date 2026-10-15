package com.aegida.alerts

import com.aegida.core.health.Trigger
import com.aegida.testkit.aScamText
import com.aegida.core.model.PrivateDns
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration

/**
 * Every request a phone makes over a few days of ordinary use, with messages to the guardian off and then on, listed through the
 * HttpClient port (the only way out, NetworkSingleOwnerTest) and compared with what docs/site/privacy.html says is sent.
 * The page is read, never written here.
 */
class NetworkVsPrivacyPageTest {
    private data class Request(val method: String, val host: String, val path: String, val body: String, val headers: Map<String, String>)

    private val requests = mutableListOf<Request>()
    private val topic = NtfyTopic("aegida_" + "pQ9wE8rT".repeat(4))

    /** Wraps the phone's fake network and writes down every request. */
    private inner class Recorder(private val inner: Http) : HttpClient {
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
            requests += Request("POST", endpoint.host, endpoint.path, body, headers)
            return inner.post(endpoint, body, headers)
        }
        override suspend fun delete(endpoint: Endpoint): HttpResult {
            requests += Request("DELETE", endpoint.host, endpoint.path, "", emptyMap())
            return inner.delete(endpoint)
        }
        override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult {
            requests += Request("GET", endpoint.host, endpoint.path, "", emptyMap())
            return inner.get(endpoint, maxBytes)
        }
    }
    private val phone = WholePhone { Recorder(it) }
    private val recorded = phone

    private val page: String by lazy {
        val html = File("../docs/site/privacy.html").readText()
        html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ")
    }

    private val scamText = aScamText(phone.now, text = "USPS: your parcel is held. Pay the fee at https://evil.example/pay now")

    /** First launch, setup with the given answer, then three days: a scam text, a daily check each day, the filter turned off. */
    private suspend fun aFewDays(alertsOn: Boolean) {
        phone.publishFeed()
        recorded.openTheApp()
        phone.aboutAndInvite(topic)
        recorded.guardianAlerts.setAlertsOn(alertsOn)
        phone.listenerEnabled = true
        phone.canPost = true
        phone.dns = PrivateDns.Hostname("security.cloudflare-dns.com")
        phone.finishSetup()
        recorded.runner.run(Trigger.NotificationPosted, scamText)
        repeat(3) {
            phone.now = phone.now.plus(Duration.ofDays(1))
            recorded.daily.run()
        }
        phone.dns = PrivateDns.Off
        recorded.runner.run(Trigger.SettingChanged)
    }

    /** The service the page names for each host the app contacts. */
    private val namedOnThePage = mapOf(Endpoint.NTFY_HOST to "ntfy.sh", Endpoint.FEED_HOST to "GitHub Pages")

    @Test fun everyHostTheAppContactsIsNamedOnThePrivacyPage() {
        for (host in Endpoint.ALLOWED_HOSTS) {
            val name = namedOnThePage[host] ?: error("$host is contacted but this test does not know how the privacy page names it")
            assertTrue("the privacy page must name $name ($host)", page.contains(name))
        }
        assertTrue("$FEED_HOST_IS_GITHUB_PAGES", Endpoint.FEED_HOST.endsWith(".github.io"))
    }

    @Test fun withMessagesOffOnlyTheTwoListFilesAreFetchedAndOneCancelIsSent() = runTest {
        aFewDays(alertsOn = false)
        assertEquals(setOf(Endpoint.FEED_HOST, Endpoint.NTFY_HOST), requests.map { it.host }.toSet())
        assertEquals("nothing is posted to anyone", emptyList<Request>(), requests.filter { it.method == "POST" })
        val gets = requests.filter { it.method == "GET" }
        assertEquals("the list files and nothing else", setOf("Aegida-Feed/feed.bin", "Aegida-Feed/feed.sig"), gets.map { it.path }.toSet())
        assertTrue("a list request names the file and nothing else", gets.all { it.body.isEmpty() && it.headers.isEmpty() && it.host == Endpoint.FEED_HOST })
        val cancels = requests.filter { it.method == "DELETE" }
        assertEquals("one small request to cancel the waiting message, carrying no words", listOf("${topic.value}/heartbeat"), cancels.map { it.path })
        assertTrue(page.contains("one small request to cancel"))
        assertTrue(page.contains("two public files from GitHub Pages"))
    }

    @Test fun withMessagesOnOnlyNtfyGetsMessagesAndTheyCarryNoWholeAddressOrMessageWords() = runTest {
        aFewDays(alertsOn = true)
        assertEquals(setOf(Endpoint.FEED_HOST, Endpoint.NTFY_HOST), requests.map { it.host }.toSet())
        val posts = requests.filter { it.method == "POST" }
        assertTrue(posts.isNotEmpty())
        assertTrue("messages go to ntfy.sh only, under her private name", posts.all { it.host == Endpoint.NTFY_HOST && it.path.startsWith(topic.value) })
        for (post in posts) {
            assertTrue("no whole web address: ${post.body}", !post.body.contains("evil.example") && !post.body.contains("http"))
            assertTrue("no words of her messages: ${post.body}", !post.body.contains("parcel") && !post.body.contains("+1 555"))
            assertTrue("only the title, priority, delay headers: ${post.headers}", post.headers.keys.all { it in setOf("Title", "Priority", "X-Delay") })
        }
        assertTrue("the list files are fetched as with messages off", requests.filter { it.method == "GET" }.all { it.host == Endpoint.FEED_HOST })
        assertTrue(page.contains("It travels through ntfy.sh"))
    }

    private companion object {
        const val FEED_HOST_IS_GITHUB_PAGES = "the list host must be a GitHub Pages site, as the page says"
    }
}
