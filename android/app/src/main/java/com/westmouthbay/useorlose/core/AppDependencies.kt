package com.westmouthbay.useorlose.core

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.westmouthbay.useorlose.calendar.CalendarDisplaySettings
import com.westmouthbay.useorlose.storage.StoreCoordinator
import com.westmouthbay.useorlose.storage.StorePaths

/**
 * The composition root for the app. I05-01 builds the pieces and their
 * injection points; MainActivity consumes them from I05-02 on.
 *
 * [create] must run on the main thread: Chaquopy's [Python.start] requires it.
 */
class AppDependencies(
    val bridge: CoreRead,
    val store: StoreCoordinator,
    val timeSource: TimeSource,
    val displaySettings: CalendarDisplaySettings,
) {
    companion object {
        fun create(context: Context): AppDependencies {
            val applicationContext = context.applicationContext
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }
            return AppDependencies(
                bridge = CoreBridge(Python.getInstance()),
                store = StoreCoordinator(StorePaths.forContext(applicationContext)),
                timeSource = TimeSource.system(),
                displaySettings = CalendarDisplaySettings(),
            )
        }
    }
}
