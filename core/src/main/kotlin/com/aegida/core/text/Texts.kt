package com.aegida.core.text

/**
 * Every sentence Mom or a guardian can see (UserTextTest, CopyRulesTest). Plain words, one idea per line,
 * never red, never "safe": say "no known problems found". Kept in :core so the notification owner in
 * :platform can use it too. A `%s` is filled by the caller (times are formatted for the phone's language).
 */
object Texts {
    const val APP_NAME = "Aegida"
    const val CANT_CHECK = "Aegida can't check this on your phone"
    const val YOU_TOLD_ME = "You told Aegida this is on"

    const val STATUS_NOTICE = "Aegida is protecting you"
    const val CHANNEL_STATUS = "Aegida is protecting you"
    const val CHANNEL_NEEDS_TAP = "Needs a tap"

    /** What a guardian reads on their phone. Written in Mom's name; no links, no message text, no "safe". */
    object Guardian {
        const val SET_UP = "Aegida is set up on %s's phone. You will get a message here if it needs help."
        const val NOT_CHECKED_IN = "Aegida hasn't checked in on %s's phone for 2 days. The phone may be off or asleep. Please call her."
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
        const val CALL_A_GUARDIAN = "Call a guardian"
        const val COULDNT_CHECK = "I couldn't check today."
        const val COULDNT_CHECK_WHY = "Aegida will try again soon. If this keeps happening, call a guardian."
        const val NEVER_CHECKED = "Aegida hasn't checked your phone yet."
        const val NEVER_CHECKED_WHY = "This takes a minute. Please open Aegida again soon."
        const val LAST_CHECKED = "Last checked %s."
        const val SCAM_ADVICE = "Messages like this are scams. You can delete it."
        const val GOT_IT = "Got it"
    }

    object History {
        const val TITLE = "History"
        const val EMPTY = "Nothing has happened yet. When Aegida does something for you, it will show here."
        const val KEPT = "Kept on this phone for 90 days."
        const val TODAY = "TODAY"
        const val YESTERDAY = "YESTERDAY"
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
        const val STEP = "STEP %s OF %s"

        const val ABOUT_TITLE = "Let's set up your helper"
        const val ABOUT_BODY = "Aegida watches for scams on your phone. It also tells someone you trust if something serious happens."
        const val YOUR_NAME = "Your first name"
        const val GUARDIAN_TITLE = "Who should we tell?"
        const val GUARDIAN_BODY = "Pick one person you trust. We call them your guardian."
        const val GUARDIAN_NAME = "Their name"
        const val GUARDIAN_EMAIL = "Their email"
        const val GUARDIAN_PHONE = "Their phone number (so you can call them)"

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
        const val ACCESS_OPEN = "Open the notification screen"
        const val ACCESS_ALLOW_BANNERS = "Allow Aegida to warn me"
        const val ACCESS_STEP = "Find Aegida in the list and turn it on."
        const val ACCESS_DONE = "Aegida can see your notifications."
        const val BANNERS_DONE = "Aegida can warn you."

        const val FILTER_TITLE = "Block scam websites"
        const val FILTER_BODY = "Your phone can refuse to open known scam websites. It takes one setting."
        const val FILTER_STEPS = "1. Tap Open settings.\n2. Tap Private DNS.\n3. Choose the hostname choice.\n4. Type: %s\n5. Tap Save."
        const val FILTER_ON = "Website protection is on."
        const val FILTER_NOT_YET = "Not on yet. When you're done, come back here."
        const val FILTER_CHECK = "Check it"

        const val BLOCKER_TITLE = "One more switch to try"
        const val BLOCKER_BODY = "Some Samsung phones have a switch called Auto Blocker. It helps stop bad apps. Aegida can't tell if it's on, so it will take your word."
        const val BLOCKER_SHOW = "Show me"
        const val BLOCKER_IS_ON = "I turned it on"

        const val DONE_TITLE = "All set."
        const val DONE_BODY = "Aegida is watching now. You don't need to do anything."
    }

    object Settings {
        const val TITLE = "My settings"
        const val GUARDIAN = "My guardian"
        const val PICTURE = "My safety picture"
        const val NAME = "My first name"
        const val NOT_SET = "Not set"
        const val TELL_GUARDIANS = "Tell my guardian about serious problems"
        const val WEBSITE_PROTECTION = "Website protection"
        const val ON = "On"
        const val OFF = "Off"
        const val CANT_TELL = "Can't tell"
        const val CHANGE = "Change"
        const val GUARDIANS_NOTIFIED = "Changing your guardian tells your current guardian."
    }

    /** The email Mom's phone drafts for her guardian. Filled in by the invite builder. */
    object Invite {
        const val SUBJECT = "%s has picked you as a guardian"
        const val BODY = "Hi %s,\n\n" +
            "I've picked you as my guardian. You'll get a message if my phone needs help.\n\n" +
            "1. Get the free ntfy app.\n" +
            "   Android: https://play.google.com/store/apps/details?id=io.heckel.ntfy\n" +
            "   iPhone: https://apps.apple.com/app/ntfy/id1625396347\n\n" +
            "2. Open this link on your phone to get my messages:\n" +
            "   https://ntfy.sh/%s\n" +
            "   (Or open ntfy, tap +, and type this name: %s)\n\n" +
            "Please keep this link private. Anyone who has it can see my messages.\n\n" +
            "This was written by my Aegida app.\n%s"
    }
}
