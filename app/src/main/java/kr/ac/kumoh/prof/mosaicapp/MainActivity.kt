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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlay: Bitmap
//    private lateinit var points: Queue<Float>

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

//        waitPhoto()

//        jsonParser()
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
//                .setTargetResolution(Size(720, 1280))
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
                  style = Paint.Style.STROKE
                  color = Color.RED
//                        setMaskFilter(BlurMaskFilter(5f, BlurMaskFilter.Blur.INNER))
                  strokeWidth = 10f
                }

                val size = points.size / 4

                for (i in 1..size) {
                  val canvas = Canvas(overlay)
//                  val x = points.poll() * 2.7f
//                  val y = points.poll() * 2.7f
//                  val width = points.poll() * 2.7f
//                  val height = points.poll() * 2.7f
                  val x = points.elementAt(0) * 2.7f
                  val y = points.elementAt(1) * 2.7f
                  val width = points.elementAt(2) * 2.7f
                  val height = points.elementAt(3) * 2.7f

                  rect.set(x, y, width, height)

                  canvas.drawRect(
                    rect,
                    paint
                  )
                  overlay?.let { Canvas(it) }?.apply {
                    canvas
                  }
                }
//                takePhoto()
              }
//              else {
//                takePhoto()
//              }

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

    private fun jsonParser(jsonString: String): Queue<Float> {
//      val jsonString = assets.open("test.json").reader().readText()
      val jsonObject = JSONTokener(jsonString).nextValue() as JSONObject
      val jsonArray = jsonObject.getJSONArray("indices")
//      val arr: Array<Float> = arrayOf(0f, 0f, 0f, 0f)
      points = LinkedList<Float>()

      for (i in 0 until jsonArray.length()) {
        points.offer(jsonArray.getJSONObject(i).getString("x").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("y").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("width").toFloat())
        points.offer(jsonArray.getJSONObject(i).getString("height").toFloat())
//        points.addAll(arr)
        Log.i("points", points.toString())
      }
//      Log.i("sampleQueueSize", points.size.toString())
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
                    //super.onCaptureSuccess(image)
                }

            }
        )
//        waitPhoto() // 코드 실행뒤에 계속해서 반복하도록 작업한다.

    }

    private val mDelayHandler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    private fun waitPhoto(){
        mDelayHandler.postDelayed(::uploadPhoto, 10000) // 5초 후에 uploadPhoto 함수를 실행한다.
    }

//    fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
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

//    fun HttpCheckId() {
//
//    // URL을 만들어 주고
//    val url = "https://myser.run-asia-northeast1.goorm.io/post"
//
//    //데이터를 담아 보낼 바디를 만든다
//    val requestBody : RequestBody = FormBody.Builder()
//      .add("id","지난달 28일 수원에 살고 있는 윤주성 연구원은 코엑스(서울 삼성역)에서 개최되는 DEVIEW 2019 Day1에 참석했다. LaRva팀의 '엄~청 큰 언어 모델 공장 가동기!' 세션을 들으며 언어모델을 학습시킬때 multi-GPU, TPU 모두 써보고 싶다는 생각을 했다.")
//      .build()
//
//    // OkHttp Request 를 만들어준다.
//    val request = Request.Builder()
//      .url(url)
//      .post(requestBody)
//      .build()
//
//    // 클라이언트 생성
//    val client = OkHttpClient()
//
//    Log.d("전송 주소 ","https://myser.run-asia-northeast1.goorm.io/post")
//
//    // 요청 전송
//    client.newCall(request).enqueue(object : Callback {
//
//      override fun onResponse(call: Call, response: Response) {
//        val body = response.body?.string();
//
//
//        Log.d("요청", body!!)
//      }
//
//      override fun onFailure(call: Call, e: IOException) {
//        Log.d("요청","요청 실패 ")
//      }
//
//    })
//}

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