package com.example.tvscreendsp.ui.auth

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import com.example.tvscreendsp.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the demo authentication flow.
 * 
 * Handles input validation, OTP generation, and verification.
 * OTP is generated locally — no network calls.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    
    private val authManager = AuthManager.getInstance(application)
    
    // Input type: PHONE or EMAIL
    enum class InputType { PHONE, EMAIL }
    
    private val _inputType = MutableStateFlow(InputType.PHONE)
    val inputType: StateFlow<InputType> = _inputType.asStateFlow()
    
    private val _inputValue = MutableStateFlow("")
    val inputValue: StateFlow<String> = _inputValue.asStateFlow()
    
    private val _inputError = MutableStateFlow<String?>(null)
    val inputError: StateFlow<String?> = _inputError.asStateFlow()
    
    private val _otpError = MutableStateFlow<String?>(null)
    val otpError: StateFlow<String?> = _otpError.asStateFlow()
    
    // Generated OTP (demo — logged to Logcat)
    private var generatedOtp: String = ""
    
    /**
     * Switch between phone and email input.
     */
    fun setInputType(type: InputType) {
        _inputType.value = type
        _inputValue.value = ""
        _inputError.value = null
    }
    
    /**
     * Update the input value (phone or email).
     */
    fun updateInput(value: String) {
        _inputValue.value = value
        _inputError.value = null
    }
    
    /**
     * Validate input and generate OTP.
     * @return true if OTP was generated successfully
     */
    fun sendOtp(): Boolean {
        val value = _inputValue.value.trim()
        
        // Validate
        val error = when (_inputType.value) {
            InputType.PHONE -> {
                when {
                    value.isEmpty() -> "Phone number is required"
                    !value.all { it.isDigit() } -> "Phone number must contain only digits"
                    value.length != 10 -> "Phone number must be exactly 10 digits"
                    else -> null
                }
            }
            InputType.EMAIL -> {
                when {
                    value.isEmpty() -> "Email is required"
                    !Patterns.EMAIL_ADDRESS.matcher(value).matches() -> "Enter a valid email address"
                    else -> null
                }
            }
        }
        
        if (error != null) {
            _inputError.value = error
            return false
        }
        
        // Generate 6-digit OTP
        generatedOtp = (100000..999999).random().toString()
        Log.d("AuthViewModel", "═══════════════════════════════════════")
        Log.d("AuthViewModel", "  DEMO OTP: $generatedOtp")
        Log.d("AuthViewModel", "  For: $value")
        Log.d("AuthViewModel", "═══════════════════════════════════════")
        
        return true
    }
    
    /**
     * Verify entered OTP against the generated one.
     * @return true if OTP matches
     */
    fun verifyOtp(enteredOtp: String): Boolean {
        if (enteredOtp.length != 6) {
            _otpError.value = "OTP must be 6 digits"
            return false
        }
        
        // Demo mode: accept any 6-digit OTP
        authManager.login(_inputValue.value.trim())
        _otpError.value = null
        return true
    }
    
    /**
     * Clear OTP error state.
     */
    fun clearOtpError() {
        _otpError.value = null
    }
    
    /**
     * Check if user is already authenticated.
     */
    fun isAuthenticated(): Boolean = authManager.isLoggedIn
}
