package com.aegida.platform.notify

import android.graphics.Bitmap

/** The picture on the right of an Aegida banner, or null when there is none. Drawn by :app, which owns the artwork. */
fun interface BannerArt {
    fun bitmap(): Bitmap?
}
