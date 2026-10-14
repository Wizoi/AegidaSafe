package com.aegida.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
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
            } catch (ignored: Exception) {
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
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        val s = SIZE.toFloat()
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, Pictures.tileColor(picture)) }
        canvas.drawRoundRect(0f, 0f, s, s, s / CORNER_DIVISOR, s / CORNER_DIVISOR, tile)
        drawPicture(canvas, picture)
        drawShield(canvas, s)
        return bitmap
    }

    /** Her picture, large and faint. */
    private fun drawPicture(canvas: Canvas, picture: SafetyPicture) {
        ContextCompat.getDrawable(context, Pictures.drawable(picture))?.mutate()?.let {
            val inset = (SIZE * PICTURE_INSET).toInt()
            it.setBounds(inset, inset, SIZE - inset, SIZE - inset)
            it.alpha = PICTURE_ALPHA
            it.draw(canvas)
        }
    }

    /** The shield on top, with its check. */
    private fun drawShield(canvas: Canvas, s: Float) {
        val shield = Path().apply {
            moveTo(ShieldTop, s)
            lineTo(ShieldRightShoulder, s)
            lineTo(ShieldRightSide, s)
            quadTo(ShieldRightCurve, ShieldBottom, s)
            quadTo(ShieldLeftCurve, ShieldLeftSide, s)
            lineTo(ShieldLeftShoulder, s)
            close()
        }
        canvas.drawPath(shield, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHIELD_COLOUR })
        canvas.drawPath(
            Path().apply { moveTo(TickStart, s); lineTo(TickBend, s); lineTo(TickEnd, s) },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TICK_COLOUR; style = Paint.Style.STROKE; strokeWidth = s * TICK_WIDTH
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            },
        )
    }

    private companion object {
        const val SIZE = 192
        const val CORNER_DIVISOR = 4
        const val PICTURE_INSET = 0.1f
        const val PICTURE_ALPHA = 110
        const val TICK_WIDTH = 0.07f
        val SHIELD_COLOUR = 0xFF0F6B73.toInt()
        val TICK_COLOUR = 0xFFFFFFFF.toInt()
    }
}

/** A point in the unit square, scaled by the bitmap's size when drawn. */
private class UnitPoint(val x: Float, val y: Float)

private val ShieldTop = UnitPoint(0.5f, 0.14f)
private val ShieldRightShoulder = UnitPoint(0.80f, 0.26f)
private val ShieldRightSide = UnitPoint(0.80f, 0.52f)
private val ShieldRightCurve = UnitPoint(0.80f, 0.76f)
private val ShieldBottom = UnitPoint(0.5f, 0.88f)
private val ShieldLeftCurve = UnitPoint(0.20f, 0.76f)
private val ShieldLeftSide = UnitPoint(0.20f, 0.52f)
private val ShieldLeftShoulder = UnitPoint(0.20f, 0.26f)
private val TickStart = UnitPoint(0.36f, 0.50f)
private val TickBend = UnitPoint(0.46f, 0.60f)
private val TickEnd = UnitPoint(0.65f, 0.38f)

private fun Path.moveTo(p: UnitPoint, s: Float) = moveTo(p.x * s, p.y * s)
private fun Path.lineTo(p: UnitPoint, s: Float) = lineTo(p.x * s, p.y * s)
private fun Path.quadTo(control: UnitPoint, end: UnitPoint, s: Float) = quadTo(control.x * s, control.y * s, end.x * s, end.y * s)
