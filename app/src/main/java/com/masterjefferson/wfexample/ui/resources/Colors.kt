package com.masterjefferson.wfexample.ui.resources

import android.content.Context
import androidx.annotation.ColorInt
import com.masterjefferson.wfexample.R

object ColorResources {

  lateinit var primary: Color
  lateinit var secondary: Color
  lateinit var surface: Color
  lateinit var background: Color

  fun load(appContext: Context) {
    /* populate color instances with values from colors.xml */
    with(appContext) {
      primary = Color(
        getColor(R.color.colorPrimary),
        getColor(R.color.colorPrimaryVariant),
        getColor(R.color.colorOnPrimary)
      )
      secondary = Color(
        getColor(R.color.colorSecondary),
        getColor(R.color.colorSecondaryVariant),
        getColor(R.color.colorOnSecondary)
      )
      surface = Color(
        getColor(R.color.colorSurface),
        overlayColor = getColor(R.color.colorOnSurface)
      )
      background = Color(
        getColor(R.color.colorBackground),
        overlayColor = getColor(R.color.colorOnBackground)
      )

    }
  }

}

data class Color(@ColorInt val color: Int, @ColorInt val variantColor: Int? = null, @ColorInt val overlayColor: Int? = null)