plugins {
    base // gives the root project check/build, which the test-time budget below hangs on
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.detekt) apply false
}

// detekt on every Kotlin module (not the fixture app), with no baseline: every issue fails the build (Kevin, Oct 14: fix what is
// found). A rule setting changes in config/detekt/detekt.yml only for a strong reason, written there.
subprojects {
    if (name == "noisyfixture") return@subprojects
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }
}

// Test-time budget (code-health item 7, CLAUDE.md "Check per-class test times"). Reads the JUnit XML that the
// test tasks wrote, after they ran. It WARNS past the budget (suite 5 min of summed class time, one class 60 s) and
// FAILS only at twice that, so a cold machine (first Robolectric load, empty caches) cannot fail a build by itself
// while a real slowdown still does. It skips quietly when no results exist.
val testTimeBudget = tasks.register("testTimeBudget") {
    group = "verification"
    description = "Warns when the unit suite passes 5 minutes or a test class passes 60 s; fails at twice that."
    val resultDirs = subprojects.map { it.layout.buildDirectory.dir("test-results").get().asFile }
    val warnSuiteSec = 300.0
    val warnClassSec = 60.0
    val failFactor = 2.0
    outputs.upToDateWhen { false }
    doLast {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        val perClass = mutableMapOf<String, Double>()
        resultDirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach { xml ->
                val suite = factory.newDocumentBuilder().parse(xml).documentElement
                if (suite.tagName == "testsuite") {
                    val key = "${dir.parentFile.parentFile.name}:${suite.getAttribute("name")}"
                    perClass.merge(key, suite.getAttribute("time").toDoubleOrNull() ?: 0.0, Double::plus)
                }
            }
        }
        if (perClass.isEmpty()) {
            logger.lifecycle("testTimeBudget: no test results found; nothing to check")
            return@doLast
        }
        val total = perClass.values.sum()
        val slowest = perClass.maxByOrNull { it.value }!!
        logger.lifecycle(
            "testTimeBudget: %.0f s summed over %d classes (warn %.0f s, fail %.0f s); slowest %s %.1f s (warn %.0f s, fail %.0f s)".format(
                total, perClass.size, warnSuiteSec, warnSuiteSec * failFactor, slowest.key, slowest.value, warnClassSec, warnClassSec * failFactor,
            ),
        )
        perClass.filter { it.value > warnClassSec }.forEach { logger.warn("WARNING: test class ${it.key} took %.1f s (budget %.0f s)".format(it.value, warnClassSec)) }
        if (total > warnSuiteSec) logger.warn("WARNING: unit suite took %.0f s summed (budget %.0f s)".format(total, warnSuiteSec))
        val tooSlow = perClass.filter { it.value > warnClassSec * failFactor }
        if (total > warnSuiteSec * failFactor || tooSlow.isNotEmpty()) {
            throw GradleException(
                "Test time budget broken: suite %.0f s (fails past %.0f s), slow classes %s (fail past %.0f s each)".format(
                    total, warnSuiteSec * failFactor, tooSlow.keys, warnClassSec * failFactor,
                ),
            )
        }
    }
}
subprojects { tasks.withType<Test>().configureEach { testTimeBudget.configure { mustRunAfter(this@configureEach) } } }
tasks.named("check") { dependsOn(testTimeBudget) }
