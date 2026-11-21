package me.edgan.redditslide.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.edgan.redditslide.baselineprofile.SlideJourneys.PACKAGE
import me.edgan.redditslide.baselineprofile.SlideJourneys.awaitList
import me.edgan.redditslide.baselineprofile.SlideJourneys.dismissFirstRunIfPresent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start with and without the baseline profile, to show the profile still pays for itself.
 *
 * [startupNoCompilation] is what a sideloaded APK got before profileinstaller: everything
 * interpreted or JIT-compiled. [startupBaselineProfile] installs the profile shipped in the APK and
 * fails outright if there is none, so it also proves the APK carries one.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.COLD,
        // A fresh install would otherwise measure the tutorial's first frame, not the feed's.
        setupBlock = {
            // COLD kills the app before the measured launch, not after it, so the previous
            // iteration's process is still alive here. The launcher activity's static
            // Slide.hasStarted would then make it finish without starting MainActivity, and
            // startActivityAndWait would wait for a frame that never comes.
            killProcess()
            pressHome()
            startActivityAndWait()
            device.dismissFirstRunIfPresent()
            killProcess()
        },
    ) {
        pressHome()
        startActivityAndWait()
        // The launcher activity is a trampoline into MainActivity, so wait for the feed itself.
        device.awaitList()
    }
}
