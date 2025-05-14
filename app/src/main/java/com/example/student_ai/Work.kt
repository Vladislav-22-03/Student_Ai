package com.example.student_ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.student_ai.network.RetrofitInstance
import retrofit2.*
import java.text.SimpleDateFormat
import java.util.*

class Work : AppCompatActivity() {
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSaveNote: Button
    private lateinit var btnProcessText: Button
    private lateinit var tvSummary: TextView

    private val speechRequestCode = 100
    private val fullscreenRequestCode = 200

    private var originalText: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startVoiceInput()
        else Toast.makeText(this, "Разрешение на микрофон отклонено", Toast.LENGTH_SHORT).show()
    }

    private val createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> saveTextToUri(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.work_page)

        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSaveNote = findViewById(R.id.btnSaveNote)
        btnProcessText = findViewById(R.id.btnProcessText)
        tvSummary = findViewById(R.id.tvSummary)

        val incomingText = intent.getStringExtra("EXTRA_NOTE_CONTENT")
        if (!incomingText.isNullOrEmpty()) {
            etInput.setText(incomingText)
            originalText = incomingText
            if (tvSummary.text.isNotEmpty()) {
                tvSummary.visibility = View.VISIBLE
            }
        } else {
            originalText = ""
        }

        btnMic.setOnClickListener { checkAndRequestMicrophonePermission() }
        btnSaveNote.setOnClickListener { promptUserToChooseFileLocation() }

        btnProcessText.setOnClickListener {
            val inputText = etInput.text.toString()
            if (inputText.isNotEmpty()) {
                processTextWithTextGears(inputText)
            } else {
                Toast.makeText(this, "Сначала введите текст", Toast.LENGTH_SHORT).show()
            }
        }

        tvSummary.setOnClickListener {
            val summaryText = tvSummary.text.toString()
            if (summaryText.isNotEmpty()) {
                openFullscreenSummary(summaryText)
            } else {
                Toast.makeText(this, "Сначала получите сокращение", Toast.LENGTH_SHORT).show()
            }
        }

        etInput.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick(v: View) {
                openFullscreenText()
            }
        })
    }

    private fun openFullscreenSummary(summaryText: String) {
        if (summaryText.isEmpty()) {
            Toast.makeText(this, "Сначала получите сокращение", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, FullscreenTextActivity::class.java).apply {
            putExtra("TEXT_CONTENT", summaryText)
        }
        startActivity(intent)
    }


    private fun checkAndRequestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            Toast.makeText(this, "Микрофон не поддерживается", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            startActivityForResult(intent, speechRequestCode)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Голосовой ввод не поддерживается на вашем устройстве", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFullscreenText() {
        if (etInput.text.isNullOrEmpty()) {
            Toast.makeText(this, "Введите текст сначала", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, FullscreenTextActivity::class.java).apply {
            putExtra("TEXT_CONTENT", etInput.text.toString())
        }
        startActivityForResult(intent, fullscreenRequestCode)
    }

    private fun promptUserToChooseFileLocation() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "note_$timeStamp.txt")
        }
        createFileLauncher.launch(intent)
    }

    private fun saveTextToUri(uri: Uri) {
        // Составляем текст для сохранения
        val textToSave = buildString {
            append(etInput.text.toString()) // Основной текст из EditText
            if (tvSummary.visibility == View.VISIBLE && tvSummary.text.isNotEmpty()) {
                append("\n\n--- Сокращение ---\n") // Разделитель между основным текстом и сокращением
                append(tvSummary.text.toString()) // Сокращённый текст
            }
        }

        // Если текст пустой, показываем ошибку
        if (textToSave.isEmpty()) {
            Toast.makeText(this, "Введите текст для сохранения", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Открываем поток для записи в файл и записываем текст
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(textToSave.toByteArray())
            }
            Toast.makeText(this, "Файл сохранен", Toast.LENGTH_LONG).show()
            originalText = etInput.text.toString() // Обновляем оригинальный текст
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при сохранении: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == fullscreenRequestCode && resultCode == RESULT_OK) {
            val editedText = data?.getStringExtra("EDITED_TEXT") ?: ""
            etInput.setText(editedText)
        }

        if (requestCode == speechRequestCode && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.let {
                val spokenText = it[0]
                val cursorPosition = etInput.selectionStart
                etInput.text?.insert(cursorPosition, spokenText)
            }
        }
    }

    override fun onBackPressed() {
        val currentText = etInput.text.toString()
        if (currentText != originalText) {
            AlertDialog.Builder(this)
                .setTitle("Есть несохранённые изменения")
                .setMessage("Вы уверены, что хотите выйти без сохранения?")
                .setPositiveButton("Выйти") { _, _ -> super.onBackPressed() }
                .setNegativeButton("Остаться", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun processTextWithTextGears(text: String) {
        val apiKey = "ux0V4k4NuYZDXsPv"
        val api = RetrofitInstance.api
        Log.d("API_REQUEST", "Отправка запроса на обработку текста: $text")

        api.summarizeText(text, apiKey, "ru-RU").enqueue(object : Callback<Map<String, Any>> {
            override fun onResponse(call: Call<Map<String, Any>>, response: Response<Map<String, Any>>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d("API_RAW_RESPONSE", responseBody.toString())

                    val status = responseBody?.get("status") as? Boolean
                    if (status == true) {
                        val data = responseBody["response"] as? Map<*, *>
                        val summary = data?.get("summary") as? List<*>
                        val keywords = data?.get("keywords") as? List<*>

                        if (summary != null) {
                            var summaryText = summary.joinToString("\n") { it.toString() }

                            // Отображаем сокращённый текст в tvSummary
                            tvSummary.text = summaryText
                            tvSummary.visibility = View.VISIBLE // Делаем tvSummary видимым

                            // Если есть ключевые слова, обрабатываем текст и выделяем их капсом
                            if (keywords != null) {
                                val processedText = processTextWithCaps(text, keywords)

                                // Обновляем текст в EditText, делая ключевые слова капсом
                                etInput.setText(processedText)
                                originalText = processedText
                            } else {
                                etInput.setText("Не удалось получить ключевые слова.")
                            }
                        } else {
                            etInput.setText("Обработка не удалась.")
                        }
                    } else {
                        etInput.setText("Ошибка от API: ${response.code()}")
                        Log.e("API_ERROR", "Ошибка ответа от API: ${response.code()} - ${response.message()}")
                    }
                } else {
                    etInput.setText("Ошибка от API: ${response.code()}")
                    Log.e("API_ERROR", "Ошибка ответа от API: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<Map<String, Any>>, t: Throwable) {
                etInput.setText("Ошибка обработки текста: ${t.localizedMessage}")
                Log.e("API_ERROR", "Ошибка запроса: ${t.localizedMessage}")
            }
        })
    }

    private fun processTextWithCaps(originalText: String, keywords: List<*>) : String {
        var processedText = originalText

        keywords.forEach { keyword ->
            val keywordStr = keyword.toString()

            var startIndex = 0
            // Ищем все вхождения ключевого слова в тексте
            while (startIndex < processedText.length) {
                startIndex = processedText.indexOf(keywordStr, startIndex, ignoreCase = true)
                if (startIndex == -1) break

                // Заменяем на капс
                val keywordUppercase = keywordStr.uppercase()

                // Заменяем слово на капс-версию
                processedText = processedText.replaceRange(startIndex, startIndex + keywordStr.length, keywordUppercase)

                startIndex += keywordStr.length
            }
        }

        return processedText
    }
}

abstract class DoubleClickListener : View.OnClickListener {
    private var lastClickTime: Long = 0
    companion object {
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 300
    }

    override fun onClick(v: View) {
        val clickTime = System.currentTimeMillis()
        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
            onDoubleClick(v)
        }
        lastClickTime = clickTime
    }

    abstract fun onDoubleClick(v: View)
}
