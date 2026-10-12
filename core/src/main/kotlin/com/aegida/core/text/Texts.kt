package com.aegida.core.text

import com.aegida.core.url.HostMask
import com.aegida.core.url.Links

/**
 * Every sentence the owner or a guardian can see (UserTextTest, CopyRulesTest). Plain words, one idea per line,
 * never red, never "safe": say "no known problems found". Kept in :core so the notification owner in
 * :platform can use it too. A `%s` is filled by the caller (times are formatted for the phone's language).
 */
object Texts {
    const val APP_NAME = "Aegida"
    const val CANT_CHECK = "Aegida can't check this on your phone"
    const val YOU_TOLD_ME = "You told Aegida this is on"

    const val STATUS_NOTICE = "Aegida is looking out for you"
    const val CHANNEL_STATUS = "Aegida is looking out for you"
    const val CHANNEL_NEEDS_TAP = "Needs a tap"

    /** What a guardian reads on their phone. Written in the owner's name; no links, no message text, no "safe". */
    object Guardian {
        const val SET_UP = "Aegida is set up on %s's phone. You will get a message here if it needs help."
        const val STILL_OPEN = "%1\$s hasn't dealt with a warning from Aegida yet. It has been a day. It may be a good time to give %1\$s a call."
        const val STILL_OPEN_URGENT = "%1\$s hasn't dealt with an urgent warning from Aegida yet. It has been a day. Please call %1\$s now."
        const val CONFIRMED = "%1\$s confirmed the change Aegida asked about. Nothing more to do."
        /** For urgent kinds: it could be a scammer coaching the owner, so it never says everything is fine. */
        const val CONFIRMED_URGENT = "%1\$s confirmed the change Aegida asked about. If you were not expecting it, please call %1\$s."
        const val CHANGED = "%1\$s picked a different guardian in Aegida. You will not get messages about %1\$s's phone from now on. If you did not expect this, please call %1\$s."
        /** %1 the owner, %2 the app. Sent when the owner tells Aegida an app is fine (finding #92); the guardian is always told. */
        const val ALLOWED = "%1\$s told Aegida that an app (“%2\$s”) is fine, so Aegida will stop warning about it. If that surprises you, it may be a good time to ask %1\$s."
        /** The same for an app with no name: its package name looks like a web address and is never sent (findings #98). */
        const val ALLOWED_NO_NAME = "%1\$s told Aegida that an app is fine, so Aegida will stop warning about it. If that surprises you, it may be a good time to ask %1\$s."
        const val NOT_CHECKED_IN = "Aegida hasn't checked in on %1\$s's phone for 2 days. The phone may be off or asleep. It may be a good time to call %1\$s."
    }

    object Nav {
        const val HOME = "Home"
        const val HISTORY = "History"
        const val SETTINGS = "My settings"
    }

    object Home {
        const val ALL_GOOD = "Your phone looks good."
        const val NO_KNOWN_PROBLEMS = "No known problems found."
        const val CHECKED_TODAY = "Checked today at %s."
        const val CHECKED_YESTERDAY = "Checked yesterday at %s."
        const val CHECKED_ON = "Checked on %s."
        const val NEEDS_ONE = "1 thing needs a tap"
        const val NEEDS_MANY = "%s things need a tap"
        const val FIX_MY_PHONE = "Fix my phone"
        const val SHOW_ME = "Show me"
        const val NOISY_COUNT = "It sent %s notices that look like ads lately."
        const val NOISY_HOW = "Open its notice settings and switch Allow notifications off. You can also remove the app."
        const val NOISY_TURN_OFF = "Turn off its notices"
        const val NOISY_REMOVE = "Remove this app"
        const val NOISY_FINE = "This app is fine"
        const val ADVICE_FROM = "It came from the %s app."
        const val CAME_IN_TODAY = "It came in today at %s."
        const val CAME_IN_YESTERDAY = "It came in yesterday at %s."
        const val CAME_IN_ON = "It came in on %s."
        const val SENDER_LINE = "From: %s"
        const val SAYS_LINE = "It says: %s"
        const val LINK_REMOVED = "(link)"
        const val SAYS_MAX = 100
        const val SAID_LINE = "It said: %s"
        const val CALL_A_GUARDIAN = "Call a guardian"
        const val COULDNT_CHECK = "I couldn't check today."
        const val COULDNT_CHECK_WHY = "Aegida will try again soon. If this keeps happening, call a guardian."
        const val NEVER_CHECKED = "Aegida hasn't checked your phone yet."
        const val NEVER_CHECKED_WHY = "This takes a minute. Please open Aegida again soon."
        const val LAST_CHECKED = "Last checked %s."
        const val SCAM_ADVICE = "Messages like this are scams. You can delete it."
        const val GOT_IT = "Got it"
        const val CONFIRM_YES = "Yes, that was me"
        const val CONFIRM_APP = "The app is called: %s"
        const val CONFIRM_NO = "No, show me"
        const val CONFIRM_NO_HELP = "If you did not do this, change it back, or call your guardian."

