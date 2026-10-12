package com.aegida.core.model

/**
 * The words of a fake warning on a website's notification ("virus", "infected", "your phone is ..."). Too broad for an app's own
 * notices, which have a narrower list in NoticeRules (findings #96).
 */
object ScareWords {
    val REGEX = Regex(
        """(?i)\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery (is )?damaged|security (alert|warning)|clean (your|now|it)|your (phone|device) (is|has been)""",
    )
}
