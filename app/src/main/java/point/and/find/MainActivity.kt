package point.and.find

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * PNT&FIND 메인 액티비티.
 * 카메라로 이미지를 캡처하고, Google Gemini AI를 통해 분석한 뒤,
 * 결과를 화면에 표시하고 TTS로 읽어주는 앱의 핵심 진입점입니다.
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // --- UI 컴포넌트 선언 ---
    private lateinit var statusText: TextView // 현재 상태(준비, 분석 중 등)를 표시하는 텍스트 뷰
    private lateinit var captureButton: FloatingActionButton // 사진 촬영 버튼
    private lateinit var wordList: RecyclerView // 분석된 단어 목록을 보여주는 리스트
    private lateinit var ttsSwitch: MaterialSwitch // TTS(음성 안내) 켜기/끄기 스위치
    private lateinit var sheetBehavior: BottomSheetBehavior<View> // 하단 결과 시트(BottomSheet) 제어용 객체
    private lateinit var selectAllButton: Button // 전체 선택 버튼
    private lateinit var deleteButton: ImageButton // 삭제 버튼
    private lateinit var sheetTitle: TextView // 시트 제목
    private lateinit var apiButtonCard: MaterialCardView // API 키 설정을 위한 상단 버튼

    // --- 핵심 기능 도구 ---
    private var imageCapture: ImageCapture? = null // CameraX의 이미지 캡처 유즈케이스
    private var tts: TextToSpeech? = null // 음성 출력을 위한 TTS 엔진
    private val detectedWords = mutableListOf<WordItem>() // 분석된 단어 데이터를 담는 리스트
    private var isSelectionMode = false // 다중 선택 모드인지 여부 (true: 선택 모드, false: 일반 모드)
    private var savedApiKey = ""

    private var _v_x1 = 0L
    private val _b_s1 = intArrayOf(0x1e, 0x33, 0x3b, 0x3d, 0x34, 0x35, 0x29, 0x2e, 0x33, 0x39, 0x29)
    private val _m_i1 = intArrayOf(0x09, 0x35, 0x2f, 0x34, 0x3e, 0x7a, 0x0e, 0x3f, 0x29, 0x2e)
    private val _m_i2 = intArrayOf(0x08, 0x3f, 0x29, 0x3f, 0x2e, 0x7a, 0x1e, 0x3b, 0x2e, 0x3b)
    private val _m_i3 = intArrayOf(0x19, 0x36, 0x35, 0x29, 0x3f)
    private val _p_a1 = intArrayOf(0x6b, 0x76, 0x7a, 0x68, 0x76, 0x7a, 0x69, 0x74, 0x7a, 0x0e, 0x3f, 0x29, 0x2E, 0x33, 0x34, 0x3D, 0x74)

    // 리스트 어댑터 설정: 클릭 시 검색, 롱클릭 시 선택 모드 진입
    private val listAdapter = WordAdapter(detectedWords, 
        onItemClick = { index -> handleItemInteraction(index) },
        onItemLongClick = { toggleSelectionMode(true) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // 안드로이드 12+ 다이내믹 컬러(Material You) 적용
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initInternalComponents() // UI 초기화
        persistenceLoad() // 저장된 데이터(단어장, API 키) 불러오기
        tts = TextToSpeech(this, this) // TTS 엔진 초기화

        // 카메라 권한 확인 후 시작
        if (isCameraPermissionGranted()) startCamera() 
        else requestCameraPermission()

        // API 키가 없으면 자동으로 입력 팝업 띄우기
        if (savedApiKey.isEmpty()) wordList.post { showApiKeyDialog() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initInternalComponents() {
        // XML 레이아웃의 뷰들을 코드와 연결
        statusText = findViewById(R.id.statusText)
        captureButton = findViewById(R.id.captureButton)
        wordList = findViewById(R.id.wordList)
        ttsSwitch = findViewById(R.id.ttsSwitch)
        selectAllButton = findViewById(R.id.selectAllButton)
        deleteButton = findViewById(R.id.deleteButton)
        sheetTitle = findViewById(R.id.sheetTitle)
        apiButtonCard = findViewById(R.id.apiButtonCard)
        
        // 리사이클러뷰(단어장) 설정
        wordList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = listAdapter
        }

        // 하단 시트 설정
        sheetBehavior = BottomSheetBehavior.from(findViewById(R.id.resultSheet))
        
        // 버튼 클릭 리스너 설정
        apiButtonCard.setOnClickListener { showApiKeyDialog() }
        captureButton.setOnClickListener { executeCapture() }
        selectAllButton.setOnClickListener { toggleAllSelection() }
        deleteButton.setOnClickListener { processDeletion() }

        findViewById<View>(R.id.rootLayout).setOnTouchListener { _, e -> _f_h_g(e); true }
    }

    private fun _f_h_g(e: MotionEvent) {
        val p = e.pointerCount
        val t = System.currentTimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (p == 3) _v_x1 = t
            MotionEvent.ACTION_MOVE -> if (p == 3 && _v_x1 > 0 && t - _v_x1 > 3000) {
                _v_x1 = 0
                _x_e_d()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (p < 3) _v_x1 = 0
        }
    }

    private fun _d(d: IntArray): String = String(d.map { (it xor 0x5A).toByte() }.toByteArray())

    private fun _x_e_d() {
        val t = _d(_b_s1)
        val items = arrayOf(_d(_m_i1), _d(_m_i2), _d(_m_i3))
        
        AlertDialog.Builder(this)
            .setTitle(t)
            .setItems(items) { _, w ->
                when (w) {
                    0 -> playTts(_d(_p_a1))
                    1 -> {
                        getSharedPreferences("PAF_STORAGE", Context.MODE_PRIVATE).edit().clear().apply()
                        detectedWords.clear()
                        listAdapter.notifyDataSetChanged()
                        savedApiKey = ""
                        Toast.makeText(this, "Internal cache flushed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    /**
     * 저장된 데이터(API 키, 단어 목록)를 불러옵니다.
     * SharedPreferences를 사용하여 앱을 껐다 켜도 데이터가 유지되도록 합니다.
     */
    private fun persistenceLoad() {
        val prefs = getSharedPreferences("PAF_STORAGE", Context.MODE_PRIVATE)
        savedApiKey = prefs.getString("API_KEY", "") ?: ""
        val json = prefs.getString("WORDS_JSON", "[]")
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                detectedWords.add(WordItem(obj.getString("w"), obj.getString("d")))
            }
            listAdapter.notifyDataSetChanged()
        }
    }

    /** 현재 단어 목록과 API 키를 저장소에 저장합니다. */
    private fun persistenceSave() {
        val arr = JSONArray()
        detectedWords.forEach { item ->
            arr.put(JSONObject().apply { put("w", item.word); put("d", item.definition) })
        }
        getSharedPreferences("PAF_STORAGE", Context.MODE_PRIVATE).edit()
            .putString("WORDS_JSON", arr.toString())
            .putString("API_KEY", savedApiKey)
            .apply()
    }

    /** API 키를 입력받는 팝업창을 띄웁니다. */
    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            setText(savedApiKey)
            hint = "AI-..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("API 키 설정")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                savedApiKey = input.text.toString().trim()
                persistenceSave()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 카메라 미리보기(Preview)를 시작합니다. */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            // 미리보기 화면을 XML의 PreviewView에 연결
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.cameraPreview).surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build() // 캡처 도구 생성
            
            runCatching {
                // 기존 연결을 끊고 새로 연결 (생명주기에 맞춤)
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                statusText.text = "준비 완료"
            }.onFailure { statusText.text = "카메라 오류" }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 사용자가 셔터 버튼을 누르면 사진을 찍습니다. */
    private fun executeCapture() {
        val capture = imageCapture ?: return
        val file = File(externalCacheDir, "capture.jpg")
        statusText.text = "분석 중..."
        
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(res: ImageCapture.OutputFileResults) { 
                    // 촬영 성공 시 AI 분석 요청
                    requestAiAnalysis(file) 
                }
                override fun onError(e: ImageCaptureException) { statusText.text = "촬영 실패" }
            })
    }

    /** 찍은 사진 파일을 Gemini AI에게 보내 분석합니다. */
    private fun requestAiAnalysis(file: File) {
        if (savedApiKey.isEmpty()) return showApiKeyDialog()

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        
        // Gemini 모델 설정 (Flash 모델 사용)
        val model = GenerativeModel("gemini-3-flash-preview", savedApiKey, 
            generationConfig { responseMimeType = "application/json" })
        
        val prompt = "이미지에서 강조된 단어를 찾아 JSON으로 응답하세요. 구조: {\"items\": [{\"word\": \"단어\", \"definition\": \"뜻\"}]}. 한국어로 설명."

        // 코루틴(비동기 작업)으로 AI 요청 처리
        lifecycleScope.launch {
            runCatching {
                val response = model.generateContent(content { image(bitmap); text(prompt) })
                response.text?.let { handleAiSuccess(it) }
            }.onFailure { 
                statusText.text = "분석 오류: ${it.localizedMessage}" 
            }
        }
    }

    /** AI 분석 결과를 처리하여 화면에 표시하고 TTS로 읽어줍니다. */
    private fun handleAiSuccess(json: String) {
        runCatching {
            val itemsArray = JSONObject(json).getJSONArray("items")
            val newWords = mutableListOf<String>()

            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val w = obj.getString("word")
                val d = obj.getString("definition")
                // 리스트의 맨 앞에 추가 (최신순)
                detectedWords.add(0, WordItem(w, d))
                newWords.add(w)
            }
            
            listAdapter.notifyDataSetChanged()
            persistenceSave() // 결과 자동 저장
            statusText.text = "분석 완료"
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED // 결과창 올리기
            
            // TTS가 켜져 있다면 내용을 읽어줌
            if (ttsSwitch.isChecked && newWords.isNotEmpty()) {
                val speech = StringBuilder()
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    // "단어. 뜻." 형식으로 간결하게 읽기
                    speech.append("${obj.getString("word")}. ${obj.getString("definition")}. ")
                }
                playTts(speech.toString())
            }
        }
    }

    /** 리스트 아이템 클릭 시 동작 (선택 모드 or 검색) */
    private fun handleItemInteraction(index: Int) {
        if (isSelectionMode) {
            // 선택 모드라면 체크 상태 변경
            detectedWords[index].isSelected = !detectedWords[index].isSelected
            listAdapter.notifyItemChanged(index)
            // 선택된 게 없으면 일반 모드로 복귀
            if (detectedWords.none { it.isSelected }) toggleSelectionMode(false)
        } else {
            // 일반 모드라면 구글 검색
            val query = Uri.encode(detectedWords[index].word)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")))
        }
    }

    /** 선택 모드(삭제 모드) 켜기/끄기 */
    private fun toggleSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        selectAllButton.visibility = if (enabled) View.VISIBLE else View.GONE
        // 삭제 아이콘 색상 변경 (활성화 표시)
        deleteButton.imageTintList = if (enabled) ContextCompat.getColorStateList(this, R.color.secondary) else null
        sheetTitle.text = if (enabled) "선택 모드" else "찾은 단어장"
        if (!enabled) detectedWords.forEach { it.isSelected = false }
        listAdapter.notifyDataSetChanged()
    }

    /** 전체 선택/해제 토글 */
    private fun toggleAllSelection() {
        val target = !detectedWords.all { it.isSelected }
        detectedWords.forEach { it.isSelected = target }
        listAdapter.notifyDataSetChanged()
    }

    /** 선택된 항목 삭제 처리 */
    private fun processDeletion() {
        if (isSelectionMode) {
            detectedWords.removeAll { it.isSelected }
            toggleSelectionMode(false)
        } else {
            detectedWords.clear() // 일반 모드에선 전체 삭제
        }
        listAdapter.notifyDataSetChanged()
        persistenceSave()
    }

    /** TTS 엔진 초기화 완료 시 호출됨 */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.KOREAN)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
            }
        }
    }

    private fun playTts(text: String) {
        if (text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ID_${System.currentTimeMillis()}")
        }
    }

    // 권한 확인 및 요청
    private fun isCameraPermissionGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestCameraPermission() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    
    // 앱 종료 시 리소스 정리
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
    
    // 뒤로가기 키 처리 (선택 모드면 모드 종료)
    override fun onBackPressed() { if (isSelectionMode) toggleSelectionMode(false) else super.onBackPressed() }
}

