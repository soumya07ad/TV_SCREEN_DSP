package com.example.tvscreendsp.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Demo-only AuthManager using SharedPreferences.
 * 
 * Manages authentication state (logged in / logged out).
 * No real backend — purely local persistence for demo purposes.
 */
class AuthManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "tv_screen_dsp_auth"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LAST_USER = "last_user"
        
        @Volatile
        private var INSTANCE: AuthManager? = null
        
        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Whether the user has completed the demo auth flow.
     */
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
    
    /**
     * The phone number or email used for last login.
     */
    var lastUser: String?
        get() = prefs.getString(KEY_LAST_USER, null)
        set(value) = prefs.edit().putString(KEY_LAST_USER, value).apply()
    
    /**
     * Mark user as authenticated.
     */
    fun login(userIdentifier: String) {
        lastUser = userIdentifier
        isLoggedIn = true
    }
    
    /**
     * Clear authentication state.
     */
    fun logout() {
        isLoggedIn = false
        lastUser = null
    }
}
