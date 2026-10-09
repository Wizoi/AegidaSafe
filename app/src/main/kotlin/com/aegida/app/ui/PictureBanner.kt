package com.aegida.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import com.aegida.core.model.SafetyPicture
import com.aegida.core.store.SetupStore
import com.aegida.platform.notify.BannerArt
import kotlinx.coroutines.runBlocking

/**
 * The Aegida shield with her safety picture faintly behind it, for the large icon on every warning banner.
 * A banner that does not carry her picture did not come from her Aegida. Drawn once per picture and kept.
 */
class PictureBanner(private val context: Context, private val setup: SetupStore) : BannerArt {
    private var cachedId: String? = null
    private var cached: Bitmap? = null

    @Synchronized
    override fun bitmap(): Bitmap? {
        // A small read from the database, taken on the thread that is already posting the banner.
        val id = runBlocking { setup.setup().pictureId }
        val picture = SafetyPicture.fromId(id) ?: return null
        if (id != cachedId) {
            cached = draw(picture)
            cachedId = id
        }
        return cached
    }

    private fun draw(picture: SafetyPicture): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = size.toFloat()
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, Pictures.tileColor(picture)) }
        canvas.drawRoundRect(0f, 0f, s, s, s / 4, s / 4, tile)
        // Her picture, large and faint.
        ContextCompat.getDrawable(context, Pictures.drawable(picture))?.mutate()?.let {
            val inset = (s * 0.1f).toInt()
            it.setBounds(inset, inset, size - inset, size - inset)
            it.alpha = 110
            it.draw(canvas)
        }
        // The shield on top.
        val shield = Path().apply {
            moveTo(s * 0.5f, s * 0.14f)
            lineTo(s * 0.80f, s * 0.26f)
            lineTo(s * 0.80f, s * 0.52f)
            quadTo(s * 0.80f, s * 0.76f, s * 0.5f, s * 0.88f)
            quadTo(s * 0.20f, s * 0.76f, s * 0.20f, s * 0.52f)
            lineTo(s * 0.20f, s * 0.26f)
            close()
        }
        canvas.drawPath(shield, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F6B73.toInt() })
        canvas.drawPath(
            Path().apply {
                moveTo(s * 0.36f, s * 0.50f); lineTo(s * 0.46f, s * 0.60f); lineTo(s * 0.65f, s * 0.38f)
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = s * 0.07f
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            },
        )
        return bitmap
    }
}
