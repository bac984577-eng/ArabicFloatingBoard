package com.example.arabicfloatingboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.Stroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager

class FloatingBoardService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var board: DrawingBoardView
    private lateinit var output: EditText
    private lateinit var params: WindowManager.LayoutParams
    private var minimized = false
    private var penColor = Color.rgb(15, 23, 42)
    private var recognizer: com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null
    private var modelReady = false

    override fun onCreate() {
        super.onCreate()
        createNotification()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
        prepareArabicModel()
    }

    private fun createNotification() {
        val channelId = "arabic_board_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "السبورة العائمة", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId).setContentTitle("السبورة العربية العائمة")
                .setContentText("السبورة تعمل. أوقفها من زر الإغلاق.")
                .setSmallIcon(android.R.drawable.ic_menu_edit).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("السبورة العربية العائمة")
                .setContentText("السبورة تعمل. أوقفها من زر الإغلاق.")
                .setSmallIcon(android.R.drawable.ic_menu_edit).build()
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(27, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(27, notification)
    }

    private fun buildPanel() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(79, 70, 229))
        }
        val title = TextView(this).apply {
            text = "السبورة العربية  ⋮ اسحب للتحريك"
            setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f))
        val minimize = Button(this).apply { text = "−"; setOnClickListener { toggleMinimize() } }
        val close = Button(this).apply { text = "×"; setOnClickListener { stopSelf() } }
        header.addView(minimize, LinearLayout.LayoutParams(dp(44), dp(40)))
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(40)))
        header.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = startX + (e.rawX - touchX).toInt(); params.y = startY + (e.rawY - touchY).toInt(); try { wm.updateViewLayout(panel, params) } catch (_: Exception) {}; return true }
                }
                return false
            }
        })
        panel.addView(header)
        board = DrawingBoardView(this).apply { setPenColor(penColor) }
        panel.addView(board, LinearLayout.LayoutParams(ViewGroupParams.MATCH, dp(230)))

        val colors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(2), dp(4), dp(2)) }
        listOf(Color.rgb(15,23,42), Color.rgb(37,99,235), Color.rgb(220,38,38), Color.rgb(22,163,74)).forEach { c ->
            val b = Button(this).apply { text = "●"; setTextColor(c); textSize = 20f; setOnClickListener { penColor = c; board.setPenColor(c) } }
            colors.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        val clear = Button(this).apply { text = "مسح"; setOnClickListener { board.clear(); output.setText("") } }
        colors.addView(clear, LinearLayout.LayoutParams(0, dp(42), 1f))
        panel.addView(colors)

        val recognize = Button(this).apply {
            text = "تعرّف على الكتابة العربية"
            setOnClickListener { recognizeWriting() }
        }
        panel.addView(recognize, LinearLayout.LayoutParams(ViewGroupParams.MATCH, dp(44)))

        output = EditText(this).apply {
            hint = "النص المتعرّف عليه سيظهر هنا ويمكنك تصحيحه"
            textSize = 19f; gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(Color.rgb(15,23,42)); setPadding(dp(8), dp(6), dp(8), dp(6))
            minLines = 2; maxLines = 4
        }
        panel.addView(output, LinearLayout.LayoutParams(ViewGroupParams.MATCH, dp(94)))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val copy = Button(this).apply { text = "نسخ النص"; setOnClickListener { copyText() } }
        val share = Button(this).apply { text = "إرسال إلى تطبيق"; setOnClickListener { shareText() } }
        bottom.addView(copy, LinearLayout.LayoutParams(0, dp(44), 1f)); bottom.addView(share, LinearLayout.LayoutParams(0, dp(44), 1f))
        panel.addView(bottom)

        val fontRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), dp(4)) }
        fontRow.addView(TextView(this).apply { text = "الخط:"; textSize = 13f }, LinearLayout.LayoutParams(dp(50), dp(42)))
        val fontSpinner = Spinner(this)
        val fonts = listOf("Noto Serif / Serif", "Sans Serif", "Monospace")
        fontSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fonts)
        fontSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                output.typeface = when(position) { 1 -> Typeface.SANS_SERIF; 2 -> Typeface.MONOSPACE; else -> Typeface.SERIF }
            }
        }
        fontRow.addView(fontSpinner, LinearLayout.LayoutParams(0, dp(42), 1f))
        panel.addView(fontRow)

        params = WindowManager.LayoutParams(dp(350), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(70) }
        wm.addView(panel, params)
    }

    private fun prepareArabicModel() {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("ar")
        if (identifier == null) {
            toast("تعذر تحديد نموذج التعرف على الكتابة العربية في إصدار المكتبة هذا.")
            return
        }
        model = DigitalInkRecognitionModel.builder(identifier).build()
        val manager = RemoteModelManager.getInstance()
        manager.download(model!!, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady = true
                recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model!!).build())
                toast("نموذج التعرف العربي جاهز")
            }
            .addOnFailureListener { e -> toast("تعذر تنزيل نموذج العربية: ${e.localizedMessage ?: "تحقق من الإنترنت"}") }
    }

    private fun recognizeWriting() {
        if (!board.hasInk()) { toast("اكتب على السبورة أولًا."); return }
        if (!modelReady || recognizer == null) { toast("نموذج العربية لم يجهز بعد. تحقق من اتصال الإنترنت ثم أعد المحاولة."); return }
        val ink = board.toInk()
        recognizer!!.recognize(ink)
            .addOnSuccessListener { result ->
                val candidate = result.candidates.firstOrNull()?.text.orEmpty()
                if (candidate.isBlank()) toast("لم أتمكن من قراءة الكتابة. جرّب الكتابة بوضوح أكبر.")
                else output.setText(candidate)
            }
            .addOnFailureListener { e -> toast("فشل التعرف: ${e.localizedMessage ?: "خطأ غير معروف"}") }
    }

    private fun copyText() {
        val text = output.text.toString().trim()
        if (text.isEmpty()) { toast("لا يوجد نص لنسخه."); return }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("النص العربي", text))
        toast("نُسخ النص. افتح تطبيق الملاحظات والصقه.")
    }

    private fun shareText() {
        val text = output.text.toString().trim()
        if (text.isEmpty()) { toast("لا يوجد نص لإرساله."); return }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(Intent.createChooser(intent, "إرسال النص إلى تطبيق الملاحظات").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toggleMinimize() {
        minimized = !minimized
        for (i in 1 until panel.childCount) panel.getChildAt(i).visibility = if (minimized) View.GONE else View.VISIBLE
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        try { wm.updateViewLayout(panel, params) } catch (_: Exception) {}
    }

    private fun toast(s: String) = android.os.Handler(mainLooper).post { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { try { if (::panel.isInitialized) wm.removeView(panel) } catch (_: Exception) {}; recognizer?.close(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private object ViewGroupParams { const val MATCH = -1 }
}
