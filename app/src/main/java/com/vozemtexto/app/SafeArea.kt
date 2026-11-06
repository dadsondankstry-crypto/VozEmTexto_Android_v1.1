package com.vozemtexto.app

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.view.WindowInsets

/** Evita que a barra de status ou os botões do sistema cubram o app no Android 15+. */
object SafeArea {
    fun apply(activity: Activity) {
        if (Build.VERSION.SDK_INT < 35) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) ?: return
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
        root.requestApplyInsets()
    }
}
