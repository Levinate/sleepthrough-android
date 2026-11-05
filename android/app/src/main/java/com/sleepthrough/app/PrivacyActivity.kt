package com.sleepthrough.app

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color

class PrivacyActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val text = TextView(this).apply {
            textSize = 18f; setTextColor(Color.rgb(250,244,237)); setPadding(40,60,40,60)
            text = getString(R.string.privacy_notice)
        }
        setContentView(ScrollView(this).apply {setBackgroundColor(Color.rgb(11,27,43));addView(text)})
    }
}
