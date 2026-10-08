package com.aegida.core.net

/**
 * Every host Aegida may contact. Built only from constants and values Aegida generated itself (the
 * ntfy topic), never from message or notification text.
 */
sealed class Endpoint(val host: String, val path: String) {
    val url: String get() = "https://$host/$path"

    /** A guardian alert topic on ntfy.sh. Constructed only by NtfySender (NtfySingleOwnerTest). */
    class Ntfy(topic: NtfyTopic) : Endpoint(NTFY_HOST, topic.value)

    /** One of Aegida's own published scam-list files (the Aegida-Feed Pages site). The only two instances are below. */
    class Feed private constructor(file: String) : Endpoint(FEED_HOST, "$FEED_PATH/$file") {
        companion object {
            val LIST = Feed("feed.bin")
            val SIGNATURE = Feed("feed.sig")
        }
    }

    companion object {
        const val NTFY_HOST = "ntfy.sh"
        const val FEED_HOST = "wizoi.github.io"
        private const val FEED_PATH = "Aegida-Feed"

        /** Hosts the app may contact. NetworkSingleOwnerTest fails if this changes without the test. */
        val ALLOWED_HOSTS = setOf(NTFY_HOST, FEED_HOST)
    }
}

/** A long random topic generated on this phone; the topic name is the password. */
@JvmInline
value class NtfyTopic(val value: String) {
    init {
        require(value.length >= 32 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            "topic must be at least 32 letters, digits, _ or -"
        }
    }
}
