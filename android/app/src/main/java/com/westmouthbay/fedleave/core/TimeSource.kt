package com.westmouthbay.fedleave.core

import java.time.Clock
import java.time.LocalDate

/** Supplies the device's current local date for Today-following behavior. */
fun interface TimeSource {
    fun today(): LocalDate

    companion object {
        /** The production clock, resolved in the device's default zone. */
        fun system(): TimeSource = TimeSource {
            val clock = Clock.systemDefaultZone()
            LocalDate.now(clock)
        }

        /** A frozen clock for tests and developer fixtures. */
        fun fixed(date: String): TimeSource = TimeSource { LocalDate.parse(date) }
    }
}
