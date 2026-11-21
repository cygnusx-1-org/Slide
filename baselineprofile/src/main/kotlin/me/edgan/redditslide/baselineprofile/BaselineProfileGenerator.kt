package me.edgan.redditslide.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.edgan.redditslide.baselineprofile.SlideJourneys.PACKAGE
import me.edgan.redditslide.baselineprofile.SlideJourneys.openImage
import me.edgan.redditslide.baselineprofile.SlideJourneys.openPostAndScrollComments
import me.edgan.redditslide.baselineprofile.SlideJourneys.scrollFeed
import me.edgan.redditslide.baselineprofile.SlideJourneys.startToFeed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods Slide runs on its hot paths, so ART can compile them ahead of
 * time at install instead of interpreting them until the JIT catches up.
 *
 * Covers cold start, feed scroll, opening a post and scrolling its comments, and opening an image.
 * See baselineprofile/build.gradle for how to run it and copy the result into :app.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        startToFeed()
        scrollFeed()
        openPostAndScrollComments()
        openImage()
    }
}
