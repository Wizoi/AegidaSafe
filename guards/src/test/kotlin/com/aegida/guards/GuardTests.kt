package com.aegida.guards

import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// One class per single-owner rule in design section 4, so a failure names the rule.

class ShippedSourcesTest {
    /** An empty scan passes every guard; make sure each module was actually read. */
    @Test fun everyModuleHasShippedSources() {
        val byModule = ShippedSources.main.groupBy { it.module }
        ShippedSources.modules.forEach { module ->
            assertTrue("no shipped sources found for :$module under ${ShippedSources.root}", byModule[module].orEmpty().isNotEmpty())
        }
    }
}

class ClockSingleOwnerTest {
    @Test fun timeComesOnlyFromClock() = assertNoViolations(Guards.clock)
}

class NetworkSingleOwnerTest {
    @Test fun networkOnlyThroughHttpClient() = assertNoViolations(Guards.network)

    @Test fun everyRequestNamesAnEndpointNeverAFreeFormUrl() {
        HttpClient::class.java.declaredMethods.forEach { method ->
            assertEquals("${method.name} must take an Endpoint first", Endpoint::class.java, method.parameterTypes.first())
        }
    }

    /** Changing this list changes the Play data-safety form; update both together. */
    @Test fun allowedHostsAreExactlyTheKnownOnes() {
        assertEquals(setOf("ntfy.sh", "wizoi.github.io"), Endpoint.ALLOWED_HOSTS)
    }
}

class NtfySingleOwnerTest {
    @Test fun onlyNtfySenderBuildsNtfyEndpoints() = assertNoViolations(Guards.ntfyEndpoint)
    @Test fun onlyAlertPipelineCallsNtfySender() = assertNoViolations(Guards.ntfyCallers)
}

class SqlSingleOwnerTest {
    @Test fun sqlOnlyInStore() = assertNoViolations(Guards.sql)
    @Test fun noDestructiveMigration() = assertNoViolations(Guards.destructiveMigration)

    @Test fun everySchemaVersionIsExported() {
        val db = ShippedSources.main.single { it.path.endsWith("/AegidaDatabase.kt") }
        val version = Regex("""version\s*=\s*(\d+)""").find(db.code)!!.groupValues[1].toInt()
        val dir = File(ShippedSources.root, "store/schemas/com.aegida.store.AegidaDatabase")
        (1..version).forEach { v ->
            assertTrue("missing exported schema $v.json in $dir (build :store and commit it)", File(dir, "$v.json").isFile)
        }
    }
}

class NotificationSingleOwnerTest {
    @Test fun notificationsOnlyThroughNotificationPoster() = assertNoViolations(Guards.notifications)
}

class OemSingleOwnerTest {
    @Test fun makerSpecificCodeOnlyInOemSource() = assertNoViolations(Guards.oem)
    @Test fun theMakersNameOnlyInOemSourceAndTheCopyFile() = assertNoViolations(Guards.oemWordInCopy)
}

class LayerTest {
    @Test fun pureModulesHaveNoAndroidImports() = assertNoViolations(Guards.androidInPureModules)
    @Test fun appMakesNoDecisions() = assertNoViolations(Guards.appMakesNoDecisions)
    @Test fun onlyDetectorsCreateFindings() = assertNoViolations(Guards.onlyDetectorsCreateFindings)

    /** Imports go down only. Matches the layer list in settings.gradle.kts and CLAUDE.md. */
    @Test fun moduleDependenciesPointDownOnly() {
        val allowed = mapOf(
            "core" to emptySet(),
            "detectors" to setOf("core"),
            "alerts" to setOf("detectors", "core"),
            "platform" to setOf("core"),
            "store" to setOf("core"),
            "app" to setOf("alerts", "detectors", "platform", "store", "core"),
            "guards" to setOf("core"),
            "feedtool" to setOf("core"),
        )
        val projectRef = Regex("""project\(":(\w+)"\)""")
        ShippedSources.buildFiles.filter { it.path.contains('/') }.forEach { file ->
            val deps = projectRef.findAll(file.code).map { it.groupValues[1] }.toSet()
            val extra = deps - allowed.getValue(file.module)
            assertTrue(":${file.module} depends upward or sideways on $extra", extra.isEmpty())
        }
    }
}

class ManifestSourcePolicyTest {
    /** The merged-manifest check is ManifestPolicyTest in :app; this one catches code and source manifests. */
    @Test fun noBannedCapabilitiesInCodeOrManifests() = assertNoViolations(Guards.bannedApis)
}

class CopyRulesTest {
    @Test fun neverSaySafe() = assertNoViolations(Guards.bannedWords)
    @Test fun neverRed() = assertNoViolations(Guards.noRed)
}

class UserTextTest {
    @Test fun noRawExceptionText() = assertNoViolations(Guards.noRawErrorText)
    @Test fun noLiteralUiText() = assertNoViolations(Guards.noLiteralUiText)
}

class NoSecretsTest {
    @Test fun noSecretsAnywhereInTheRepo() = assertNoViolations(Guards.secrets, ShippedSources.allText)
    @Test fun nothingInjectedIntoBuildConfig() = assertNoViolations(Guards.buildConfigSecrets, ShippedSources.buildFiles)
}

/**
 * SmugView's dead-column lesson: every Room column must be read somewhere outside its entity, either as
 * `.column` in :store code or in a @Query. Approximate (matches by name), so keep column names distinct.
 */
class NoDeadColumnsTest {
    @Test fun everyColumnIsRead() {
        val store = ShippedSources.main.filter { it.module == "store" && it.path.endsWith(".kt") }
        val entity = Regex("""@Entity[^)]*\)\s*data class (\w+)\((.*?)\n\)""", RegexOption.DOT_MATCHES_ALL)
        val column = Regex("""val (\w+)\s*:""")
        val queries = store.flatMap { f -> Regex("""@Query\("([^"]*)"\)""").findAll(f.code).map { it.groupValues[1] }.toList() }
        val entities = store.flatMap { f -> entity.findAll(f.code).map { f to it }.toList() }
        assertTrue("no @Entity found in :store; the scan is broken", entities.isNotEmpty())
        val dead = entities.flatMap { (file, match) ->
            column.findAll(match.groupValues[2]).map { it.groupValues[1] }.filter { name ->
                // A read is any mention except a named-argument write ("name = ..."), so receiver-less reads
                // inside an extension count (findings #25) but a column that is only ever written does not.
                val readInCode = store.any { it !== file && Regex("""\b$name\b(?!\s*=(?!=))""").containsMatchIn(it.code) }
                val readInSql = queries.any { Regex("""\b$name\b""").containsMatchIn(it) }
                !readInCode && !readInSql
            }.map { "${match.groupValues[1]}.$it" }.toList()
        }
        assertEquals("columns written but never read", emptyList<String>(), dead)
    }
}
