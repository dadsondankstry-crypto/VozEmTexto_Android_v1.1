package com.vozemtexto.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Janela flutuante opcional, expansível/retrátil, sem acessibilidade ou root. */
class FloatingTranscript(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var body: TextView? = null
    private var panel: LinearLayout? = null
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val observer: () -> Unit = { update() }

    fun show() {
        if (root != null) { update(); return }
        val container = FrameLayout(context)
        val expandedPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(11))
            background = backgroundShape(18)
            elevation = dp(10).toFloat()
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "●  AO VIVO   ·   Arraste para mover"
            setTextColor(Color.rgb(77, 214, 183))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(1), dp(4), 0, dp(7))
        }
        val collapseButton = TextView(context).apply {
            text = "—"
            contentDescription = "Recolher janela em um botão flutuante"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(13), 0, dp(3), dp(5))
            setOnClickListener { collapse() }
        }
        top.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(collapseButton)
        expandedPanel.addView(top)
        val transcript = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 5
            text = "Aguardando palavras…"
        }
        body = transcript
        expandedPanel.addView(transcript)
        container.addView(expandedPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val minimizedBubble = TextView(context).apply {
            text = "✎"
            contentDescription = "Mostrar transcrição flutuante"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = backgroundShape(16)
            elevation = dp(12).toFloat()
            visibility = View.GONE
            setOnClickListener { expand() }
        }
        container.addView(minimizedBubble,
            FrameLayout.LayoutParams(dp(58), dp(58)))
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(45)
        }
        // O cabeçalho pode ser arrastado; não precisa abrir o aplicativo.
        title.setOnTouchListener(object : View.OnTouchListener {
            private var downY = 0f
            private var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        startY = layout.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layout.y = (startY + (downY - event.rawY)).toInt().coerceAtLeast(0)
                        try { manager.updateViewLayout(container, layout) } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })
        try {
            manager.addView(container, layout)
            root = container
            panel = expandedPanel
            bubble = minimizedBubble
            params = layout
            AppState.listen(observer)
            update()
        } catch (_: Exception) {
            body = null
            root = null
            panel = null
            bubble = null
            params = null
        }
    }

    private fun collapse() {
        panel?.visibility = View.GONE
        bubble?.visibility = View.VISIBLE
        params?.apply {
            width = dp(58)
            height = dp(58)
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(18)
            y = dp(100)
        }
        root?.let { view -> params?.let { layout ->
            try { manager.updateViewLayout(view, layout) } catch (_: Exception) { }
        } }
    }

    private fun expand() {
        bubble?.visibility = View.GONE
        panel?.visibility = View.VISIBLE
        params?.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(45)
        }
        root?.let { view -> params?.let { layout ->
            try { manager.updateViewLayout(view, layout) } catch (_: Exception) { }
        } }
    }

    fun hide() {
        AppState.unlisten(observer)
        root?.let { try { manager.removeView(it) } catch (_: Exception) { } }
        root = null
        body = null
        panel = null
        bubble = null
        params = null
    }

    private fun update() {
        val words = AppState.committed.trim().lines().takeLast(3).joinToString("\n")
        val partial = AppState.partial
        body?.text = (words + if (partial.isNotBlank()) "\n› $partial" else "")
            .trim().ifEmpty { "Aguardando palavras…" }
    }

    private fun backgroundShape(radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.argb(244, 25, 29, 51))
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), Color.rgb(139, 117, 247))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
