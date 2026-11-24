package com.westmouthbay.fedleave.storage

import java.io.File
import java.security.MessageDigest

object StoreDigest {

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun fileDigest(file: File): String = sha256(file.readBytes())

    fun tree(files: Map<String, ByteArray>): String {
        val payload = files.entries
            .sortedBy { it.key }
            .joinToString("") { (name, bytes) -> "$name\u0000${sha256(bytes)}\n" }
        return sha256(payload.toByteArray(Charsets.UTF_8))
    }

    fun treeOfStrings(files: Map<String, String>): String =
        tree(files.mapValues { it.value.toByteArray(Charsets.UTF_8) })
}
