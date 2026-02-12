package com.example.tvscreendsp.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// Matching splash screen colors
private val GradientTop = Color(0xFF0D1B2A)
private val GradientBot = Color(0xFF1B2838)
private val AccentCyan = Color(0xFF00E5FF)

/**
 * Signup screen with phone/email toggle and input validation.
 */
@Composable
fun SignupScreen(
    viewModel: AuthViewModel = viewModel(),
    onNavigateToOtp: () -> Unit
) {
    val inputType by viewModel.inputType.collectAsStateWithLifecycle()
    val inputValue by viewModel.inputValue.collectAsStateWithLifecycle()
    val inputError by viewModel.inputError.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientTop, GradientBot)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            // Header
            Text(
                text = "📡",
                fontSize = 48.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Welcome",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Sign in to continue",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Phone / Email Toggle
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
                    // Segmented Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToggleButton(
                            text = "📱 Phone",
                            selected = inputType == AuthViewModel.InputType.PHONE,
                            onClick = { viewModel.setInputType(AuthViewModel.InputType.PHONE) },
                            modifier = Modifier.weight(1f)
                        )
                        ToggleButton(
                            text = "📧 Email",
                            selected = inputType == AuthViewModel.InputType.EMAIL,
                            onClick = { viewModel.setInputType(AuthViewModel.InputType.EMAIL) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Input Field
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (inputType == AuthViewModel.InputType.PHONE)
                                    "Phone Number" else "Email Address"
                            )
                        },
                        placeholder = {
                            Text(
                                if (inputType == AuthViewModel.InputType.PHONE)
                                    "Enter 10-digit number" else "example@email.com"
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (inputType == AuthViewModel.InputType.PHONE)
                                KeyboardType.Phone else KeyboardType.Email
                        ),
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = inputError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            cursorColor = AccentCyan,
                            focusedLabelColor = AccentCyan,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Send OTP Button
                    Button(
                        onClick = {
                            if (viewModel.sendOtp()) {
                                onNavigateToOtp()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color(0xFF0D1B2A)
                        )
                    ) {
                        Text(
                            text = "Send OTP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer
            Text(
                text = "Demo Authentication • No real SMS/Email sent",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
            contentColor = if (selected) AccentCyan else Color.White.copy(alpha = 0.5f)
        ),
        border = if (selected) ButtonDefaults.outlinedButtonBorder() else null
    ) {
        Text(
            text = text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}
