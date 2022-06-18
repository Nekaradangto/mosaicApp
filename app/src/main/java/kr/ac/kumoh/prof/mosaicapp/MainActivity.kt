package kr.ac.kumoh.prof.mosaicapp

import android.Manifest
import android.R.attr.path
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kr.ac.kumoh.prof.mosaicapp.databinding.ActivityMainBinding
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null

    private lateinit var cameraExecutor: ExecutorService

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.videoCaptureButton.setOnClickListener { uploadPhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            if (imageAnalyzer != null) {
                cameraProvider!!.unbind(imageAnalyzer)
            }

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
            
            //화면 비율 640 X 480 설정
            imageCapture = ImageCapture.Builder().setMaxResolution(Size(640, 480)).build()

            imageAnalyzer = ImageAnalysis.Builder().build()

            needUpdateGraphicOverlayImageSourceInfo = true

            imageAnalyzer?.setAnalyzer(
                ContextCompat.getMainExecutor(this),
                ImageAnalysis.Analyzer { imageProxy: ImageProxy ->

                    if (needUpdateGraphicOverlayImageSourceInfo) {
//                        val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
//                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
//                        if (rotationDegrees == 0 || rotationDegrees == 180) {
//                            graphicOverlay!!.setImageSourceInfo(imageProxy.width, imageProxy.height, isImageFlipped)
//                        } else {
//                            graphicOverlay!!.setImageSourceInfo(imageProxy.height, imageProxy.width, isImageFlipped)
//                        }
                        needUpdateGraphicOverlayImageSourceInfo = false
                    }
//                    try {
//                        imageProcessor!!.processImageProxy(imageProxy, graphicOverlay)
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Failed to process image. Error: " + e.localizedMessage)
//                        Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
//                    }
                }
            )

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this)

        )
    }

//    fun HttpCheckId() {
//
//        // URL을 만들어 주고
//        val url = "https://myser.run-asia-northeast1.goorm.io/post"
//
//        //데이터를 담아 보낼 바디를 만든다
//        val requestBody : RequestBody = FormBody.Builder()
//          .add("id","지난달 28일 수원에 살고 있는 윤주성 연구원은 코엑스(서울 삼성역)에서 개최되는 DEVIEW 2019 Day1에 참석했다. LaRva팀의 '엄~청 큰 언어 모델 공장 가동기!' 세션을 들으며 언어모델을 학습시킬때 multi-GPU, TPU 모두 써보고 싶다는 생각을 했다.")
//          .build()
//
//        // OkHttp Request 를 만들어준다.
//        val request = Request.Builder()
//          .url(url)
//          .post(requestBody)
//          .build()
//
//        // 클라이언트 생성
//        val client = OkHttpClient()
//
//        Log.d("전송 주소 ","https://myser.run-asia-northeast1.goorm.io/post")
//
//        // 요청 전송
//        client.newCall(request).enqueue(object : Callback {
//
//          override fun onResponse(call: Call, response: Response) {
//            val body = response.body?.string();
//
//
//            Log.d("요청", body!!)
//          }
//
//          override fun onFailure(call: Call, e: IOException) {
//            Log.d("요청","요청 실패 ")
//          }
//
//        })
//    }

    //사진을 저장하는게 아니라 서버에 바로 올리기 위한 함수 (10프레임에 한 번씩 실행시킬 예정)
    private fun uploadPhoto(){
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.e(TAG, "Photo Upload success: "+ image.format)//JPEG Format

                    val url = "https://myser.run-asia-northeast1.goorm.io/postimage"

                    val byteArray = ByteArray(image.planes[0].buffer.remaining())
                    image.planes[0].buffer.get(byteArray)

                    val requestBody: RequestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "image/jpg",
                            "filename.jpg",
                            RequestBody.create(MultipartBody.FORM, byteArray)
                        )
                        .build()

                    val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                    // 클라이언트 생성
                   val client = OkHttpClient()

                   // 요청 전송
                   client.newCall(request).enqueue(object : Callback {

                     override fun onResponse(call: Call, response: Response) {
                       val body = response.body?.string();


                       Log.d("요청", body!!)
                     }

                     override fun onFailure(call: Call, e: IOException) {
                       Log.d("요청","요청 실패 ")
                     }})

                    image.close()
                    //super.onCaptureSuccess(image)
                }

            }
        )

    }

    fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun captureVideo() {}

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    companion object {
        private const val TAG = "MosaicApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}