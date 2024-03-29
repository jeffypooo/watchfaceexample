package com.masterjefferson.wfexample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.palette.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 5f
private const val MINUTE_STROKE_WIDTH = 3f
private const val SECOND_TICK_STROKE_WIDTH = 2f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

private const val SHADOW_RADIUS = 6f

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

  override fun onCreateEngine(): Engine = Engine()

  private class EngineHandler(reference: Engine) : Handler() {
    private val engineRef: WeakReference<Engine> = WeakReference(reference)

    override fun handleMessage(msg: Message) {
      val engine = engineRef.get()
      if (engine != null) {
        when (msg.what) {
          MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
        }
      }
    }
  }

  inner class Engine : CanvasWatchFaceService.Engine() {

    private lateinit var calendar: Calendar

    private var registeredTimeZoneReceiver = false
    private var muteMode: Boolean = false
    private var centerX: Float = 0F
    private var centerY: Float = 0F

    private var secondHandLength: Float = 0F
    private var minuteHandLength: Float = 0F
    private var hourHandLength: Float = 0F

    /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
    private var watchHandColor: Int = 0
    private var watchHandHighlightColor: Int = 0
    private var watchHandShadowColor: Int = 0

    private lateinit var hourPaint: Paint
    private lateinit var minutePaint: Paint
    private lateinit var secondPaint: Paint
    private lateinit var tickAndCirclePaint: Paint

    private lateinit var backgroundPaint: Paint
    private lateinit var backgroundBitmap: Bitmap
    private lateinit var grayBackgroundBitmap: Bitmap

    private var ambient: Boolean = false
    private var lowBitAmbient: Boolean = false
    private var burnInProtection: Boolean = false

    /* Handler to update the time once a second in interactive mode. */
    private val updateTimeHandler = EngineHandler(this)

    private val timeZoneReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        calendar.timeZone = TimeZone.getDefault()
        invalidate()
      }
    }

    override fun onCreate(holder: SurfaceHolder) {
      super.onCreate(holder)

      setWatchFaceStyle(
          WatchFaceStyle.Builder(this@MyWatchFace)
              .setAcceptsTapEvents(true)
              .build()
      )

      calendar = Calendar.getInstance()

      initializeBackground()
      initializeWatchFace()
    }

    private fun initializeBackground() {
      backgroundPaint = Paint().apply {
        color = Color.BLACK
      }
      backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_goat)

      /* Extracts colors from background image to improve watchface style. */
      Palette.from(backgroundBitmap).generate {
        it?.let {
          watchHandHighlightColor = it.getVibrantColor(Color.RED)
          watchHandColor = it.getLightVibrantColor(Color.WHITE)
          watchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
          updateWatchHandStyle()
        }
      }
    }

    private fun initializeWatchFace() {
      /* Set defaults for colors */
      watchHandColor = Color.WHITE
      watchHandHighlightColor = Color.RED
      watchHandShadowColor = Color.BLACK

      hourPaint = Paint().apply {
        color = watchHandColor
        strokeWidth = HOUR_STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(
            SHADOW_RADIUS, 0f, 0f, watchHandShadowColor
        )
      }

      minutePaint = Paint().apply {
        color = watchHandColor
        strokeWidth = MINUTE_STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(
            SHADOW_RADIUS, 0f, 0f, watchHandShadowColor
        )
      }

      secondPaint = Paint().apply {
        color = watchHandHighlightColor
        strokeWidth = SECOND_TICK_STROKE_WIDTH
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(
            SHADOW_RADIUS, 0f, 0f, watchHandShadowColor
        )
      }

      tickAndCirclePaint = Paint().apply {
        color = watchHandColor
        strokeWidth = SECOND_TICK_STROKE_WIDTH
        isAntiAlias = true
        style = Paint.Style.STROKE
        setShadowLayer(
            SHADOW_RADIUS, 0f, 0f, watchHandShadowColor
        )
      }
    }

    override fun onDestroy() {
      updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
      super.onDestroy()
    }

    override fun onPropertiesChanged(properties: Bundle) {
      super.onPropertiesChanged(properties)
      lowBitAmbient = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
      burnInProtection = properties.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
    }

    override fun onTimeTick() {
      super.onTimeTick()
      invalidate()
    }

    override fun onAmbientModeChanged(inAmbientMode: Boolean) {
      super.onAmbientModeChanged(inAmbientMode)
      ambient = inAmbientMode

      updateWatchHandStyle()

      // Check and trigger whether or not timer should be running (only
      // in active mode).
      updateTimer()
    }

    private fun updateWatchHandStyle() {
      if (ambient) {
        hourPaint.color = Color.WHITE
        minutePaint.color = Color.WHITE
        secondPaint.color = Color.WHITE
        tickAndCirclePaint.color = Color.WHITE

        hourPaint.isAntiAlias = false
        minutePaint.isAntiAlias = false
        secondPaint.isAntiAlias = false
        tickAndCirclePaint.isAntiAlias = false

        hourPaint.clearShadowLayer()
        minutePaint.clearShadowLayer()
        secondPaint.clearShadowLayer()
        tickAndCirclePaint.clearShadowLayer()
      } else {
        hourPaint.color = watchHandColor
        minutePaint.color = watchHandColor
        secondPaint.color = watchHandHighlightColor
        tickAndCirclePaint.color = watchHandColor

        hourPaint.isAntiAlias = true
        minutePaint.isAntiAlias = true
        secondPaint.isAntiAlias = true
        tickAndCirclePaint.isAntiAlias = true

        hourPaint.setShadowLayer(SHADOW_RADIUS, 0f, 0f, watchHandShadowColor)
        minutePaint.setShadowLayer(SHADOW_RADIUS, 0f, 0f, watchHandShadowColor)
        secondPaint.setShadowLayer(SHADOW_RADIUS, 0f, 0f, watchHandShadowColor)
        tickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0f, 0f, watchHandShadowColor)
      }
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) {
      super.onInterruptionFilterChanged(interruptionFilter)
      val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

      /* Dim display in mute mode. */
      if (muteMode != inMuteMode) {
        muteMode = inMuteMode
        hourPaint.alpha = if (inMuteMode) 100 else 255
        minutePaint.alpha = if (inMuteMode) 100 else 255
        secondPaint.alpha = if (inMuteMode) 80 else 255
        invalidate()
      }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
      super.onSurfaceChanged(holder, format, width, height)

      /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
      centerX = width / 2f
      centerY = height / 2f

      /*
             * Calculate lengths of different hands based on watch screen size.
             */
      secondHandLength = (centerX * 0.875).toFloat()
      minuteHandLength = (centerX * 0.75).toFloat()
      hourHandLength = (centerX * 0.5).toFloat()

      /* Scale loaded background image (more efficient) if surface dimensions change. */
      val scale = width.toFloat() / backgroundBitmap.width.toFloat()

      backgroundBitmap = Bitmap.createScaledBitmap(
          backgroundBitmap,
          (backgroundBitmap.width * scale).toInt(),
          (backgroundBitmap.height * scale).toInt(), true
      )

      /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
      if (!burnInProtection && !lowBitAmbient) {
        initGrayBackgroundBitmap()
      }
    }

    private fun initGrayBackgroundBitmap() {
      grayBackgroundBitmap = Bitmap.createBitmap(
          backgroundBitmap.width,
          backgroundBitmap.height,
          Bitmap.Config.ARGB_8888
      )
      val canvas = Canvas(grayBackgroundBitmap)
      val grayPaint = Paint()
      val colorMatrix = ColorMatrix()
      colorMatrix.setSaturation(0f)
      val filter = ColorMatrixColorFilter(colorMatrix)
      grayPaint.colorFilter = filter
      canvas.drawBitmap(backgroundBitmap, 0f, 0f, grayPaint)
    }

    /**
     * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
     * used for implementing specific logic to handle the gesture.
     */
    override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
      when (tapType) {
        WatchFaceService.TAP_TYPE_TOUCH        -> {
          // The user has started touching the screen.
        }
        WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
          // The user has started a different gesture or otherwise cancelled the tap.
        }
        WatchFaceService.TAP_TYPE_TAP          ->
          // The user has completed the tap gesture.
          // TODO: Add code to handle the tap gesture.
          Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
              .show()
      }
      invalidate()
    }

    override fun onDraw(canvas: Canvas, bounds: Rect) {
      val now = System.currentTimeMillis()
      calendar.timeInMillis = now

      drawBackground(canvas)
      drawWatchFace(canvas)
    }

    private fun drawBackground(canvas: Canvas) {

      if (ambient && (lowBitAmbient || burnInProtection)) {
        canvas.drawColor(Color.BLACK)
      } else if (ambient) {
        canvas.drawBitmap(grayBackgroundBitmap, 0f, 0f, backgroundPaint)
      } else {
        canvas.drawBitmap(backgroundBitmap, 0f, 0f, backgroundPaint)
      }
    }

    private fun drawWatchFace(canvas: Canvas) {

      /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
      val innerTickRadius = centerX - 10
      val outerTickRadius = centerX
      for (tickIndex in 0..11) {
        val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
        val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
        val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
        val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
        val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
        canvas.drawLine(
            centerX + innerX, centerY + innerY,
            centerX + outerX, centerY + outerY, tickAndCirclePaint
        )
      }

      /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
      val seconds =
          calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f
      val secondsRotation = seconds * 6f

      val minutesRotation = calendar.get(Calendar.MINUTE) * 6f

      val hourHandOffset = calendar.get(Calendar.MINUTE) / 2f
      val hoursRotation = calendar.get(Calendar.HOUR) * 30 + hourHandOffset

      /*
             * Save the canvas state before we can begin to rotate it.
             */
      canvas.save()

      canvas.rotate(hoursRotation, centerX, centerY)
      canvas.drawLine(
          centerX,
          centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
          centerX,
          centerY - hourHandLength,
          hourPaint
      )

      canvas.rotate(minutesRotation - hoursRotation, centerX, centerY)
      canvas.drawLine(
          centerX,
          centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
          centerX,
          centerY - minuteHandLength,
          minutePaint
      )

      /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
      if (!ambient) {
        canvas.rotate(secondsRotation - minutesRotation, centerX, centerY)
        canvas.drawLine(
            centerX,
            centerY - CENTER_GAP_AND_CIRCLE_RADIUS,
            centerX,
            centerY - secondHandLength,
            secondPaint
        )
      }
      canvas.drawCircle(
          centerX,
          centerY,
          CENTER_GAP_AND_CIRCLE_RADIUS,
          tickAndCirclePaint
      )

      /* Restore the canvas' original orientation. */
      canvas.restore()
    }

    override fun onVisibilityChanged(visible: Boolean) {
      super.onVisibilityChanged(visible)

      if (visible) {
        registerReceiver()
        /* Update time zone in case it changed while we weren't visible. */
        calendar.timeZone = TimeZone.getDefault()
        invalidate()
      } else {
        unregisterReceiver()
      }

      /* Check and trigger whether or not timer should be running (only in active mode). */
      updateTimer()
    }

    private fun registerReceiver() {
      if (registeredTimeZoneReceiver) {
        return
      }
      registeredTimeZoneReceiver = true
      val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
      this@MyWatchFace.registerReceiver(timeZoneReceiver, filter)
    }

    private fun unregisterReceiver() {
      if (!registeredTimeZoneReceiver) {
        return
      }
      registeredTimeZoneReceiver = false
      this@MyWatchFace.unregisterReceiver(timeZoneReceiver)
    }

    /**
     * Starts/stops the [.updateTimeHandler] timer based on the state of the watch face.
     */
    private fun updateTimer() {
      updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
      if (shouldTimerBeRunning()) {
        updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
      }
    }

    /**
     * Returns whether the [.updateTimeHandler] timer should be running. The timer
     * should only run in active mode.
     */
    private fun shouldTimerBeRunning(): Boolean {
      return isVisible && !ambient
    }

    /**
     * Handle updating the time periodically in interactive mode.
     */
    fun handleUpdateTimeMessage() {
      invalidate()
      if (shouldTimerBeRunning()) {
        val timeMs = System.currentTimeMillis()
        val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
        updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
      }
    }
  }
}