        const val CONFIRM_LABEL_MAX = 40

        /** The one big button on Home: "Fix my phone" only where something really gets fixed; a scam warning just shows what to do. */
        fun fixButton(fix: Fix): String = if (fix == Fix.Acknowledge || fix == Fix.AppNotices) SHOW_ME else FIX_MY_PHONE

        /** "It came from the “Messages” app." for the scam-link advice page, or null when the app is not known. Cleaned like [confirmApp]. */
        fun adviceFrom(label: String?): String? {
            val clean = cleanLabel(label.orEmpty())
            return if (clean.isEmpty()) null else ADVICE_FROM.format("“$clean”")
        }

        /** "From: “Chase Bank”" (the notification's title), or null when there is none. Cleaned and cut short like an app label. */
        fun senderLine(title: String?): String? {
            val clean = cleanLabel(withoutLinks(title.orEmpty()))
            return if (clean.isEmpty()) null else SENDER_LINE.format("“$clean”")
        }

        /**
         * "It says: “...”" with the first words of the message. Every link in it is replaced by "(link)", found by the same code that
         * finds links ([Links]), so a look-alike in another alphabet or a second link is taken out too (findings #94); the rest is
         * cleaned and cut short. [host] is no longer needed and kept for callers.
         */
        fun saysLine(text: String?, host: String?): String? {
            val clean = cleanLabel(withoutLinks(text.orEmpty()), SAYS_MAX)
            return if (clean.isEmpty()) null else SAYS_LINE.format("“$clean”")
        }

        /**
         * [text] for the phone's screens. Invisible characters are removed and dot look-alikes become dots first; then a word that looks
         * like an address at all (see [looksLikeAddress]) shows as "(link)" whole. Only an ordinary word with a dot stays (findings #99-#101).
         */
        fun withoutLinks(text: String): String =
            prepared(text).split(WORD_GAPS).joinToString(" ") { word -> if (looksLikeAddress(word) && !isOrdinaryWord(word)) LINK_REMOVED else word }

        /**
         * An app's label as it may be sent off the phone. After the same preparation, a word that looks like an address is sent as the
         * masks of the addresses found in it, or as `*****` when none can be read; never any part of the word. Then it is cut short.
         */
        fun guardianLabel(raw: String): String =
            cleanLabel(
                cleanLabel(prepared(raw), Int.MAX_VALUE).split(WORD_GAPS).joinToString(" ") { word ->
                    if (!looksLikeAddress(word)) {
                        word
                    } else {
                        Links.findForMasking(word).map { HostMask.of(it.host) }.ifEmpty { listOf(HostMask.of("")) }.joinToString(" ")
                    }
                },
            )

