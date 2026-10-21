package com.tpstreams.player.analytics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import com.tpstreams.player.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AnalyticsUserAgentTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var resources: Resources
    private lateinit var configuration: Configuration

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        packageManager = mock(PackageManager::class.java)
        resources = mock(Resources::class.java)
        configuration = Configuration()

        val appInfo = ApplicationInfo()
        val packageInfo = PackageInfo().apply { versionName = "2.3.4" }

        `when`(context.applicationInfo).thenReturn(appInfo)
        `when`(context.packageManager).thenReturn(packageManager)
        `when`(context.packageName).thenReturn("com.test.app")
        `when`(context.resources).thenReturn(resources)
        `when`(resources.configuration).thenReturn(configuration)
        `when`(packageManager.getApplicationLabel(appInfo)).thenReturn("TestApp")
        `when`(packageManager.getPackageInfo("com.test.app", 0)).thenReturn(packageInfo)
    }

    @Test
    fun `userAgent contains Android and Mobile for standard phone configuration`() {
        configuration.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL
        val ua = AnalyticsUserAgent.build(context)

        assertTrue(ua.contains("Android"))
        assertTrue(ua.contains("Mobile"))
        assertTrue(ua.startsWith("Mozilla/5.0 (Linux; Android"))
        assertTrue(ua.endsWith("TPStreamsAndroidPlayer/${BuildConfig.SDK_VERSION}"))
    }

    @Test
    fun `userAgent contains Tablet for large screen configuration`() {
        configuration.screenLayout = Configuration.SCREENLAYOUT_SIZE_LARGE
        val ua = AnalyticsUserAgent.build(context)

        assertTrue(ua.contains("Android"))
        assertTrue(!ua.contains(" Mobile Safari/537.36"))
        assertTrue(ua.contains("Tablet"))
        assertTrue(ua.endsWith("TPStreamsAndroidPlayer/${BuildConfig.SDK_VERSION}"))
    }

    @Test
    fun `userAgent falls back gracefully on mocked or null context`() {
        val blankContext = mock(Context::class.java)
        val ua = AnalyticsUserAgent.build(blankContext)

        assertTrue(ua.contains("Android"))
        assertTrue(ua.contains("Mobile"))
    }
}
