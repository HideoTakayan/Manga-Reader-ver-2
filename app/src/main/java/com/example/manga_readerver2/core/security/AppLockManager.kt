package com.example.manga_readerver2.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object AppLockManager : DefaultLifecycleObserver {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var lastPauseTime = 0L
    private val securityPreferences by lazy { Injekt.get<SecurityPreferences>() }
    private var currentActivity: FragmentActivity? = null

    // Call this from MainActivity to keep track of the current activity for BiometricPrompt
    fun setActivity(activity: FragmentActivity?) {
        currentActivity = activity
        if (activity != null && _isLocked.value) {
            promptUnlock(activity)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (!securityPreferences.appLockEnabled.get()) {
            _isLocked.value = false
            return
        }

        val timeoutMinutes = securityPreferences.appLockTimeout.get()
        val timeoutMillis = timeoutMinutes * 60 * 1000L
        val now = System.currentTimeMillis()

        if (lastPauseTime > 0 && (now - lastPauseTime) >= timeoutMillis) {
            _isLocked.value = true
        } else if (lastPauseTime == 0L) {
            // First launch
            _isLocked.value = true
        }

        if (_isLocked.value) {
            currentActivity?.let { promptUnlock(it) }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (securityPreferences.appLockEnabled.get()) {
            lastPauseTime = System.currentTimeMillis()
        }
    }

    fun lockNow() {
        if (securityPreferences.appLockEnabled.get()) {
            _isLocked.value = true
        }
    }

    fun setLocked(locked: Boolean) {
        _isLocked.value = locked
    }

    fun promptUnlock(activity: FragmentActivity, onUnlockSuccess: (() -> Unit)? = null) {
        val biometricManager = BiometricManager.from(activity)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(activity)
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Mở khóa ứng dụng")
                    .setSubtitle("Vui lòng xác thực để tiếp tục sử dụng Manga Reader")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()

                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        _isLocked.value = false
                        lastPauseTime = System.currentTimeMillis()
                        onUnlockSuccess?.invoke()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // User cancelled or error, stay locked
                    }
                })

                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Biometrics not available, skip lock
                _isLocked.value = false
                onUnlockSuccess?.invoke()
            }
        }
    }
}
