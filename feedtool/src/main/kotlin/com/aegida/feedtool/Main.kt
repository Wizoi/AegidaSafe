package com.aegida.feedtool

import com.aegida.core.feed.FeedCodec
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
 * build  --domains F --links F --urlhaus F --out DIR   writes feed.bin + feed.sig; key from env AEGIDA_FEED_SIGNING_KEY
 * keygen --out DIR                                    writes feed-public.txt and feed-private.txt (base64)
 *
 * The private key goes only into the GitHub secret AEGIDA_FEED_SIGNING_KEY; never commit it. The public
 * key is built into the app.
 */
fun main(args: Array<String>) {
    val options = args.drop(1).chunked(2).associate { (k, v) -> k.removePrefix("--") to v }
    when (args.firstOrNull()) {
        "build" -> build(options)
        "keygen" -> keygen(File(options.getValue("out")))
        else -> { System.err.println("usage: build --domains F --links F --urlhaus F --out DIR | keygen --out DIR"); exitProcess(2) }
    }
}

private fun build(options: Map<String, String>) {
    val keyBase64 = System.getenv("AEGIDA_FEED_SIGNING_KEY") ?: error("AEGIDA_FEED_SIGNING_KEY is not set")
    val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyBase64.trim())))
    val (feed, report) = FeedBuilder.build(
        generatedAt = Instant.now(),
        phishingDomains = File(options.getValue("domains")).readText(),
        phishingLinks = File(options.getValue("links")).readText(),
        urlhaus = File(options.getValue("urlhaus")).readText(),
    )
    val bytes = FeedCodec.encode(feed)
    val signature = Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(privateKey); update(bytes); sign() }
    val out = File(options.getValue("out")).apply { mkdirs() }
    File(out, "feed.bin").writeBytes(bytes)
    File(out, "feed.sig").writeBytes(signature)
    println("hosts=${report.hostEntries} urls=${report.urlEntries} unparseable=${report.unparseable} bytes=${bytes.size}")
    println("dropped shared hosts (${report.droppedSharedHosts.size}): ${report.droppedSharedHosts}")
    feed.sources.forEach { println("source ${it.name}: ${it.entries}") }
}

private fun keygen(out: File) {
    val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    out.mkdirs()
    File(out, "feed-public.txt").writeText(Base64.getEncoder().encodeToString(pair.public.encoded))
    File(out, "feed-private.txt").writeText(Base64.getEncoder().encodeToString(pair.private.encoded))
    println("wrote ${out.absolutePath}/feed-public.txt and feed-private.txt (keep the private one out of git)")
}
