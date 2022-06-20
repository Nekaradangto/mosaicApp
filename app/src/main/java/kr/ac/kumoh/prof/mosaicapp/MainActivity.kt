package kr.ac.kumoh.prof.mosaicapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kr.ac.kumoh.prof.mosaicapp.databinding.ActivityMainBinding
import okhttp3.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlay: Bitmap

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var rect: RectF = RectF()
    private var points: Queue<Float> = LinkedList<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

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

        // Preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            }

        //화면 비율 640 X 480 설정
        imageCapture = ImageCapture.Builder()
            .setMaxResolution(Size(960, 540))
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalyzer?.setAnalyzer(
            ContextCompat.getMainExecutor(this),
            ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
              val bitmap = viewBinding.viewFinder.bitmap ?: return@Analyzer
              overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

              if (points.isNotEmpty()) {
                val paint = Paint().apply {
                  isAntiAlias = true
                  style = Paint.Style.FILL
                  color = Color.WHITE
                  setMaskFilter(BlurMaskFilter(5f, BlurMaskFilter.Blur.INNER))
                  strokeWidth = 10f
                }

                val size = points.size / 4-1

                for (i in 0..size) {
                  val canvas = Canvas(overlay)
                  val x = points.elementAt(i*4+0) * 2.7f - 30f
                  val y = points.elementAt(i*4+1) * 2.7f - 30f
                  val width = points.elementAt(i*4+2) * 2.7f - 30f
                  val height = points.elementAt(i*4+3) * 2.7f - 30f

                  rect.set(x, y, width, height)

                  canvas.drawRect(
                    rect,
                    paint
                  )
                  overlay?.let { Canvas(it) }?.apply {
                    canvas
                  }
                }
              }
              runOnUiThread {
                viewBinding.imageView.setImageBitmap(overlay)
              }
              imageProxy.close()
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

    // json 파일 파싱 함수
    private fun jsonParser(jsonString: String): Queue<Float> {
      val jsonObject = JSONTokener(jsonString).nextValue() as JSONObject
      val jsonArray = jsonObject.getJSONArray("indices")
      points = LinkedList<Float>()

      for (i in 0 until jsonArray.length()) {
        points.offer(jsonArray.getJSONObject(i).getString("x").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("y").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("width").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("height").toFloat())
      }
      Log.i("points", points.toString())
      return points
    }

    // 사진을 저장하는게 아니라 서버에 바로 올리기 위한 함수 (10프레임에 한 번씩 실행시킬 예정)
    private fun uploadPhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.e(TAG, "Photo Upload success: "+ image.format)//JPEG Format

                    val url = "https://nekara.loca.lt/postimage"

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
                       val body = response.body?.string()
                       Log.d("요청", body!!)
                       points = jsonParser(body)
                     }

                     override fun onFailure(call: Call, e: IOException) {
                       Log.d("요청 실패",e.toString())
                     }})

                    image.close()
                }

            }
        )
        waitPhoto() // 코드 실행뒤에 계속해서 반복하도록 작업한다.
    }

    // 블러 처리된 사진 저장
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.e(TAG, "Photo Upload success: "+ image.format)//JPEG Format

                    val url = "https://nekara.loca.lt/updateimage"

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

                            val root: File=android.os.Environment.getExternalStorageDirectory()

                            val fos = FileOutputStream(root.getAbsolutePath()+"/Pictures/blur.jpg")
                            fos.write(response.body!!.bytes())
                            fos.close()
                            val imgBitmap = BitmapFactory.decodeFile(root.getAbsolutePath()+"/Pictures/blur.jpg")
                            MediaStore.Images.Media.insertImage(getContentResolver(), imgBitmap, FILENAME_FORMAT , "blurImage")
                        }

                        override fun onFailure(call: Call, e: IOException) {
                            Log.d("요청 실패",e.toString())
                        }})
                    image.close()
                }
            }
        )
    }

    private val mDelayHandler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    private fun waitPhoto() {
        mDelayHandler.postDelayed(::uploadPhoto, 1000) // 1초 후에 uploadPhoto 함수를 실행한다.
    }

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