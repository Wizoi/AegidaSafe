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
        /** 57 characters: no I, l, 1, O or 0, so a guardian typing the name cannot pick the wrong one (findings #110). Names already given out are never re-checked against this. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        private const val RANDOM_LENGTH = 40

        /** At most 12 letters of her name, so the whole name stays inside ntfy's 64 characters (12 + "-aegida-" + 40 = 60). */
        private const val NAME_LETTERS = 12

        /** Letters that do not come apart into a plain letter and an accent, written the usual plain way. */
        private val PLAIN_FORMS = mapOf(
            'ß' to "ss", 'ø' to "o", 'Ø' to "O", 'æ' to "ae", 'Æ' to "Ae", 'œ' to "oe", 'Œ' to "Oe", 'ł' to "l", 'Ł' to "L",
            'đ' to "d", 'Đ' to "D", 'ð' to "d", 'Ð' to "D", 'þ' to "th", 'Þ' to "Th", 'ı' to "i",
        )
        /** Titles skipped before her first name, with or without a dot (finding #163, Kevin Oct 19). None is a usual first name; "Sr" is left out as it is also a name ending. */
        private val TITLES = setOf("dr", "mr", "mrs", "ms", "miss", "mx", "prof", "rev", "sir", "dame", "fr")
        private val MARKS = Regex("\\p{M}+")
        private val WORD_BREAK = Regex("[\\p{Z}\\s]+")

        /**
         * A new channel name (finding #162): her first name, "-aegida-", then 40 random letters and digits without look-alikes
         * (57 characters, about 233 bits), so a guardian who follows several channels in ntfy sees whose it is. With no
         * name that can be written in plain letters it is "aegida-" and the 40. Pass a SecureRandom; the default is one.
         */
        fun generate(ownerName: String, random: java.util.Random = java.security.SecureRandom()): NtfyTopic {
            val secret = String(CharArray(RANDOM_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
            val name = plainFirstName(ownerName)
            return NtfyTopic(if (name.isEmpty()) "aegida-$secret" else "$name-aegida-$secret")
        }

        /**
         * The first word of her name that has a letter, accents taken off, in plain letters A-Z and a-z only (no digits, so
         * no 0 or 1 next to an O or l), first letter raised, at most [NAME_LETTERS]. Empty when any letter in it has no plain
         * form (another alphabet, or one look-alike letter mixed in), so the name is never spelled half or wrong.
         */
        private fun plainFirstName(ownerName: String): String {
            val word = ownerName.split(WORD_BREAK).firstOrNull { w -> w.any { it.isLetter() } && w.trimEnd('.').lowercase() !in TITLES }.orEmpty()
            val plain = MARKS.replace(java.text.Normalizer.normalize(word, java.text.Normalizer.Form.NFKD), "")
                .map { PLAIN_FORMS[it] ?: it.toString() }.joinToString("")
            // Code points, not chars: a letter outside the basic range is two chars, neither a letter alone (findings #163).
            if (plain.codePoints().anyMatch { Character.isLetter(it) && it !in 'A'.code..'Z'.code && it !in 'a'.code..'z'.code }) return ""
            return plain.filter { it in 'A'..'Z' || it in 'a'..'z' }.take(NAME_LETTERS).replaceFirstChar { it.uppercaseChar() }
        }
    }
}
