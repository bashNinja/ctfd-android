package io.github.bashninja.ctfd.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A saved CTF. The token is kept encrypted with a key that never leaves the Android Keystore. */
@Serializable
data class SavedCtf(
    val name: String,
    val url: String,
    val encryptedToken: String,
) {
    fun api(store: AccountStore): CtfdApi = CtfdApi(url.toHttpUrl(), store.token(this))
}

/** The saved CTFs and which one is in use, like the `ctfd` CLI's config file. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("ctfs", Context.MODE_PRIVATE)

    private val _ctfs = MutableStateFlow(load())
    val ctfs: StateFlow<List<SavedCtf>> = _ctfs.asStateFlow()

    private val _active = MutableStateFlow(prefs.getString(KEY_ACTIVE, null)?.takeIf { n -> _ctfs.value.any { it.name == n } }
        ?: _ctfs.value.firstOrNull()?.name)
    val active: StateFlow<String?> = _active.asStateFlow()

    fun activeCtf(): SavedCtf? = _ctfs.value.firstOrNull { it.name == _active.value }

    /** Saves a CTF, replacing one with the same URL or name, and makes it the one in use. */
    fun add(name: String, url: String, token: String) {
        val entry = SavedCtf(name, url, encrypt(token))
        val list = _ctfs.value.filterNot { it.url == url || it.name == name } + entry
        save(list)
        select(name)
    }

    fun remove(name: String) {
        save(_ctfs.value.filterNot { it.name == name })
        if (_active.value == name) select(_ctfs.value.firstOrNull()?.name)
    }

    fun select(name: String?) {
        _active.value = name
        prefs.edit().putString(KEY_ACTIVE, name).apply()
    }

    fun token(ctf: SavedCtf): String = decrypt(ctf.encryptedToken)

    private fun load(): List<SavedCtf> = prefs.getString(KEY_LIST, null)?.let {
        runCatching { CtfdApi.json.decodeFromString(ListSerializer(SavedCtf.serializer()), it) }.getOrNull()
    } ?: emptyList()

    private fun save(list: List<SavedCtf>) {
        _ctfs.value = list
        prefs.edit().putString(KEY_LIST, CtfdApi.json.encodeToString(ListSerializer(SavedCtf.serializer()), list)).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val sealed = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES))
    }

    private companion object {
        const val KEY_LIST = "list"
        const val KEY_ACTIVE = "active"
        const val KEY_ALIAS = "ctfd-tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
