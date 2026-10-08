package com.aegida.guards

import org.junit.Assert.fail

/**
 * A banned pattern and the only paths allowed to contain it. Rules live here once so the guard tests
 * and GuardSelfTest (which proves each rule catches a violation) use the same definition.
 */
class Rule(
    val name: String,
    val pattern: Regex,
    val why: String,
    private val allowedPrefixes: List<String> = emptyList(),
    private val onlyIn: (SourceFile) -> Boolean = { true },
    private val rawText: Boolean = false,
) {
    fun violationsIn(files: List<SourceFile>): List<String> = files
        .filter { onlyIn(it) && allowedPrefixes.none(it.path::startsWith) }
        .flatMap { file ->
            val text = if (rawText) file.text else file.code
            pattern.findAll(text).map { match ->
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                "${file.path}:$line  '${match.value.trim()}'"
            }
        }
}

fun assertNoViolations(rule: Rule, files: List<SourceFile> = ShippedSources.main) {
    val found = rule.violationsIn(files)
    if (found.isNotEmpty()) {
        fail("${rule.name}: ${rule.why}\n" + found.joinToString("\n") { "  $it" })
    }
}

private const val KT = "src/main/kotlin/com/aegida"
private fun inModules(vararg modules: String): (SourceFile) -> Boolean = { it.module in modules }

object Guards {
    val clock = Rule(
        "ClockSingleOwner",
        Regex("""System\.currentTimeMillis|System\.nanoTime|Instant\.now\(|(LocalDate|LocalDateTime|ZonedDateTime|OffsetDateTime)\.now\(|java\.time\.Clock|SystemClock\.(elapsedRealtime|uptimeMillis)|\bDate\(\)"""),
        "read time only through com.aegida.core.time.Clock; the real clock is platform/time/SystemClock",
        allowedPrefixes = listOf("platform/$KT/platform/time/"),
    )

    val network = Rule(
        "NetworkSingleOwner",
        Regex("""okhttp3|HttpURLConnection|openConnection\(|\bjava\.net\.(URL|Socket)\b|\bURL\(|retrofit2|io\.ktor|android\.webkit|\bWebView\b|DownloadManager"""),
        "all network goes through HttpClient with an Endpoint; never fetch a URL from a message or notification",
        allowedPrefixes = listOf("platform/$KT/platform/net/"),
    )

