package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.domain.service.TextOcr
import com.pennywiseai.tracker.ocr.MlKitTextOcr
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the standard flavor: [TextOcr] is ML Kit's bundled
 * recognizer (on-device, Play-services backed). The F-Droid flavor has a
 * sibling module under `app/src/fdroid/` binding the unavailable stub, so
 * shared `main` sources only ever import the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindTextOcr(impl: MlKitTextOcr): TextOcr
}
