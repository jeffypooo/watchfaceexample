package com.masterjefferson.wfexample

import android.graphics.*

object BitmapUtils {

  fun createGrayScale(source: Bitmap): Bitmap {
    val gray = Bitmap.createBitmap(
      source.width,
      source.height,
      Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(gray)
    val paint = Paint()
    val matrix = ColorMatrix().apply { setSaturation(0f) }
    val filter = ColorMatrixColorFilter(matrix)
    paint.colorFilter = filter
    canvas.drawBitmap(source, 0f, 0f, paint)
    return gray
  }

}

internal fun Bitmap.copyGrayScale(): Bitmap = BitmapUtils.createGrayScale(this)

internal fun Bitmap.copyScaled(scaleX: Float, scaleY: Float = scaleX): Bitmap = Bitmap.createScaledBitmap(
  this,
  (width * scaleX).toInt(),
  (height * scaleY).toInt(),
  true
)