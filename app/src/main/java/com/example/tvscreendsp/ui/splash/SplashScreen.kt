package com.example.tvscreendsp.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Professional dark gradient colors (DSP/tech feel)
private val SplashGradientTop = Color(0xFF0D1B2A)
private val SplashGradientMid = Color(0xFF1B2838)
private val SplashGradientBot = Color(0xFF0A0F1A)
private val AccentCyan = Color(0xFF00E5FF)
private val AccentBlue = Color(0xFF448AFF)

/**
 * Professional animated splash screen.
 * 
 * Shows app branding with fade-in + scale animation.
 * After 2 seconds, navigates based on authentication state.
 */
@Composable
fun SplashScreen(
    isAuthenticated: Boolean,
    onNavigateToHome: () -> Unit,
    onNavigateToSignup: () -> Unit
) {
    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    
    // Logo scale: 0.6 → 1.0 with spring
    val logoScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    // Logo alpha: 0 → 1
    val logoAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = EaseOutCubic),
        label = "logoAlpha"
    )
    
    // Text alpha: 0 → 1 (delayed)
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 400,
            easing = EaseOutCubic
        ),
        label = "textAlpha"
    )
    
    // Subtitle alpha
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 700,
            easing = EaseOutCubic
        ),
        label = "subtitleAlpha"
    )
    
    // Trigger animation and navigation
    LaunchedEffect(Unit) {
        startAnimation = true
        delay(3000) // Total splash duration
        
        if (isAuthenticated) {
            onNavigateToHome()
        } else {
            onNavigateToSignup()
        }
    }
    
    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SplashGradientTop, SplashGradientMid, SplashGradientBot)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo / Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring glow
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    AccentCyan.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Inner waveform icon (simulated with text)
                Text(
                    text = "📡",
                    fontSize = 56.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Name
            Text(
                text = "TV Screen DSP",
                modifier = Modifier.alpha(textAlpha),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Digital Signal Processing",
                modifier = Modifier.alpha(subtitleAlpha),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = AccentCyan.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                letterSpacing = 3.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Loading dots animation
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotPulse"
            )
            
            Text(
                text = "● ● ●",
                modifier = Modifier.alpha(subtitleAlpha * dotAlpha),
                fontSize = 12.sp,
                color = AccentBlue,
                letterSpacing = 8.sp
            )
        }
    }
}
