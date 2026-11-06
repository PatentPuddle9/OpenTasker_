package com.opentasker.core.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCredentialPromptTest {

    @Test
    fun olderAndroidVersionsKeepTheScreenCredentialPrompt() {
        assertEquals(
            DeviceSignInPromptMode.DEVICE_CREDENTIAL_ONLY,
            deviceSignInPromptModeForApi(26),
        )
        assertEquals(
            DeviceSignInPromptMode.DEVICE_CREDENTIAL_ONLY,
            deviceSignInPromptModeForApi(27),
        )
    }

    @Test
    fun androidNineUsesFingerprintWithAnExplicitScreenCredentialFallback() {
        assertEquals(
            DeviceSignInPromptMode.BIOMETRIC_WITH_CREDENTIAL_FALLBACK,
            deviceSignInPromptModeForApi(28),
        )
    }

    @Test
    fun androidTenAndNewerAllowBiometricOrDeviceCredential() {
        assertEquals(
            DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL,
            deviceSignInPromptModeForApi(29),
        )
        assertEquals(
            DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL,
            deviceSignInPromptModeForApi(30),
        )
        assertEquals(
            DeviceSignInPromptMode.BIOMETRIC_OR_DEVICE_CREDENTIAL,
            deviceSignInPromptModeForApi(36),
        )
    }
}
