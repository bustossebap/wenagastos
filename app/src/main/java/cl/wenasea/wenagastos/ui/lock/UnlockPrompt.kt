package cl.wenasea.wenagastos.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/**
 * Pide la credencial del sistema sin agregar la librería `androidx.biometric`: desde Android 11
 * el `BiometricPrompt` del sistema ya ofrece huella, rostro o PIN en un solo diálogo, y en
 * versiones anteriores se usa la pantalla de bloqueo con `createConfirmDeviceCredentialIntent`.
 *
 * Si el teléfono no tiene PIN ni huella no hay nada que preguntar: el bloqueo de Wenagastos no
 * puede proteger nada y la pantalla de bloqueo lo dice.
 */
object UnlockPrompt {

    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Pantalla de bloqueo del sistema; `null` cuando este Android usa [biometric]. */
    @Suppress("DEPRECATION")
    fun credentialIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            null
        } else {
            context.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent("Desbloquear Wenagastos", null)
        }

    /**
     * Un solo diálogo para biometría o PIN (Android 11+).
     *
     * @return `false` si el teléfono no puede autenticar por esta vía, para que quien llama
     * elija otra o deje pasar.
     */
    fun biometric(activity: Activity, onDone: (Boolean) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = activity.getSystemService(BiometricManager::class.java)
        if (manager?.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            return false
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle("Desbloquear Wenagastos")
            .setSubtitle("Usa tu huella o el PIN del teléfono")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onDone(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onDone(false)
                }
            },
        )
        return true
    }
}
