package me.ancal.pointandfind

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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

    private val httpClient = OkHttpClient()
    private var currentPhotoFile: File? = null
    private var currentPhotoUri: Uri? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            statusText.text = "Camera permission denied."
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            uploadCurrentPhoto()
        } else {
            statusText.text = "Photo capture cancelled."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverUrlInput = findViewById(R.id.serverUrlInput)
        statusText = findViewById(R.id.statusText)
        captureButton = findViewById(R.id.captureButton)

        captureButton.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoFile = createImageFile()
        currentPhotoFile = photoFile
        val photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            photoFile
        )
        currentPhotoUri = photoUri
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PAF_${timeStamp}", ".jpg", storageDir)
    }

    private fun uploadCurrentPhoto() {
        val photoFile = currentPhotoFile
        val serverUrl = serverUrlInput.text.toString().trim()
        if (photoFile == null || serverUrl.isBlank()) {
            statusText.text = "Missing photo or server URL."
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
                    val message = if (response.isSuccessful) {
                        val json = runCatching { JSONObject(body) }.getOrNull()
                        val received = json?.optBoolean("received") ?: false
                        val filename = json?.optString("filename") ?: ""
                        if (received) {
                            "Server received: $filename"
                        } else {
                            "Upload ok, but server did not confirm receipt."
                        }
                    } else {
                        "Upload failed: ${response.code}"
                    }

                    runOnUiThread {
                        statusText.text = message
                        if (response.isSuccessful) {
                            Toast.makeText(this, "Upload complete", Toast.LENGTH_SHORT).show()
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
}
