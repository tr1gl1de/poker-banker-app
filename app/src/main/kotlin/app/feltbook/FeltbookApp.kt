package app.feltbook

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FeltbookApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(RussianLocale.wrap(base))
    }
}
