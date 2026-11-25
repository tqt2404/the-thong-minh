package com.example

import java.security.*
import java.io.File
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

object KeyUtil {
    private const val PRIVATE_KEY_FILE = "backend_private.key"
    private const val PUBLIC_KEY_FILE = "backend_public.key"

    fun generateKeyPairIfNotExists() {
        val privateKeyFile = File(PRIVATE_KEY_FILE)
        val publicKeyFile = File(PUBLIC_KEY_FILE)
        if (privateKeyFile.exists() && publicKeyFile.exists()) return

        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(512)
        val pair = keyGen.generateKeyPair()
        privateKeyFile.writeBytes(pair.private.encoded)
        publicKeyFile.writeBytes(pair.public.encoded)
    }

    fun getPrivateKey(): PrivateKey {
        val bytes = File(PRIVATE_KEY_FILE).readBytes()
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun getPublicKey(): PublicKey {
        val bytes = File(PUBLIC_KEY_FILE).readBytes()
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }
}