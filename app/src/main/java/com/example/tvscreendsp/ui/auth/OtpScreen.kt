package com.example.tvscreendsp.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// Matching theme colors
private val GradientTop = Color(0xFF0D1B2A)
private val GradientBot = Color(0xFF1B2838)
private val AccentCyan = Color(0xFF00E5FF)
private val SuccessGreen = Color(0xFF00E676)

/**
 * OTP verification screen.
 * User enters the 6-digit OTP generated locally (visible in Logcat).
 */
@Composable
fun OtpScreen(
    viewModel: AuthViewModel = viewModel(),
    onVerificationSuccess: () -> Unit
) {
    val inputValue by viewModel.inputValue.collectAsStateWithLifecycle()
    val inputType by viewModel.inputType.collectAsStateWithLifecycle()
    val otpError by viewModel.otpError.collectAsStateWithLifecycle()
    
    var otpValue by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    
    // Success animation scale
    val successScale by animateFloatAsState(
        targetValue = if (showSuccess) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "successScale"
    )
    
    // Navigate after success animation
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            kotlinx.coroutines.delay(800)
            onVerificationSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientTop, GradientBot)
                )
            )
    ) {
        if (showSuccess) {
            // Success state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(successScale)
                ) {
                    Text(text = "✅", fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Verification Successful!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }
        } else {
            // OTP Input state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(80.dp))
                
                // Lock icon
                Text(text = "🔐", fontSize = 56.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Enter OTP",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show where OTP was "sent"
                val destination = if (inputType == AuthViewModel.InputType.PHONE) {
                    "📱 ${inputValue.take(3)}***${inputValue.takeLast(2)}"
                } else {
                    "📧 ${inputValue.substringBefore("@").take(3)}***@${inputValue.substringAfter("@")}"
                }
                
                Text(
                    text = "Sent to $destination",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // OTP Input Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // OTP TextField
                        OutlinedTextField(
                            value = otpValue,
                            onValueChange = { 
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    otpValue = it
                                    viewModel.clearOtpError()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("6-Digit OTP") },
                            placeholder = { Text("000000") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            isError = otpError != null,
                            supportingText = otpError?.let {
                                { Text(it, color = MaterialTheme.colorScheme.error) }
                            },
                            shape = RoundedCornerShape(12.dp),
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 24.sp,
                                letterSpacing = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = AccentCyan,
                                focusedLabelColor = AccentCyan,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.2f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Verify Button
                        Button(
                            onClick = {
                                isVerifying = true
                                if (viewModel.verifyOtp(otpValue)) {
                                    showSuccess = true
                                }
                                isVerifying = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = otpValue.length == 6 && !isVerifying,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF0D1B2A),
                                disabledContainerColor = AccentCyan.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (isVerifying) "Verifying..." else "Verify OTP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Hint for demo
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A237E).copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = "💡 Demo: Check Logcat for OTP\nFilter tag: \"AuthViewModel\"",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Footer
                Text(
                    text = "Demo Authentication • No real verification",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
