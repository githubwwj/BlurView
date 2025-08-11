package com.eightbitlab.blurview_sample

import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object StatusBarsUtil {

    @JvmStatic
    fun setEdgeToEdge(statusBarView: View, paddingRoot: View) {
        paddingRoot.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(statusBarView) { v: View, insets: WindowInsetsCompat ->
            val statusInsets =
                insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val layoutParams = v.layoutParams
            layoutParams.height = statusInsets.top
            v.layoutParams = layoutParams
            val naviInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            paddingRoot.setPadding(naviInsets.left, naviInsets.top, naviInsets.right, naviInsets.bottom)
            insets
        }
    }

    @JvmStatic
    @JvmOverloads
    fun setEdgeToEdge(view: View, isTopPadding: Boolean = true, isBottomPadding: Boolean = true) {
        view.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->
            val insetsCompat =
                insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (isTopPadding && isBottomPadding) {
                v.setPadding(insetsCompat.left, insetsCompat.top, insetsCompat.right, insetsCompat.bottom)
            } else if (isTopPadding) {
                v.setPadding(insetsCompat.left, insetsCompat.top, insetsCompat.right, 0)
            } else if (isBottomPadding) {
                v.setPadding(insetsCompat.left, 0, insetsCompat.right, insetsCompat.bottom)
            }
            insets
        }
    }

    @JvmStatic
    fun setEdgeTopAndBottomMargin(topMarginView: View, bottomPaddingView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(topMarginView) { v: View, insets: WindowInsetsCompat ->
            val topMargin =
                insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            val topLp = v.layoutParams as ViewGroup.MarginLayoutParams
            topLp.topMargin = topMargin
            v.layoutParams = topLp

            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            bottomPaddingView.setPadding(navInsets.left, navInsets.top, navInsets.right, navInsets.bottom)
            insets
        }
    }

    @JvmStatic
    fun setEdgeWithStatusBar(topMarginView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(topMarginView) { v: View, insets: WindowInsetsCompat ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val layoutParams = v.layoutParams
            layoutParams.height = statusInsets.top
            v.layoutParams = layoutParams
            insets
        }
    }

    @JvmStatic
    @JvmOverloads
    fun setEdgeWithNavigatorBar(topMarginView: View, isHeight: Boolean = false) {
        ViewCompat.setOnApplyWindowInsetsListener(topMarginView) { v: View, insets: WindowInsetsCompat ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            if (isHeight) {
                val layoutParams = v.layoutParams
                layoutParams.height = navInsets.bottom
                v.layoutParams = layoutParams
            } else {
                v.setPadding(navInsets.left, navInsets.top, navInsets.right, navInsets.bottom)
            }
            insets
        }
    }


    @JvmStatic
    fun dp2px(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, Resources.getSystem().displayMetrics)
    }

}