package com.aegida.core.model

/**
 * The words of a fake warning ("virus", "infected", "your phone is ..."). One list for the website pop-up check and the notice
 * ad-spam check (findings #93), so the two cannot drift apart.
 */
object ScareWords {
    val REGEX = Regex(
        """(?i)\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery (is )?damaged|security (alert|warning)|clean (your|now|it)|your (phone|device) (is|has been)""",
    )
}
