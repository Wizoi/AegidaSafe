package com.aegida.feedtool

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedFormatException
import com.aegida.core.feed.FeedSanity
import com.aegida.core.feed.FeedVerifier
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlin.system.exitProcess

/**
 * build  --domains F --links F [--urlhaus F] [--echap F] [--popular F] --out DIR  writes feed.bin + feed.sig; key from env
 *                                                      AEGIDA_FEED_SIGNING_KEY. --popular is the Majestic Million CSV.
 * verify --feed F --sig F --public F [--previous F]    exits 1 unless the signature matches the public key and the
 *                                                      feed passes the same sanity checks the app applies
 * keygen --out DIR                                     writes feed-public.txt and feed-private.txt (base64)
 *
 * The private key goes only into the GitHub secret AEGIDA_FEED_SIGNING_KEY; never commit it. The public
 * key is built into the app.
 */
fun main(args: Array<String>) {
    val options = args.drop(1).chunked(2).associate { (k, v) -> k.removePrefix("--") to v }
    when (args.firstOrNull()) {
        "build" -> build(options)
        "verify" -> verify(options)
        "keygen" -> keygen(File(options.getValue("out")))
        else -> {
            System.err.println(USAGE)
            exitProcess(2)
        }
    }
}

private fun build(options: Map<String, String>) {
    val keyBase64 = System.getenv("AEGIDA_FEED_SIGNING_KEY") ?: error("AEGIDA_FEED_SIGNING_KEY is not set")
    val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBase64.trim())))
    val (feed, report) = FeedBuilder.build(
        generatedAt = Instant.now(),
        phishingDomains = File(options.getValue("domains")).readText(),
        phishingLinks = File(options.getValue("links")).readText(),
        urlhaus = options["urlhaus"]?.let { File(it).readText() },
        extras = Extras(
            echap = options["echap"]?.let { EchapParser.parse(File(it).readText()) },
            popular = options["popular"]?.let { FeedBuilder.popularDomains(File(it).readText()) },
        ),
    )
    if (report.scamEntries < BuildReport.MIN_SCAM_ENTRIES) fail("only ${report.scamEntries} scam entries; the lists came down short")
    val bytes = FeedCodec.encode(feed)
    val signature = Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(privateKey); update(bytes); sign() }
    val out = File(options.getValue("out")).apply { mkdirs() }
    File(out, "feed.bin").writeBytes(bytes)
    File(out, "feed.sig").writeBytes(signature)
    println("packages=${report.packageEntries} certificates=${report.certificateEntries} popular=${report.popularEntries}")
    println("hosts=${report.hostEntries} urls=${report.urlEntries} unparseable=${report.unparseable} bytes=${bytes.size}")
    println("dropped shared hosts (${report.droppedSharedHosts.size}): ${report.droppedSharedHosts}")
    feed.sources.forEach { println("source ${it.name}: ${it.entries}") }
}

private fun verify(options: Map<String, String>) {
    val bytes = File(options.getValue("feed")).readBytes()
    val signature = File(options.getValue("sig")).readBytes()
    val publicKey = Base64.getDecoder().decode(File(options.getValue("public")).readText().trim())
    if (!FeedVerifier(publicKey).verify(bytes, signature)) fail("signature does not match the public key")
    val feed = try { FeedCodec.decode(bytes) } catch (e: FeedFormatException) { fail("feed is malformed: ${e.message}") }
    val previous = options["previous"]?.let(::File)?.takeIf { it.isFile }?.let {
        try { FeedCodec.decode(it.readBytes()) } catch (ignored: FeedFormatException) { null } // no usable previous feed: the check runs without one
    }
    FeedSanity.check(feed, previous, Instant.now())?.let { fail("sanity check failed: $it") }
    println(
        "ok: ${feed.hostCount} hosts, ${feed.urlCount} urls, ${feed.packageCount} packages, ${feed.certificateCount} certificates, " +
            "generated ${feed.generatedAt}, previous=${previous?.generatedAt}",
    )
}

private const val USAGE = "usage: build --domains F --links F [--urlhaus F] [--echap F] [--popular F] --out DIR | " +
    "verify --feed F --sig F --public F [--previous F] | keygen --out DIR"

private fun fail(message: String): Nothing {
    System.err.println("FEED REJECTED: $message")
    exitProcess(1)
}

private fun keygen(out: File) {
    val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    out.mkdirs()
    File(out, "feed-public.txt").writeText(Base64.getEncoder().encodeToString(pair.public.encoded))
    File(out, "feed-private.txt").writeText(Base64.getEncoder().encodeToString(pair.private.encoded))
    println("wrote ${out.absolutePath}/feed-public.txt and feed-private.txt (keep the private one out of git)")
}
