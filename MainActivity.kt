package com.example.arabicfloatingboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
        root.addView(TextView(this).apply {
            text = "السبورة العربية العائمة\n\nاكتب بالقلم أو الإصبع، ثم اضغط «تعرّف على الكتابة». يستخدم التطبيق محرك ML Kit الحقيقي للتعرّف على الحبر الرقمي باللغة العربية."
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(0xFF0F172A.toInt())
            setPadding(0, 0, 0, 24)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply {
            text = "تشغيل السبورة العائمة"
            setOnClickListener { requestAndStart() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "ملاحظة: يلزم السماح بالظهور فوق التطبيقات الأخرى. قد يحتاج نموذج العربية إلى التنزيل أول مرة، والتعرّف يعتمد على وضوح الخط."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF475569.toInt())
            setPadding(0, 20, 0, 0)
        })
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    private fun requestAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "فعّل السماح بالظهور فوق التطبيقات، ثم ارجع واضغط تشغيل.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FloatingBoardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "تم تشغيل السبورة العائمة", Toast.LENGTH_SHORT).show()
        finish()
    }
}
