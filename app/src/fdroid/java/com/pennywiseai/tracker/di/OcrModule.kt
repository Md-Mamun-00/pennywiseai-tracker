package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.domain.service.TextOcr
import com.pennywiseai.tracker.ocr.NoTextOcr
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the F-Droid flavor: no OCR, so [TextOcr] is the
 * unavailable stub and the image-import button never shows. Standard flavor
 * has a sibling module wiring ML Kit's recognizer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindTextOcr(impl: NoTextOcr): TextOcr
}
