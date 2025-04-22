package com.example.student_ai

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class FullscreenTextActivity : AppCompatActivity() {

    private lateinit var etFullscreenText: EditText
    private var originalText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_text)

        etFullscreenText = findViewById(R.id.etFullscreenText)

        // Получаем исходный текст
        val textContent = intent.getStringExtra("TEXT_CONTENT") ?: ""
        etFullscreenText.setText(textContent)
        originalText = textContent

        // Слушатель для сохранения текста при потере фокуса (если нужно)
        etFullscreenText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveText()
            }
        }
    }

    // Сохранить изменения в текст и передать обратно
    private fun saveText() {
        val editedText = etFullscreenText.text.toString()
        if (editedText != originalText) {
            // Создаём Intent для передачи данных обратно
            val resultIntent = Intent()
            resultIntent.putExtra("EDITED_TEXT", editedText)
            setResult(RESULT_OK, resultIntent)
        }
    }

    override fun onBackPressed() {
        saveText() // Сохраняем изменения перед выходом
        super.onBackPressed()
    }
}
