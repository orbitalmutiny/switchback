package com.spop.poverlay.overlay

import android.view.Gravity

enum class OverlayLocation(val gravity: Int) {
    Top(Gravity.TOP or Gravity.CENTER_HORIZONTAL),
    Bottom(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
    Left(Gravity.START or Gravity.CENTER_VERTICAL),
    Right(Gravity.END or Gravity.CENTER_VERTICAL);

    val isHorizontal: Boolean
        get() = this == Top || this == Bottom
}
