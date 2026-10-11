package com.aegida.core.notice

import java.time.Instant

/** The counted notices per app for the last week, kept on the phone by :store. Counts and signals only, never words. */
interface NoticeLog {
    /**
     * Adds one notice. The same notice coming back ([key] and its own time [whenMillis] both the same, as after a snooze) is not new,
     * and the same [key] again within [NoticeRules.SAME_NOTICE] is an update of it. The key is kept only as a hash.
     */
    suspend fun record(packageName: String, key: String, whenMillis: Long, at: Instant, signals: Int)

    suspend fun since(packageName: String, from: Instant): List<NoticeRow>

    /** Drops what is older than [before] or later than [notAfter] (a clock that was ahead); returns how many. */
    suspend fun prune(before: Instant, notAfter: Instant): Int

    /** Keeps nothing; for code and tests that do not look at notices. */
    object None : NoticeLog {
        override suspend fun record(packageName: String, key: String, whenMillis: Long, at: Instant, signals: Int) = Unit
        override suspend fun since(packageName: String, from: Instant): List<NoticeRow> = emptyList()
        override suspend fun prune(before: Instant, notAfter: Instant): Int = 0
    }
}

/** One thing the owner said is fine, shown in My settings with a way to take it back. */
data class AllowedItem(val problem: com.aegida.core.text.Problem, val subject: String, val label: String, val at: Instant)

/** What the owner has told Aegida to leave alone. Removing an item makes Aegida warn about it again. */
interface AllowList {
    suspend fun add(item: AllowedItem)
    suspend fun remove(problem: com.aegida.core.text.Problem, subject: String)
    suspend fun all(): List<AllowedItem>

    /** Allows nothing; for code and tests that do not look at it. */
    object None : AllowList {
        override suspend fun add(item: AllowedItem) = Unit
        override suspend fun remove(problem: com.aegida.core.text.Problem, subject: String) = Unit
        override suspend fun all(): List<AllowedItem> = emptyList()
    }
}
