package eu.kanade.tachiyomi.util.system

import android.util.Log

object logcat {
    operator fun invoke(priority: Int = Log.DEBUG, tag: String = "Tachiyomi", message: () -> String) {
        Log.println(priority, tag, message())
    }
    
    operator fun invoke(priority: Int, throwable: Throwable, tag: String = "Tachiyomi", message: () -> String) {
        Log.println(priority, tag, "${message()}\n${Log.getStackTraceString(throwable)}")
    }
}
