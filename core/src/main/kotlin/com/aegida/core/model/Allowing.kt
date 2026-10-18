package com.aegida.core.model

import com.aegida.core.text.Problem
import com.aegida.core.url.LinkSigns
import com.aegida.core.url.Links

/**
 * Whether she may say this warning is fine ("This app is fine", "This link is fine"): the kind allows it ([Problem.allowableFrom]), and
 * a look-alike link names the brand in plain letters. A brand word is a guess Aegida can get wrong; a name in another alphabet or a brand
 * spelled with a number for a letter almost never is, and it is the one a scammer would coach her to allow (findings #155, #156, rows
 * ST34, ST37-ST39). The page and the action both ask here.
 */
fun FindingKey.mayBeAllowed(): Boolean = when {
    problem.allowableFrom != detectorId -> false
    problem == Problem.LookalikeLink -> Links.parse(subject)?.let(LinkSigns::namesBrandPlainly) == true
    else -> true
}
