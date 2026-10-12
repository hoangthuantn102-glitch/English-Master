package com.example.ui.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class FireworkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float = 1f,
    val decay: Float = 0.02f
)

@Composable
fun CelebrationFireworksEffect(
    modifier: Modifier = Modifier,
    onExplosion: (() -> Unit)? = null
) {
    val particles = remember { mutableStateListOf<FireworkParticle>() }
    val colors = listOf(
        Color(0xFFFFD700), // Gold
        Color(0xFFFF1744), // Crimson
        Color(0xFF00E676), // Bright Green
        Color(0xFF2979FF), // Vivid Blue
        Color(0xFFFF4081), // Pink
        Color(0xFFFF9100), // Orange
        Color(0xFF00E5FF)  // Cyan
    )

    // Firework burst spawner
    LaunchedEffect(Unit) {
        repeat(12) {
            delay((250..600).random().toLong())
            onExplosion?.invoke()
            val centerX = Random.nextFloat() * 800f + 100f
            val centerY = Random.nextFloat() * 600f + 150f
            val baseColor = colors.random()

            // Spawn 36 particles per burst
            for (i in 0 until 36) {
                val angle = (i * 10) * (Math.PI / 180.0)
                val speed = Random.nextFloat() * 7f + 3f
                particles.add(
                    FireworkParticle(
                        x = centerX,
                        y = centerY,
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        color = if (Random.nextBoolean()) baseColor else colors.random(),
                        size = Random.nextFloat() * 6f + 4f,
                        alpha = 1f,
                        decay = Random.nextFloat() * 0.015f + 0.012f
                    )
                )
            }
        }
    }

    // Animation frame tick
    val transition = rememberInfiniteTransition(label = "particles")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tick"
    )

    SideEffect {
        if (tick >= 0f) {
            val iterator = particles.listIterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.12f // gravity
                p.alpha -= p.decay
                if (p.alpha <= 0f) {
                    iterator.remove()
                }
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { p ->
            drawCircle(
                color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                radius = p.size,
                center = Offset(p.x, p.y)
            )
        }
    }
}
