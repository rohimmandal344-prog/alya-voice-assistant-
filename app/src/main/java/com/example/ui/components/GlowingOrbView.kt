package com.example.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.sin

/**
 * GlowingOrbView (Alya v2.2.0)
 * 
 * Hardware-accelerated glowing spherical orb component matching the screenshot.
 * Renders a semi-translucent luminous misty sphere with multi-layer radial glow,
 * breathing wave pulses, and smooth hovering/floating animations upon assistant wake-up.
 */
class GlowingOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pulseFraction = 0f
    private var floatOffset = 0f
    private var animPhase = 0f

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    private val innerCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    // Pre-allocated arrays for zero-GC render loop
    private val outerGlowColors = intArrayOf(
        Color.argb(120, 240, 245, 255),
        Color.argb(60, 200, 220, 255),
        Color.argb(20, 180, 200, 255),
        Color.TRANSPARENT
    )
    private val outerGlowStops = floatArrayOf(0.0f, 0.4f, 0.75f, 1.0f)

    private val ringColors = intArrayOf(
        Color.argb(180, 255, 255, 255),
        Color.argb(90, 225, 235, 255),
        Color.TRANSPARENT
    )
    private val ringStops = floatArrayOf(0.5f, 0.85f, 1.0f)

    private val orbColors = intArrayOf(
        Color.argb(255, 255, 255, 255),  // Center bright highlight
        Color.argb(245, 242, 246, 252),  // Upper sphere body
        Color.argb(225, 215, 225, 238),  // Mid sphere tone
        Color.argb(185, 175, 190, 210),  // Deep base shading
        Color.argb(120, 140, 160, 190)   // Rim edge shadow
    )
    private val orbStops = floatArrayOf(0.0f, 0.25f, 0.55f, 0.85f, 1.0f)

    private val sheenColors = intArrayOf(
        Color.argb(200, 255, 255, 255),
        Color.argb(80, 255, 255, 255),
        Color.TRANSPARENT
    )
    private val sheenStops = floatArrayOf(0.0f, 0.5f, 1.0f)

    private var animator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        startAnimation()
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val progress = va.animatedValue as Float
                pulseFraction = progress
                animPhase += 0.04f
                floatOffset = (sin(animPhase.toDouble()) * 8f).toFloat()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = (height / 2f) + floatOffset
        val baseRadius = (minOf(width, height) / 2.3f)

        // 1. Outer Misty Glow (Subtle translucent white/silver aura)
        val outerGlowRadius = baseRadius * (1.15f + (pulseFraction * 0.12f))
        val outerGlowShader = RadialGradient(
            cx, cy, outerGlowRadius,
            outerGlowColors,
            outerGlowStops,
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = outerGlowShader
        canvas.drawCircle(cx, cy, outerGlowRadius, glowPaint)

        // 2. Secondary Breathing Ripple Ring
        val ringRadius = baseRadius * (1.05f + (pulseFraction * 0.08f))
        val ringShader = RadialGradient(
            cx, cy, ringRadius,
            ringColors,
            ringStops,
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = ringShader
        canvas.drawCircle(cx, cy, ringRadius, glowPaint)

        // 3. Spherical Core Orb (Luminous 3D Spherical Volume with realistic light gradient)
        val coreRadius = baseRadius * (0.94f + (pulseFraction * 0.04f))
        val highlightX = cx - (coreRadius * 0.22f)
        val highlightY = cy - (coreRadius * 0.28f)

        val orbShader = RadialGradient(
            highlightX, highlightY, coreRadius * 1.35f,
            orbColors,
            orbStops,
            Shader.TileMode.CLAMP
        )
        orbPaint.shader = orbShader
        canvas.drawCircle(cx, cy, coreRadius, orbPaint)

        // 4. Specular Surface Sheen (Misty soft top reflection)
        val sheenRadius = coreRadius * 0.65f
        val sheenShader = RadialGradient(
            highlightX, highlightY, sheenRadius,
            sheenColors,
            sheenStops,
            Shader.TileMode.CLAMP
        )
        innerCorePaint.shader = sheenShader
        canvas.drawCircle(highlightX, highlightY, sheenRadius, innerCorePaint)
    }
}
