package tkhase.camerax.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import timber.log.Timber
import tkhase.camerax.sample.databinding.ActivityMainBinding
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraSelector: CameraSelector
    private var lastImageAnalysis: UseCase? = null
    private val cameraAnalysisExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.isStartCapture = false
        binding.finishButton.setOnClickListener {
            this.finish()

        }
        binding.controlAnalysisButton.setOnClickListener {
            if (binding.isStartCapture) {
                stopAnalysis()
            } else {
                startAnalysis()
            }

            binding.isStartCapture = !binding.isStartCapture
        }

        setupCamera()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 画像解析処理用のExecutorを終了
        cameraAnalysisExecutor.shutdown()
        Timber.d("shutdown camera analysis executor")
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_REQUEST_CODE && permissions[0] == Manifest.permission.CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) { // ユーザがカメラ権限を許可 → もう一度カメラの設定を行う
                Timber.d("* user allowed camera service's permission")
                setupCamera()
            } else { // カメラ権限を拒否した場合 → アプリ終了
                Timber.w("! user denied camera service's permission")
                showPermissionErrorDialog()
            }
        }
    }

    /**
     * カメラ機能の初期化
     */
    private fun setupCamera() {
        // すでにカメラ権限許可済み
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Timber.d("* require show request permission's dialog")
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST_CODE)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // カメラ機能を提供するProviderを取得
            cameraProvider = cameraProviderFuture.get()
            // カメラのレンズ（前 or 後ろ）のどちらを使うか設定
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // プレビュー表示を開始する
            startPreview()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * 画面プレビューの初期化
     */
    private fun startPreview() {
        Timber.i("* start preview")

        // プレビュー設定
        val preview = Preview.Builder()
            .setTargetResolution(Size(binding.cameraPreview.measuredWidth, binding.cameraPreview.measuredHeight))
            .build()
        // Viewの関連付け
        preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

        // このアクティビティのライフサイクルにバインドする
        bindCameraUseCaseToLifecycle(preview)
    }

    /**
     * 画像解析機能の初期化
     */
    private fun startAnalysis() {
        Timber.i("===== start analysis =====")

        // 画像解析の設定
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(binding.cameraPreview.measuredWidth, binding.cameraPreview.measuredHeight))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 非ブロッキングモード
            .build()

        // 画像解析処理の実装
        imageAnalysis.setAnalyzer(cameraAnalysisExecutor) { imageProxy ->
            Timber.v("image analysis -- height: ${imageProxy.height}, width: ${imageProxy.width}, planes count: ${imageProxy.planes.size}")

            // planes[0]にYUV形式のデータが含まれているので、画像解析時はそのデータを解析すると良い
            for (index in imageProxy.planes.indices) {
                val buffer = imageProxy.planes[index].buffer
                Timber.v("    - planes[$index]: ${buffer.limit()}")
            }

            imageProxy.close()
        }

        // このアクティビティのライフサイクルにバインドする
        bindCameraUseCaseToLifecycle(imageAnalysis)
        lastImageAnalysis = imageAnalysis
    }

    private fun stopAnalysis() {
        Timber.i("===== stop analysis =====")
        lastImageAnalysis?.also {
            unbindCameraUseCase(it)
            lastImageAnalysis = null
        }
    }

    private fun bindCameraUseCaseToLifecycle(useCase: UseCase) {
        cameraProvider.bindToLifecycle(this, cameraSelector, useCase)
    }

    private fun unbindCameraUseCase(useCase: UseCase) {
        cameraProvider.unbind(useCase)
    }

    private fun showPermissionErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("エラー")
            .setMessage("本アプリを利用する場合、カメラ権限の許可が必要です。")
            .setPositiveButton("アプリを閉じる") { _, _ -> this.finish() }
            .show()
    }

    companion object {
        val CAMERA_REQUEST_CODE by lazy { Random.nextInt() }
    }
}