        /**
         * Removes invisible and format characters (zero-width ones, soft hyphen, direction marks: an address split by one is still the
         * address to a browser) and turns every dot look-alike into "." (findings #101).
         */
        private fun prepared(text: String): String {
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    Character.getType(cp) == Character.FORMAT.toInt() || isIgnoredByBrowsers(cp) -> Unit
                    cp in DOT_LOOK_ALIKES -> out.append('.')
                    else -> out.appendCodePoint(cp)
                }
            }
            return out.toString()
        }

        /** Fails closed: a scheme, a dot followed by two letters, an IPv4 address, or anything the link finder sees. */
        private fun looksLikeAddress(word: String): Boolean =
            word.contains("://") || DOT_LETTERS.containsMatchIn(word) || IPV4.containsMatchIn(word) || Links.findForMasking(word).isNotEmpty()

        /**
         * "Mr.Smith", "St.Louis", "held.Please" (no space after a full stop): plain ASCII, two parts, no scheme, path or @, the first a
         * title or a lowercase word, and the ending a capitalised word in no IANA top-level-domain list. Only for what Mom sees.
         */
        private fun isOrdinaryWord(word: String): Boolean {
            val w = word.trim { !it.isLetterOrDigit() }
            if (w.any { it.code > 127 } || w.any { it in "/?@:" }) return false
            val parts = w.split('.')
            if (parts.size != 2) return false
            val (first, ending) = parts
            if (ending.length < 2 || !ending.first().isUpperCase() || !ending.drop(1).all { it.isLowerCase() }) return false
            if (com.aegida.core.url.TopLevelDomains.isDomain(ending)) return false
            return first in TITLES || (first.isNotEmpty() && first.all { it.isLowerCase() })
        }

        private val WORD_GAPS = Regex("""[\s\p{Z}]+""")
        /** A dot and two letters, with any marks between them ("evil.ćom" too, findings #102). */
        private val DOT_LETTERS = Regex("""\.\p{M}*\p{L}(\p{M}*\p{L})+""")

        /** Default-ignorable code points a browser drops from an address (Unicode's list, beyond the format characters). */
        private fun isIgnoredByBrowsers(cp: Int): Boolean =
            cp == 0x034F || cp in 0x115F..0x1160 || cp in 0x17B4..0x17B5 || cp in 0x180B..0x180F || cp == 0x3164 ||
                cp in 0xFE00..0xFE0F || cp == 0xFFA0 || cp in 0xE0000..0xE0FFF
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val TITLES = setOf("Mr", "Mrs", "Ms", "Dr", "St", "Mt", "Jr", "Sr", "Ft")
        private val DOT_LOOK_ALIKES = setOf(
            0x2024, 0xFE52, 0xFE12, 0xFF0E, 0x3002, 0xFF61, 0x00B7, 0x0701, 0x2E3C, 0x2219, 0x22C5, 0x30FB,
            0x2027, 0x0387, 0x16EB, 0x2E31, 0xFF65, 0xA78F, 0x1427, // findings #102
        )

        /** "It said: “…”" for what a website's notification said (its title), cleaned and cut short. */
        fun saidLine(title: String?): String? {
            val clean = cleanLabel(withoutLinks(title.orEmpty()), SAYS_MAX) // a pop-up's title can carry a link too (findings #98)
            return if (clean.isEmpty()) null else SAID_LINE.format("“$clean”")
        }

        /**
         * The "The app is called" line on the "Was that you?" page, or null for no line. An app chooses its own
         * label, so an attacker can make it read like an instruction. For an urgent problem the label is never
         * shown; otherwise it is cut short, stripped of control characters and quoted (R17).
         */
        fun confirmApp(problem: Problem, label: String?): String? {
            if (problem.severity == com.aegida.core.model.Severity.Urgent) return null
            val clean = cleanLabel(label.orEmpty())
            return if (clean.isEmpty()) null else CONFIRM_APP.format("“$clean”")
        }

        fun cleanLabel(raw: String, max: Int = CONFIRM_LABEL_MAX): String {
            val spaced = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val cp = raw.codePointAt(i)
                i += Character.charCount(cp)
                val type = Character.getType(cp)
                val blocked = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() ||
                    type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt()
                spaced.append(if (blocked) " " else String(Character.toChars(cp)))
            }
            val text = spaced.toString().trim().replace(Regex(" {2,}"), " ")
            if (text.codePointCount(0, text.length) <= max) return text
            return text.substring(0, text.offsetByCodePoints(0, max)).trimEnd() + "…"
        }
    }

    object History {
        const val WEBSITE_WAS = "The website was %s."
        const val NOISY = "The %1\$s app sent %2\$s notices that look like ads."
        const val NOISY_NO_COUNT = "The %s app sent notices that look like ads."
        const val APP_IS = "The %s app."
        const val LINK_WENT = "The link went to %s."
        const val TITLE = "History"
        const val EMPTY = "Nothing has happened yet. When Aegida does something for you, it will show here."
        const val KEPT = "Kept on this phone for 90 days."
        const val TODAY = "Today"
        const val YESTERDAY = "Yesterday"
        const val HANDLED = "Handled"
        const val NEEDS_YOU = "Needs you"
    }

    object Common {
        const val CONTINUE = "Continue"
        const val BACK = "Back"
        const val NOT_NOW = "Not now"
        const val SAVE = "Save"
        const val DONE = "Done"
        const val OPEN_SETTINGS = "Open settings"
        const val YOUR_PICTURE = "Your picture: %s"
    }

    object Setup {
        const val WELCOME_TITLE = "Welcome to Aegida"
        const val WELCOME_BODY = "Set up Aegida in about five minutes. It quietly looks out for scams, and keeps you and a guardian you trust aware."
        const val WELCOME_START = "Let's go"
        const val STEP = "Step %s of %s"

        const val ABOUT_TITLE = "Let's set up your helper"
        const val YOUR_NAME = "How shall I address you?"
        const val GUARDIAN_BODY = "Pick one person you trust. We'll call them your guardian."
        const val GUARDIAN_NAME = "Their name"
        const val GUARDIAN_EMAIL = "Their email"
        const val GUARDIAN_PHONE = "Their phone (one tap to call)"

        const val PICTURE_TITLE = "Pick your safety picture"
        const val PICTURE_BODY = "You'll see it every time it's really me. If you don't see it, it isn't me."

        const val INVITE_TITLE = "Tell your guardian"
        const val INVITE_BODY = "I'll write an email to %s in your own email app. You press Send."
        const val INVITE_WRITE = "Write the email"
        const val INVITE_SENT = "I sent it"
        const val INVITE_NO_EMAIL_APP = "This phone has no email app set up. Ask a guardian to help."

        const val ALERTS_TITLE = "Let Aegida tell your guardian about serious problems"
        const val ALERTS_BODY = "For example: \"A website was sending fake warnings to Ruth's phone. Aegida hid them.\" It never sends your messages, calls or where you are."
        const val ALERTS_YES = "Yes, tell my guardian"
        const val ALERTS_NO = "No, I'll tell them myself"

        const val ACCESS_TITLE = "Let Aegida see your notifications"
        const val ACCESS_BODY = "This is how Aegida spots fake warnings and scam links. It does not keep your messages."
        const val ACCESS_PART_1 = "1. Let Aegida see your notifications"
        const val ACCESS_PART_2 = "2. Let Aegida warn you"
        const val ACCESS_FIRST = "Do step 1 first."
        const val ACCESS_OPEN = "Open the notification screen"
        const val ACCESS_ALLOW_BANNERS = "Allow Aegida to warn me"
        const val ACCESS_STEP = "Find Aegida in the list and turn it on."
        const val ACCESS_DONE = "Aegida can see your notifications."
        const val BANNERS_DONE = "Aegida can warn you."
        const val ACCESS_SKIP_TITLE = "Skip this step?"
        const val ACCESS_SKIP_BODY = "Without this, Aegida can't see scam links in your messages. If you chose to tell your guardian about problems, they will be told this is off."
        const val ACCESS_SKIP_BACK = "Go back"
        const val ACCESS_SKIP_ANYWAY = "Skip anyway"

        const val FILTER_TITLE = "Block scam websites"
        const val FILTER_BODY = "Your phone can refuse to open known scam websites. It takes one setting."
        const val FILTER_COPY_AND_OPEN = "Copy the address and open settings"
        const val FILTER_STEPS = "1. Tap the big button below. It copies the address and opens Settings.\n2. Find Private DNS. It is usually under Network or Connections, then More or Advanced.\n3. Choose the hostname option.\n4. Press and hold in the box, tap Paste, and the address is filled in. (It is %s.)\n5. Tap Save, then come back here."
        const val FILTER_ON = "Website protection is on."
        const val FILTER_NOT_YET = "Not on yet. When you're done, come back here."
        const val FILTER_CHECK = "Check it"

        const val BLOCKER_TITLE = "One more switch to try"
        const val BLOCKER_BODY = "Some Samsung phones have a switch called Auto Blocker. It helps stop bad apps. Aegida can't see whether it is on, so it will never say it is."
        const val BLOCKER_SHOW = "Show me"
        const val BLOCKER_IS_ON = "I turned it on"

        const val DONE_TITLE = "All set."
        const val DONE_BODY = "Aegida is looking out for you now. You don't need to do anything."
    }

    object Settings {
        const val TITLE = "My settings"
        const val HOW_IT_WORKS = "How does this work?"
        const val GUARDIAN = "My guardian"
        const val PICTURE = "My safety picture"
        const val NAME = "My first name"
        const val NOT_SET = "Not set"
        const val TELL_GUARDIANS = "Tell my guardian about serious problems"
        const val WEBSITE_PROTECTION = "Website protection"
        const val TURN_ON_WEBSITE_PROTECTION = "Turn on website protection"
        const val ALLOWED_TITLE = "Things you told Aegida are fine"
        const val ALLOWED_REMOVE = "Warn me again"
        const val ON = "On"
        const val OFF = "Off"
        const val CANT_TELL = "Can't tell"
        const val CHANGE = "Change"
        const val CREDITS_TITLE = "Where the lists come from"
        const val LISTS_UPDATED = "Lists updated %s."
        const val LISTS_NOT_YET = "The lists have not been downloaded yet."
        const val CREDITS = "Scam websites: Phishing.Database (MIT license). Spying apps: Stalkerware Indicators by Echap (CC BY 4.0). Both are stored in a shortened, scrambled form."
        const val RESEND_INVITE = "Send the invitation again"
        const val CHANGE_GUARDIAN = "Pick a different guardian"
        const val CHANGE_GUARDIAN_TITLE = "Pick a new guardian"
        const val CHANGE_GUARDIAN_POPUP_TITLE = "Please tell %s"
        const val CHANGE_GUARDIAN_POPUP_BODY = "Aegida will send %s a short message that you changed your guardian. Please tell them yourself too, so they aren't left wondering."
        const val CHANGE_GUARDIAN_POPUP_OK = "I will"
        const val CHANGE_GUARDIAN_POPUP_BACK = "Go back"
    }

    /** The email the owner's phone drafts for her guardian. Filled in by the invite builder. */
    object Invite {
        const val CHOOSE = "Pick your email app"
        const val SUBJECT = "%s has picked you as a guardian"
        const val SUBJECT_NO_NAME = "You have been picked as a guardian"
        const val BODY = "Hi %s,\n\n" +
            "I've picked you as my guardian. If I have switched on “Tell my guardian about serious problems” in my Aegida app, you will get a first message saying it is set up, and then a message if my phone needs help. If it has not come within a day of adding the name, please ask me.\n\n" +
            "1. Get the free ntfy app.\n" +
            "   Android: https://play.google.com/store/apps/details?id=io.heckel.ntfy\n" +
            "   iPhone: https://apps.apple.com/app/ntfy/id1625396347\n\n" +
            "2. Open ntfy, tap +, then copy this name and paste it:\n" +
            "   %s\n" +
            "   (On Android you can instead tap this link once ntfy is installed: ntfy://ntfy.sh/%s)\n\n" +
            "3. Optional, to read them on a computer too: open https://ntfy.sh/%s in a web browser. It only shows messages while that page is open, so it does not replace step 2.\n\n" +
            "Please save this email. The name above is all you need to get my messages on another phone or computer later.\n\n" +
            "Please keep this name private. Anyone who has it can see my messages.\n\n" +
            "This was written by my Aegida app.\n%s"
    }
}
