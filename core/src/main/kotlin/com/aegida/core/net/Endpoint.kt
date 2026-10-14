package com.aegida.core.net

/**
 * Every host Aegida may contact. Built only from constants and values Aegida generated itself (the
 * ntfy topic), never from message or notification text.
 */
sealed class Endpoint(val host: String, val path: String) {
    val url: String get() = "https://$host/$path"

    /**
     * A guardian alert topic on ntfy.sh, optionally with a sequence id: publishing again to the same
     * id replaces a message still waiting to be delivered, and a DELETE on it cancels that message.
     * Constructed only by NtfySender (NtfySingleOwnerTest).
     */
    class Ntfy(topic: NtfyTopic, sequenceId: String? = null) : Endpoint(NTFY_HOST, ntfyPath(topic, sequenceId))

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
        private val SEQUENCE_ID = Regex("[A-Za-z0-9_-]{1,64}")

        /** Hosts the app may contact. NetworkSingleOwnerTest fails if this changes without the test. */
        val ALLOWED_HOSTS = setOf(NTFY_HOST, FEED_HOST)

        private fun ntfyPath(topic: NtfyTopic, sequenceId: String?): String {
            if (sequenceId == null) return topic.value
            require(SEQUENCE_ID.matches(sequenceId)) { "sequence id must be 1 to 64 letters, digits, _ or -" }
            return "${topic.value}/$sequenceId"
        }
    }
}

/** A long random topic generated on this phone; the topic name is the password. */
@JvmInline
value class NtfyTopic(val value: String) {
    init {
        require(value.length >= MIN_LENGTH && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
            "topic must be at least 32 letters, digits, _ or -"
        }
    }

    companion object {
        private const val MIN_LENGTH = 32
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val RANDOM_LENGTH = 40

        /** "aegida_" plus 40 random letters and digits (about 238 bits). Pass a SecureRandom; the default is one. */
        fun generate(random: java.util.Random = java.security.SecureRandom()): NtfyTopic =
            NtfyTopic("aegida_" + String(CharArray(RANDOM_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))
    }
}
