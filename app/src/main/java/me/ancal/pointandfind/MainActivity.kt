package me.ancal.pointandfind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var serverUrlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var captureButton: Button
    private lateinit var cameraPreview: PreviewView
    private lateinit var wordList: RecyclerView

    private val httpClient = OkHttpClient()
    private var imageCapture: ImageCapture? = null
    private val wordItems = mutableListOf<WordItem>()
    private val wordAdapter = WordAdapter(wordItems)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            statusText.text = "Camera permission denied."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        statusText = findViewById(R.id.statusText)
        captureButton = findViewById(R.id.captureButton)
        cameraPreview = findViewById(R.id.cameraPreview)
        wordList = findViewById(R.id.wordList)

        wordList.layoutManager = LinearLayoutManager(this)
        wordList.adapter = wordAdapter

        captureButton.setOnClickListener {
            takePhoto()
        }

        ensureCameraPermission()
    }

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (ex: Exception) {
                statusText.text = "Camera start failed: ${ex.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PAF_${timeStamp}", ".jpg", storageDir)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture
        if (imageCapture == null) {
            statusText.text = "Camera is not ready."
            return
        }

        val photoFile = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        statusText.text = "Capturing photo..."

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    uploadPhoto(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    statusText.text = "Photo capture failed: ${exception.message}"
                }
            }
        )
    }

    private fun uploadPhoto(photoFile: File) {
        val serverUrl = serverUrlInput.text.toString().trim()
        if (serverUrl.isBlank()) {
            statusText.text = "Missing server URL."
            return
        }

        statusText.text = "Uploading photo..."

        thread {
            try {
                val mediaType = "image/jpeg".toMediaType()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "photo",
                        photoFile.name,
                        RequestBody.create(mediaType, photoFile)
                    )
                    .build()

                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/upload")
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = runCatching { JSONObject(body) }.getOrNull()
                        val items = json?.optJSONArray("items")
                        val newItems = parseItems(items)

                        runOnUiThread {
                            if (newItems.isNotEmpty()) {
                                wordItems.addAll(newItems)
                                wordAdapter.notifyItemRangeInserted(
                                    wordItems.size - newItems.size,
                                    newItems.size
                                )
                                statusText.text = "Saved ${newItems.size} words."
                            } else {
                                statusText.text = "No words found."
                            }
                            Toast.makeText(this, "Upload complete", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            statusText.text = "Upload failed: ${response.code}"
                        }
                    }
                }
            } catch (ex: Exception) {
                runOnUiThread {
                    statusText.text = "Upload error: ${ex.message}"
                }
            }
        }
    }

    private fun parseItems(items: JSONArray?): List<WordItem> {
        if (items == null) return emptyList()
        val results = mutableListOf<WordItem>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val word = item.optString("word").trim()
            val definition = item.optString("definition").trim()
            if (word.isNotEmpty() && definition.isNotEmpty()) {
                results.add(WordItem(word, definition))
            }
        }
        return results
    }
}

private data class WordItem(
    val word: String,
    val definition: String
)

private class WordAdapter(
    private val items: List<WordItem>
) : RecyclerView.Adapter<WordViewHolder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): WordViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class WordViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
    private val wordText: TextView = itemView.findViewById(R.id.wordText)
    private val definitionText: TextView = itemView.findViewById(R.id.definitionText)

    fun bind(item: WordItem) {
        wordText.text = item.word
        definitionText.text = item.definition
    }
}
