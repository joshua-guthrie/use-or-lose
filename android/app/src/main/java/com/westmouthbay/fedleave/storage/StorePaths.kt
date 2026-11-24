package com.westmouthbay.fedleave.storage

import android.content.Context
import java.io.File

object StorePaths {

    const val CONFIG = "config.json"
    const val POINTER = "active.json"
    const val RECOVERY = "recovery.json"

    private val YEAR_FILE = Regex("leave_years/(\\d{1,4})\\.json")
    private val CACHE_FILE = Regex("holiday_cache/[A-Za-z0-9_-]+\\.json")

    fun forContext(context: Context): File = File(context.noBackupFilesDir, "fedleave")

    fun pointerFile(root: File): File = File(root, POINTER)

    fun generationsDir(root: File): File = File(root, "generations")

    fun generationDir(root: File, generationId: String): File = File(generationsDir(root), generationId)

    fun candidatesDir(root: File): File = File(root, "candidates")

    fun recoveryFile(root: File): File = File(root, RECOVERY)

    fun yearFile(year: Int): String = "leave_years/$year.json"

    fun isCanonical(name: String): Boolean =
        name == CONFIG || YEAR_FILE.matches(name) || CACHE_FILE.matches(name)

    fun yearOf(name: String): Int? = YEAR_FILE.matchEntire(name)?.groupValues?.get(1)?.toInt()
}
