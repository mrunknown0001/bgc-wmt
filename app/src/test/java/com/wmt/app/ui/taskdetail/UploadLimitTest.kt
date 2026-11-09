package com.wmt.app.ui.taskdetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The size check runs against the server's own two caps.
 *
 * The video limit used to be written down here as 50MB while the server sent its own
 * `video_max_upload_size` that nothing read. That fails in both directions: it refuses a
 * file the server would now accept, and -- worse -- accepts one it will reject, after
 * the upload has already finished over mobile data.
 */
class UploadLimitTest {

    private fun mb(n: Int) = n.toLong() * 1024 * 1024

    @Test
    fun `video is measured against the video cap, not the general one`() {
        // 30MB of video: over the general 10MB cap, well under the video one.
        assertTrue(acceptsUpload("video/mp4", mb(30), maxUploadMb = 10, maxVideoMb = 50))
        assertFalse(acceptsUpload("video/mp4", mb(60), maxUploadMb = 10, maxVideoMb = 50))
    }

    @Test
    fun `the video cap follows the server rather than a number written here`() {
        // Raised on the server: a 70MB video becomes acceptable without an app release.
        assertTrue(acceptsUpload("video/mp4", mb(70), maxUploadMb = 10, maxVideoMb = 100))
        // Lowered on the server: the phone stops offering to send what will be refused.
        assertFalse(acceptsUpload("video/mp4", mb(30), maxUploadMb = 10, maxVideoMb = 25))
    }

    @Test
    fun `images are always accepted, since they are downscaled on the way out`() {
        assertTrue(acceptsUpload("image/jpeg", mb(200), maxUploadMb = 10, maxVideoMb = 50))
    }

    @Test
    fun `everything else is measured against the general cap`() {
        assertTrue(acceptsUpload("application/pdf", mb(9), maxUploadMb = 10, maxVideoMb = 50))
        assertFalse(acceptsUpload("application/pdf", mb(11), maxUploadMb = 10, maxVideoMb = 50))
        // An unknown type is treated as an ordinary file, not waved through.
        assertFalse(acceptsUpload("application/octet-stream", mb(11), maxUploadMb = 10, maxVideoMb = 50))
    }

    @Test
    fun `a file exactly on the cap is allowed`() {
        assertTrue(acceptsUpload("application/pdf", mb(10), maxUploadMb = 10, maxVideoMb = 50))
        assertTrue(acceptsUpload("video/mp4", mb(50), maxUploadMb = 10, maxVideoMb = 50))
    }
}
