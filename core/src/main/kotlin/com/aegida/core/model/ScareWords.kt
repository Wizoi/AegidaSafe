package com.aegida.core.model

/**
 * The words of a fake warning on a website's notification ("virus", "infected", "your phone is ..."). Too broad for an app's own
 * notices, which have a narrower list in NoticeRules (findings #96).
 */
object ScareWords {
    val REGEX = Regex(
        """(?i)\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery (is )?damaged|security (alert|warning)""" +
            """|clean (your|now|it)|your (phone|device) (is|has been)""",
    )

    /**
     * Scare wording aimed at her own phone, not a news story about someone else (findings #109). "Found" counts when nothing follows
     * it but a stop, "on" or an order to her ("3 viruses found! Remove now"), not "found in 40 apps"; "your <any phone's name> is
     * infected" covers every maker without naming one (findings #119).
     */
    val ABOUT_HER_PHONE = Regex(
            """(?i)\byour (phone|device|mobile|android|battery)\b|\bthis (phone|device)\b|battery (is )?damaged|clean (your|now|it)|""" +
                """\b(virus(es)?|malware|trojans?|spyware) (detected\b|found( on\b|\s*[!.:]|\s*$|\s+(tap|remove|clean|delete|fix|scan)\b))|""" +
                """\bremove (it |them )?(now|immediately)\b|\byour [\w-]+( [\w-]+)? (is|has been) infected\b|""" +
                """\byou('re| are| have been|'ve been) (infected|hacked)\b""",
    )
}
