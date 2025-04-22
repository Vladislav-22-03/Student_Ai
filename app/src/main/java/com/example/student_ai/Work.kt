package com.example.student_ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class Work : AppCompatActivity() {
    private lateinit var etInput: EditText
    private lateinit var btnMic: ImageButton
    private lateinit var btnSaveNote: Button
    private val speechRequestCode = 100
    private val fullscreenRequestCode = 200 // Новый код запроса для FullscreenTextActivity

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
        enableEdgeToEdge()
        setContentView(R.layout.work_page)

        etInput = findViewById(R.id.etInput)
        btnMic = findViewById(R.id.btnMic)
        btnSaveNote = findViewById(R.id.btnSaveNote)

        // Загружаем текст, если открываем существующую заметку
        val incomingText = intent.getStringExtra("EXTRA_NOTE_CONTENT")
        if (!incomingText.isNullOrEmpty()) {
            etInput.setText(incomingText)
            originalText = incomingText
        } else {
            originalText = ""
        }

        btnMic.setOnClickListener { checkAndRequestMicrophonePermission() }
        btnSaveNote.setOnClickListener { promptUserToChooseFileLocation() }

        etInput.setOnClickListener(object : DoubleClickListener() {
            override fun onDoubleClick(v: View) {
                openFullscreenText()
            }
        })
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
            putExtra("TEXT_CONTENT", etInput.text.toString()) // Передаем текст для редактирования
        }
        startActivityForResult(intent, fullscreenRequestCode) // Ожидаем результат
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
        val textToSave = etInput.text.toString()
        if (textToSave.isEmpty()) {
            Toast.makeText(this, "Введите текст для сохранения", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(textToSave.toByteArray())
            }
            Toast.makeText(this, "Файл сохранен", Toast.LENGTH_LONG).show()
            originalText = textToSave // Сохранили — обновляем оригинал
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при сохранении: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Обрабатываем результат от FullscreenTextActivity
        if (requestCode == fullscreenRequestCode && resultCode == RESULT_OK) {
            val editedText = data?.getStringExtra("EDITED_TEXT") ?: ""
            etInput.setText(editedText) // Обновляем текст в поле ввода
        }

        // Обрабатываем голосовой ввод
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
