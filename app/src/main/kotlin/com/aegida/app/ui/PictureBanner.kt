package com.aegida.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import androidx.core.content.ContextCompat
import com.aegida.core.model.SafetyPicture
import com.aegida.core.store.SetupStore
import com.aegida.platform.notify.BannerArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Aegida shield with her safety picture faintly behind it, for the large icon on every warning banner.
 * A banner that does not carry her picture did not come from her Aegida.
 *
 * Posting a banner must never wait for the database or the canvas, and may happen on the main thread. So the
 * picture id is kept in memory: read once when this is made (on [scope], off the main thread), changed by
 * [set] when she picks another, and the bitmap is drawn there, never in [bitmap] (R5).
 */
class PictureBanner(
    private val context: Context,
    private val setup: SetupStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BannerArt {
    @Volatile private var cachedId: String? = null
    @Volatile private var cached: Bitmap? = null
    private var chosen = false
    private val loaded = CountDownLatch(1)

    init {
        scope.launch {
            try {
                val stored = setup.setup().pictureId
                synchronized(this@PictureBanner) { if (!chosen) apply(stored) } // a pick made meanwhile wins
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No picture is better than no banner.
            } finally {
                loaded.countDown()
            }
        }
    }

    /** Remembers her picture and draws it. Called when she chooses one; call it off the main thread. */
    @Synchronized
    fun set(id: String?) {
        chosen = true
        apply(id)
    }

    private fun apply(id: String?) {
        val picture = SafetyPicture.fromId(id)
        if (picture == null) {
            cachedId = null
            cached = null
        } else if (id != cachedId) {
            cached = draw(picture)
            cachedId = id
        }
        loaded.countDown()
    }

    /** Never reads the database. Off the main thread, the first few milliseconds after the app starts wait for the first load. */
    override fun bitmap(): Bitmap? {
        if (loaded.count > 0 && Looper.myLooper() != Looper.getMainLooper()) loaded.await(1, TimeUnit.SECONDS)
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
