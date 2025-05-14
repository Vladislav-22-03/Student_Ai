package com.example.student_ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainMenuActivity : AppCompatActivity() {

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            uri?.let {
                val content = readTextFromUri(it)
                openWorkActivityWithContent(content)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)
        val btnCreate = findViewById<Button>(R.id.btnCreateNote)
        val btnOpen = findViewById<Button>(R.id.btnOpenNote)

        btnCreate.setOnClickListener {
            val intent = Intent(this, Work::class.java)
            startActivity(intent)
        }

        btnOpen.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            openFileLauncher.launch(intent)
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.readText()
            inputStream?.close()
            text
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка при открытии файла", Toast.LENGTH_SHORT).show()
            ""
        }
    }

    private fun openWorkActivityWithContent(text: String) {
        val intent = Intent(this, Work::class.java).apply {
            putExtra("EXTRA_NOTE_CONTENT", text)
        }
        startActivity(intent)
    }
}
