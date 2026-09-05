package com.sulat.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.sulat.ai.preview.PreviewActivity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<LinearLayout>(R.id.rootContainer)
        container.removeAllViews()

        val previewButton = Button(this).apply {
            text = "Preview Letter"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        previewButton.setOnClickListener {
            val intent = Intent(this, PreviewActivity::class.java)
            startActivity(intent)
        }
        container.addView(previewButton)

        Toast.makeText(this, "Sulat - Offline Letter Creation", Toast.LENGTH_LONG).show()
    }
}
