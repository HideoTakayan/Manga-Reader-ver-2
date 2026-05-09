package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.network.ProgressListener

open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
) : ProgressListener {
    val number: Int get() = index + 1
    var status: Int = 0
    var progress: Int = 0

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    companion object {
        const val QUEUE = 0
        const val LOAD_PAGE = 1
        const val DOWNLOAD_IMAGE = 2
        const val READY = 3
        const val ERROR = 4
    }
}