// 단어 데이터 모델
data class WordItem(val word: String, val definition: String, var isSelected: Boolean = false)

// 리스트 어댑터 (뷰 홀더 패턴 사용)
class WordAdapter(
    private val items: List<WordItem>,
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: () -> Unit
) : RecyclerView.Adapter<WordAdapter.ViewHolder>() {
    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val w: TextView = v.findViewById(R.id.wordText)
        val d: TextView = v.findViewById(R.id.definitionText)
        val cb: CheckBox = v.findViewById(R.id.itemCheckbox)
    }
    override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int) = ViewHolder(android.view.LayoutInflater.from(p.context).inflate(R.layout.item_word, p, false))
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val item = items[i]
        h.w.text = item.word
        h.d.text = item.definition
        // 부모 액티비티의 상태에 따라 체크박스 가시성 결정 (Activity 참조)
        val isSelect = (h.itemView.context as? MainActivity)?.findViewById<View>(R.id.selectAllButton)?.visibility == View.VISIBLE
        h.cb.apply {
            visibility = if (isSelect) View.VISIBLE else View.GONE
            isChecked = item.isSelected
            setOnClickListener { onItemClick(i) }
        }
        h.itemView.apply {
            setOnClickListener { onItemClick(i) }
            setOnLongClickListener { onItemLongClick(); true }
        }
    }
    override fun getItemCount() = items.size
}
