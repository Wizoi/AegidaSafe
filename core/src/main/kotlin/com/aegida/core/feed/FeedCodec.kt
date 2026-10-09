package com.aegida.core.feed

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant

/**
 * feed.bin layout (big-endian): "AEGF", version byte, generatedAt epoch seconds (long),
 * source count (int) then per source name/licence (UTF) and entries (int), host count (int) + hashes,
 * URL count (int) + hashes. Version 2 adds package-name count (int) + hashes and signing-certificate count (int) +
 * hashes after that; version 1 feeds still read, with no app lists. feed.sig is an ECDSA P-256 / SHA-256
 * signature of the whole feed.bin.
 */
object FeedCodec {
    private const val MAGIC = 0x41454746 // "AEGF"
    private const val VERSION = 2
    private const val MAX_ENTRIES = 5_000_000

    fun encode(feed: ThreatFeed): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            out.writeLong(feed.generatedAt.epochSecond)
            out.writeInt(feed.sources.size)
            feed.sources.forEach { out.writeUTF(it.name); out.writeUTF(it.licence); out.writeInt(it.entries) }
            out.writeInt(feed.hosts.size); feed.hosts.forEach(out::writeLong)
            out.writeInt(feed.urls.size); feed.urls.forEach(out::writeLong)
            out.writeInt(feed.packages.size); feed.packages.forEach(out::writeLong)
            out.writeInt(feed.certificates.size); feed.certificates.forEach(out::writeLong)
        }
    }.toByteArray()

    /** Throws [FeedFormatException] for anything that is not a well-formed version 1 or 2 feed. */
    fun decode(bytes: ByteArray): ThreatFeed = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) throw FeedFormatException("not an Aegida feed")
            val version = input.readUnsignedByte()
            if (version != 1 && version != VERSION) throw FeedFormatException("unsupported feed version $version")
            val generatedAt = Instant.ofEpochSecond(input.readLong())
            val sources = List(input.readInt().checked(16)) { FeedSource(input.readUTF(), input.readUTF(), input.readInt()) }
            val hosts = LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() }
            val urls = LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() }
            val packages = if (version >= 2) LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() } else LongArray(0)
            val certificates = if (version >= 2) LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() } else LongArray(0)
            if (input.read() != -1) throw FeedFormatException("trailing bytes")
            ThreatFeed(generatedAt, sources, hosts, urls, packages, certificates)
        }
    } catch (e: IOException) {
        throw FeedFormatException("truncated feed")
    }

    private fun Int.checked(max: Int): Int = if (this in 0..max) this else throw FeedFormatException("bad count $this")
}

class FeedFormatException(message: String) : Exception(message)

/** Checks feed.sig against the public key built into the app. The private key lives only in CI. */
class FeedVerifier(publicKeyX509: ByteArray) {
    private val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyX509))

    fun verify(feedBytes: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance(ALGORITHM).run {
            initVerify(key)
            update(feedBytes)
            verify(signature)
        }
    } catch (e: java.security.SignatureException) {
        false
    }

    companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}

/** Why a downloaded feed is not used; the last good one stays (design section 6). */
enum class FeedRejection { BadSignature, Malformed, TooSmall, FromTheFuture, OlderThanCurrent, ShrankTooMuch, TooManyEntries }

object FeedSanity {
    private const val MIN_ENTRIES = 1_000

    /** Echap lists a few thousand spying apps; anything near this is a broken or hostile feed (R18). */
    private const val MAX_APP_ENTRIES = 100_000

    /** The app lists are only compared for shrinking once the current feed has at least this many of them. */
    private const val APP_SHRINK_FLOOR = 100

    private fun shrankTooMuch(candidate: Int, current: Int) = current >= APP_SHRINK_FLOOR && candidate < current / 2

    /** Null when [candidate] may replace [current]. */
    fun check(candidate: ThreatFeed, current: ThreatFeed?, now: Instant): FeedRejection? {
        val size = candidate.hostCount + candidate.urlCount
        return when {
            size < MIN_ENTRIES -> FeedRejection.TooSmall
            candidate.packageCount > MAX_APP_ENTRIES || candidate.certificateCount > MAX_APP_ENTRIES -> FeedRejection.TooManyEntries
            candidate.generatedAt.isAfter(now.plus(Duration.ofHours(1))) -> FeedRejection.FromTheFuture
            current != null && !candidate.generatedAt.isAfter(current.generatedAt) -> FeedRejection.OlderThanCurrent
            current != null && size < (current.hostCount + current.urlCount) / 2 -> FeedRejection.ShrankTooMuch
            current != null && shrankTooMuch(candidate.packageCount, current.packageCount) -> FeedRejection.ShrankTooMuch
            current != null && shrankTooMuch(candidate.certificateCount, current.certificateCount) -> FeedRejection.ShrankTooMuch
            else -> null
        }
    }
}
