package com.westmouthbay.fedleave.storage

import java.io.File

interface StoreIo {
    fun write(path: File, bytes: ByteArray)

    fun read(path: File): ByteArray

    fun move(source: File, target: File)

    fun delete(path: File)

    fun exists(path: File): Boolean

    fun list(path: File): List<File>

    fun mkdirs(path: File)
}

class DiskStoreIo : StoreIo {

    override fun write(path: File, bytes: ByteArray) {
        path.parentFile?.mkdirs()
        java.io.FileOutputStream(path).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }

    override fun read(path: File): ByteArray = path.readBytes()

    override fun move(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    override fun delete(path: File) {
        path.deleteRecursively()
    }

    override fun exists(path: File): Boolean = path.exists()

    override fun list(path: File): List<File> = path.listFiles()?.toList() ?: emptyList()

    override fun mkdirs(path: File) {
        path.mkdirs()
    }
}
