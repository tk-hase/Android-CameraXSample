package tkhase.camerax.sample

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import timber.log.Timber

class MainApplication: Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // ログ出力設定
        Timber.plant(Timber.DebugTree())
    }
}