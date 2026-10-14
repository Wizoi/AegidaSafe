package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.PrivateDns
import com.aegida.core.text.Problem
import javax.inject.Inject

/** Owns "is the website filter the owner set up still on?". Nothing to say before she sets it up. */
class DnsDetector @Inject constructor() : Detector {
    override val id = DetectorId.Dns

    override fun evaluate(input: Inputs): List<Finding> {
        val chosen = input.setup.websiteFilterHost ?: return emptyList()
        val unchanged = input.snapshot.privateDns == PrivateDns.Hostname(chosen)
        val finding = Finding(FindingKey(id, Problem.WebsiteFilterOff, chosen), mapOf(Evidence.HOST to chosen), input.now, input.now)
        return if (unchanged) emptyList() else listOf(finding)
    }
}