    val ntfyEndpoint = Rule(
        "NtfySingleOwner",
        Regex("""Endpoint\.Ntfy\(|"ntfy\.sh""""),
        "only NtfySender builds an ntfy endpoint; the host constant lives in core Endpoint.kt",
        allowedPrefixes = listOf("alerts/$KT/alerts/NtfySender.kt", "core/$KT/core/net/Endpoint.kt"),
    )

    val ntfyCallers = Rule(
        "NtfySingleOwner (callers)",
        Regex("""\bNtfySender\b"""),
        "only the alert classes send to guardians (AlertPipeline builds the message from a Finding; the others send fixed texts or retry queued ones)",
        allowedPrefixes = listOf("alerts/$KT/alerts/NtfySender.kt", "alerts/$KT/alerts/AlertPipeline.kt", "alerts/$KT/alerts/DeadMansSwitch.kt", "alerts/$KT/alerts/GuardianAlerts.kt", "alerts/$KT/alerts/OutboxFlusher.kt"),
    )

    val sql = Rule(
        "SqlSingleOwner",
        Regex("""androidx\.room|android\.database|SQLiteDatabase|SupportSQLite|rawQuery|execSQL|androidx\.sqlite"""),
        "SQL and Room live only in :store",
        allowedPrefixes = listOf("store/"),
    )

    val destructiveMigration = Rule(
        "NoDestructiveMigration",
        Regex("""fallbackToDestructiveMigration"""),
        "Room migrations are explicit; a destructive fallback wipes Mom's baseline and history",
    )

    val notifications = Rule(
        "NotificationSingleOwner",
        Regex("""NotificationCompat|Notification\.Builder|\bNotificationChannel\b|createNotificationChannel|NotificationManagerCompat|\.notify\("""),
        "channels and posted notifications are owned by platform/notify/NotificationPoster",
        allowedPrefixes = listOf("platform/$KT/platform/notify/"),
    )

    /** Maker-specific code: package names, intent actions, manufacturer checks. Only OemSource may hold these. */
    val oem = Rule(
        "OemSingleOwner",
        Regex("""com\.sec\.|com\.samsung\.|\bone ?ui\b|\bBuild\.(MANUFACTURER|BRAND)\b"""),
        "maker-specific code lives only in platform/oem (OemSource) and returns NotPresent/Unreadable",
        allowedPrefixes = listOf("platform/$KT/platform/oem/"),
    )

    /** The plain word in the owner-facing copy ("Some Samsung phones have...") is not code; only Texts.kt may use it (findings #34). */
    val oemWordInCopy = Rule(
        "OemSingleOwner (words)",
        Regex("""(?i)samsung"""),
        "the word may appear only in the copy file core/.../text/Texts.kt (and OemSource)",
        allowedPrefixes = listOf("platform/$KT/platform/oem/", "core/$KT/core/text/Texts.kt"),
    )

    val androidInPureModules = Rule(
        "Layer: pure modules",
        Regex("""import\s+(android|androidx|dagger\.hilt)\."""),
        ":core, :detectors and :alerts are pure Kotlin so their logic runs as fast JVM tests",
        onlyIn = inModules("core", "detectors", "alerts"),
    )

    val appMakesNoDecisions = Rule(
        "Layer: :app makes no decisions",
        Regex("""\bSeverity\b|\bVerdict\b|\bFinding\("""),
        ":app wires and draws; severity and verdict decisions belong to detectors/alerts and HomeState.of",
        onlyIn = inModules("app"),
    )

    val onlyDetectorsCreateFindings = Rule(
        "Only detectors create Findings",
        Regex("""\bFinding\("""),
        "one owner for 'is something wrong?': only :detectors constructs a Finding",
        // :store only rebuilds findings detectors already made, from their saved rows (findings #26).
        allowedPrefixes = listOf("detectors/", "core/$KT/core/model/Finding.kt", "store/$KT/store/RoomStores.kt"),
    )

    val bannedApis = Rule(
        "Banned capabilities",
        Regex("""AccessibilityService|VpnService|TYPE_APPLICATION_OVERLAY|setFullScreenIntent|ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS|MediaRecorder|AudioRecord|Telephony\.Sms|SmsManager|BIND_ACCESSIBILITY_SERVICE|BIND_VPN_SERVICE|READ_SMS|RECEIVE_SMS|RECORD_AUDIO|SYSTEM_ALERT_WINDOW|USE_FULL_SCREEN_INTENT|REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"""),
        "no accessibility service, SMS, call recording, VPN, overlays, full-screen intents or direct battery-exemption request",
    )

    /** Any string literal (Kotlin) or element text (XML) with a word Aegida must never say. */
    val bannedWords = Rule(
        "CopyRules",
        Regex("""(?i)"[^"\n]*\b(safe|safely|safest|unsafe|secure|secured|protected from)\b[^"\n]*"|>[^<]*\b(safe|safely|safest|unsafe|secure|secured|protected from)\b[^<]*<"""),
        "never say \"safe\" (or \"secure\", \"protected from\"); say \"no known problems found\"",
    )

    val noRed = Rule(
        "CopyRules: never red",
        Regex("""Color\.Red|0x[fF]{2}[fF][fF0-9]0{4}\b|#[fF][fF]0000"""),
        "no red anywhere on Mom's screens",
        onlyIn = inModules("app"),
    )

    val noRawErrorText = Rule(
        "UserText: no raw exception text",
        Regex("""localizedMessage|stackTraceToString|printStackTrace|\b(e|t|it|ex|error|exception|throwable|cause)\.message\b"""),
        "every sentence Mom or a guardian sees comes from Texts/Problem, never an exception",
    )

    val noLiteralUiText = Rule(
        "UserText: no literal UI text",
        Regex("""\bText\(\s*"|Toast\.makeText\([^,]+,\s*""""),
        "UI text comes from com.aegida.core.text.Texts",
        onlyIn = inModules("app"),
    )

    private val SECRET = Regex(
        listOf(
            """AIza[0-9A-Za-z_\-]{35}""", // Google API key
            """-----BEGIN [A-Z ]*PRIVATE KEY-----""",
            """\bgh[pousr]_[A-Za-z0-9]{36}\b""", // GitHub token
            """\bxox[baprs]-[A-Za-z0-9-]{10,}""", // Slack token
            """\bsk-[A-Za-z0-9_\-]{32,}""",
            """\bAKIA[0-9A-Z]{16}\b""", // AWS key id
            """"type"\s*:\s*"service_account"""",
            """(?i)(api[_-]?key|auth[_-]?key|secret|token|passw(or)?d)["']?\s*[:=]\s*["'][A-Za-z0-9_\-+/]{16,}["']""",
        ).joinToString("|"),
    )

    val secrets = Rule(
        "NoSecrets",
        SECRET,
        "no keys in source or the APK; feed keys live only on the sync proxy",
        rawText = true,
    )

    val buildConfigSecrets = Rule(
        "NoSecrets (BuildConfig)",
        Regex("""buildConfigField|resValue\("""),
        "BuildConfig/resValue is how keys get into an APK; Aegida has none to put there",
        rawText = true,
    )
}
