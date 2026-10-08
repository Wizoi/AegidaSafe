package com.aegida.guards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each guard must catch a real violation, or it passes silently forever. Every rule is fed a bad
 * snippet in a disallowed place (must be flagged) and, where it has an owner, the same snippet in the
 * owner's file (must pass). Snippets that look like secrets are assembled at runtime so this file
 * does not trip NoSecretsTest itself.
 */
class GuardSelfTest {
    private val k = "src/main/kotlin/com/aegida"

    private fun flags(rule: Rule, path: String, text: String) =
        assertTrue("${rule.name} missed: $text", rule.violationsIn(listOf(SourceFile(path, text))).isNotEmpty())

    private fun allows(rule: Rule, path: String, text: String) =
        assertEquals("${rule.name} wrongly flagged $path", emptyList<String>(), rule.violationsIn(listOf(SourceFile(path, text))))

    @Test fun clock() {
        flags(Guards.clock, "detectors/$k/detectors/X.kt", "val t = System.currentTimeMillis()")
        flags(Guards.clock, "alerts/$k/alerts/X.kt", "val t = Instant.now()")
        allows(Guards.clock, "platform/$k/platform/time/SystemClock.kt", "val t = Instant.now()")
        allows(Guards.clock, "detectors/$k/detectors/X.kt", "// not Instant.now() in a comment")
    }

    @Test fun network() {
        flags(Guards.network, "app/$k/app/X.kt", "import okhttp3.OkHttpClient")
        flags(Guards.network, "app/$k/app/X.kt", "URL(link).openConnection()")
        flags(Guards.network, "app/$k/app/X.kt", "val v = WebView(context)")
        allows(Guards.network, "platform/$k/platform/net/OkHttpHttpClient.kt", "import okhttp3.OkHttpClient")
    }

    @Test fun ntfy() {
        flags(Guards.ntfyEndpoint, "app/$k/app/X.kt", "http.post(Endpoint.Ntfy(topic), body)")
        allows(Guards.ntfyEndpoint, "alerts/$k/alerts/NtfySender.kt", "http.post(Endpoint.Ntfy(topic), body)")
        flags(Guards.ntfyCallers, "app/$k/app/X.kt", "class Y(val s: NtfySender)")
        allows(Guards.ntfyCallers, "alerts/$k/alerts/AlertPipeline.kt", "class AlertPipeline(val s: NtfySender)")
    }

    @Test fun sql() {
        flags(Guards.sql, "detectors/$k/detectors/X.kt", "import androidx.room.Dao")
        allows(Guards.sql, "store/$k/store/X.kt", "import androidx.room.Dao")
        flags(Guards.destructiveMigration, "store/$k/store/X.kt", ".fallbackToDestructiveMigration()")
    }

    @Test fun notifications() {
        flags(Guards.notifications, "app/$k/app/X.kt", "NotificationCompat.Builder(context, id)")
        flags(Guards.notifications, "app/$k/app/X.kt", "manager.notify(1, n)")
        allows(Guards.notifications, "platform/$k/platform/notify/NotificationPoster.kt", "manager.notify(1, n)")
    }

    @Test fun oem() {
        flags(Guards.oem, "detectors/$k/detectors/X.kt", "if (Build.MANUFACTURER == \"x\")")
        flags(Guards.oem, "app/$k/app/X.kt", "Intent(\"com.samsung.android.sm.ACTION\")")
        allows(Guards.oem, "platform/$k/platform/oem/AndroidOemSource.kt", "Build.MANUFACTURER == \"samsung\"")
    }

    @Test fun layers() {
        flags(Guards.androidInPureModules, "core/$k/core/X.kt", "import android.content.Context")
        flags(Guards.androidInPureModules, "detectors/$k/detectors/X.kt", "import androidx.work.Worker")
        allows(Guards.androidInPureModules, "platform/$k/platform/X.kt", "import android.content.Context")
        flags(Guards.appMakesNoDecisions, "app/$k/app/X.kt", "if (finding.severity == Severity.Urgent)")
        flags(Guards.onlyDetectorsCreateFindings, "alerts/$k/alerts/X.kt", "val f = Finding(key, s, e, a, b)")
        allows(Guards.onlyDetectorsCreateFindings, "detectors/$k/detectors/X.kt", "val f = Finding(key, s, e, a, b)")
        flags(Guards.onlyDetectorsCreateFindings, "store/$k/store/OtherStore.kt", "val f = Finding(key, s, e, a, b)")
        allows(Guards.onlyDetectorsCreateFindings, "store/$k/store/RoomStores.kt", "val f = Finding(key, s, e, a, b)")
    }

    @Test fun bannedCapabilities() {
        flags(Guards.bannedApis, "app/src/main/AndroidManifest.xml", "<uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\"/>")
        flags(Guards.bannedApis, "app/$k/app/X.kt", "class Spy : AccessibilityService()")
        flags(Guards.bannedApis, "app/$k/app/X.kt", "builder.setFullScreenIntent(pi, true)")
    }

    @Test fun copyRules() {
        flags(Guards.bannedWords, "core/$k/core/text/Texts.kt", "const val OK = \"Your phone is safe\"")
        flags(Guards.bannedWords, "app/src/main/res/values/strings.xml", "<string name=\"x\">Secure browsing</string>")
        flags(Guards.bannedWords, "core/$k/core/text/Texts.kt", "const val P = \"You are protected from scams\"")
        allows(Guards.bannedWords, "core/$k/core/text/Texts.kt", "const val P = \"Pick your safety picture\"")
        flags(Guards.noRed, "app/$k/app/ui/X.kt", "color = Color.Red")
    }

    @Test fun userText() {
        flags(Guards.noRawErrorText, "app/$k/app/X.kt", "Text(e.localizedMessage)")
        flags(Guards.noRawErrorText, "alerts/$k/alerts/X.kt", "body = e.message")
        flags(Guards.noLiteralUiText, "app/$k/app/ui/X.kt", "Text(\"Hello\")")
        allows(Guards.noLiteralUiText, "app/$k/app/ui/X.kt", "Text(Texts.APP_NAME)")
    }

    @Test fun deadColumnReadPattern() {
        // Same pattern as NoDeadColumnsTest: a named-argument write is not a read; any other mention is.
        val read = { name: String, code: String -> Regex("""\b$name\b(?!\s*=(?!=))""").containsMatchIn(code) }
        assertTrue(!read("finishedAtMillis", "CheckRunEntity(finishedAtMillis = run.finishedAt?.toEpochMilli())"))
        assertTrue(read("finishedAtMillis", "finishedAt = finishedAtMillis?.let(Instant::ofEpochMilli)"))
        assertTrue(read("finishedAtMillis", "if (row.finishedAtMillis == null) x"))
    }

    @Test fun secrets() {
        val googleKey = "AI" + "za" + "Sy" + "A".repeat(33)
        flags(Guards.secrets, "app/$k/app/X.kt", "val key = \"$googleKey\"")
        flags(Guards.secrets, "local.properties", "auth" + "_key=\"" + "abcdef0123456789abcd" + "\"")
        flags(Guards.secrets, "x.pem", "-----BEGIN " + "RSA PRIVATE KEY-----")
        flags(Guards.buildConfigSecrets, "app/build.gradle.kts", "buildConfigField(\"String\", \"KEY\", key)")
    }
}
