package com.aegida.core.model

import com.aegida.core.text.Problem
import com.aegida.core.url.LinkSign
import com.aegida.core.url.LinkSigns
import com.aegida.core.url.Links

/**
 * Whether she may say this warning is fine ("This app is fine", "This link is fine"): the kind allows it ([Problem.allowableFrom]), and
 * a look-alike link is not a name that mixes alphabets. A brand word is a guess Aegida can get wrong; a Latin name with a Cyrillic letter in
 * it almost never is, and it is the one a scammer would coach her to allow (findings #155, row ST34). The page and the action both ask here.
 */
fun FindingKey.mayBeAllowed(): Boolean = when {
    problem.allowableFrom != detectorId -> false
    problem == Problem.LookalikeLink -> Links.parse(subject)?.let { LinkSign.MixedScript !in LinkSigns.of(it) } == true
    else -> true
}
