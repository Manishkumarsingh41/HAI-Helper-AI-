
package com.manish.helperai

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import java.util.concurrent.atomic.AtomicBoolean

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


class HelperAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "HelperAI"
        private const val SCAN_ANIMATION_COLOR = 0xFF7CFFB2.toInt()

        private const val OCR_DELAY_MS = 1200L

        private const val MIN_SCREENSHOT_INTERVAL_MS = 1500L

        private const val MIN_TEXT_LENGTH = 2

        const val ACTION_SHOW_FLOATING_BUTTON =
            "com.manish.helperai.SHOW_FLOATING_BUTTON"

        const val ACTION_HIDE_FLOATING_BUTTON =
            "com.manish.helperai.HIDE_FLOATING_BUTTON"
    }


    // =========================================================
    // OCR
    // =========================================================

    private val textRecognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )


    // =========================================================
    // HANDLER
    // =========================================================

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )


    // =========================================================
    // WINDOW MANAGER
    // =========================================================

    private var windowManager: WindowManager? = null


    // =========================================================
    // FLOATING BUTTON
    // =========================================================

    private var floatingButton: ImageView? = null

    private var floatingButtonParams:
            WindowManager.LayoutParams? = null


    // =========================================================
    // POPUP
    // =========================================================

    private var scanPopup: PopupWindow? = null


    // =========================================================
    // SELECTED AREA
    // =========================================================

    private var selectionOverlay: SelectionOverlayView? = null

    private var selectionFrame: SelectionFrameView? = null

    private var selectionParams:
            WindowManager.LayoutParams? = null

    // =========================================================
    // SCANNING ANIMATION OVERLAY
    // =========================================================

    private var scanAnimationOverlay:
            ScanAnimationView? = null

    private var scanAnimationParams:
            WindowManager.LayoutParams? = null

    private var selectedAreaMode = false


    private var selectedRect =
        Rect(
            100,
            300,
            900,
            900
        )


    // =========================================================
    // SCAN STATE
    // =========================================================

    private var scanEnabled = false

    private var serviceConnected = false

    private var ocrScheduled = false

    private var lastScreenshotTime = 0L

    private var lastOcrText = ""

    private var lastPackageName = ""

    private var scanMode =
        ScanMode.NONE


    private enum class ScanMode {

        NONE,

        FULL_SCREEN,

        SELECTED_AREA
    }


    private val ocrRunning =
        AtomicBoolean(false)


    // =========================================================
    // BROADCAST RECEIVER
    // =========================================================

    private val commandReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (intent == null) {
                    return
                }


                when (intent.action) {

                    ACTION_SHOW_FLOATING_BUTTON -> {

                        Log.d(
                            TAG,
                            "COMMAND: SHOW FLOATING BUTTON"
                        )

                        showFloatingButton()
                    }


                    ACTION_HIDE_FLOATING_BUTTON -> {

                        Log.d(
                            TAG,
                            "COMMAND: HIDE FLOATING BUTTON"
                        )

                        hideFloatingButton()
                    }
                }
            }
        }


    // =========================================================
    // SERVICE CONNECTED
    // =========================================================

    override fun onServiceConnected() {

        super.onServiceConnected()

        serviceConnected = true


        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        try {

            val filter =
                IntentFilter()


            filter.addAction(
                ACTION_SHOW_FLOATING_BUTTON
            )


            filter.addAction(
                ACTION_HIDE_FLOATING_BUTTON
            )


            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                registerReceiver(
                    commandReceiver,
                    filter,
                    RECEIVER_NOT_EXPORTED
                )

            } else {

                @Suppress("DEPRECATION")

                registerReceiver(
                    commandReceiver,
                    filter
                )
            }

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "RECEIVER REGISTER ERROR",
                exception
            )
        }


        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE CONNECTED"
        )

        Log.d(
            TAG,
            "UNIVERSAL SCREEN OCR READY"
        )

        Log.d(
            TAG,
            "FLOATING HAI LOGO READY"
        )

        Log.d(
            TAG,
            "SCAN MODE = OFF"
        )

        Log.d(
            TAG,
            "================================"
        )
    }


    // =========================================================
    // ACCESSIBILITY EVENT
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (
            event == null ||
            !serviceConnected
        ) {

            return
        }


        if (!scanEnabled) {

            return
        }


        val packageName =
            event.packageName
                ?.toString()
                ?: return


        // -----------------------------------------------------
        // NEVER SCAN HELPERAI
        // -----------------------------------------------------

        if (
            packageName ==
            this.packageName
        ) {

            return
        }


        lastPackageName =
            packageName


        Log.d(
            TAG,
            "EVENT type=${event.eventType} " +
                    "package=$packageName"
        )


        when (
            event.eventType
        ) {

            AccessibilityEvent.TYPE_VIEW_SCROLLED,

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {

                Log.d(
                    TAG,
                    "SCAN EVENT -> OCR SCHEDULED"
                )

                scheduleOcr()
            }
        }
    }


    // =========================================================
    // SCHEDULE OCR
    // =========================================================

    private fun scheduleOcr() {

        if (
            !serviceConnected ||
            !scanEnabled ||
            ocrScheduled
        ) {

            return
        }


        val now =
            System.currentTimeMillis()


        val elapsed =
            now -
                    lastScreenshotTime


        val extraDelay =
            if (
                elapsed <
                MIN_SCREENSHOT_INTERVAL_MS
            ) {

                MIN_SCREENSHOT_INTERVAL_MS -
                        elapsed

            } else {

                0L
            }


        ocrScheduled = true


        mainHandler.postDelayed({

            ocrScheduled = false


            if (scanEnabled) {

                captureScreenForOcr()
            }

        }, OCR_DELAY_MS + extraDelay)
    }


    // =========================================================
    // CAPTURE SCREEN
    // =========================================================

    private fun captureScreenForOcr() {

        if (
            !serviceConnected ||
            !scanEnabled
        ) {

            return
        }


        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R
        ) {

            Log.d(
                TAG,
                "SCREENSHOT REQUIRES ANDROID 11+"
            )

            return
        }


        if (ocrRunning.get()) {

            Log.d(
                TAG,
                "OCR ALREADY RUNNING"
            )

            return
        }


        if (
            lastPackageName ==
            this.packageName
        ) {

            Log.d(
                TAG,
                "HELPERAI DETECTED -> OCR SKIPPED"
            )

            return
        }


        val now =
            System.currentTimeMillis()


        if (
            now -
            lastScreenshotTime <
            MIN_SCREENSHOT_INTERVAL_MS
        ) {

            return
        }


        lastScreenshotTime =
            now


        if (
            !ocrRunning.compareAndSet(
                false,
                true
            )
        ) {

            return
        }


        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "STARTING SCAN"
        )

        Log.d(
            TAG,
            "SCAN MODE = $scanMode"
        )

        Log.d(
            TAG,
            "SOURCE PACKAGE = $lastPackageName"
        )

        Log.d(
            TAG,
            "================================"
        )


        val shouldCrop =
            scanMode ==
                    ScanMode.SELECTED_AREA


        // -----------------------------------------------------
        // Remove selection frame before screenshot.
        //
        // IMPORTANT:
        // removeSelectionOverlayTemporarily() now also clears
        // the stored View reference.
        // -----------------------------------------------------

        // Hide every visual overlay for the actual screenshot so
        // the animation/selection frame is never included in OCR.
        hideScanAnimation()

        if (shouldCrop) {

            removeSelectionOverlayTemporarily()
        }


        try {

            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,

                object : TakeScreenshotCallback {

                    override fun onSuccess(
                        screenshot: ScreenshotResult
                    ) {

                        Log.d(
                            TAG,
                            "SCREENSHOT SUCCESS"
                        )


                        processScreenshot(
                            screenshot,
                            shouldCrop
                        )
                    }


                    override fun onFailure(
                        errorCode: Int
                    ) {

                        Log.e(
                            TAG,
                            "SCREENSHOT FAILED code=$errorCode"
                        )


                        ocrRunning.set(
                            false
                        )


                        mainHandler.post {

                            if (shouldCrop) {
                                restoreSelectionOverlay()
                            }

                            showScanAnimation()
                        }
                    }
                }
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "SCREENSHOT EXCEPTION",
                exception
            )


            ocrRunning.set(
                false
            )


            // Selection/animation overlays are restored only after
            // ML Kit finishes. This keeps the screenshot clean.
            // runOcr() handles that asynchronous lifecycle.
        }
    }


    // =========================================================
    // PROCESS SCREENSHOT
    // =========================================================

    private fun processScreenshot(
        screenshot: ScreenshotResult,
        shouldCrop: Boolean
    ) {

        var bitmap: Bitmap? = null

        var finalBitmap: Bitmap? = null


        try {

            val hardwareBuffer =
                screenshot.hardwareBuffer


            if (
                hardwareBuffer == null
            ) {

                Log.e(
                    TAG,
                    "HARDWARE BUFFER NULL"
                )


                ocrRunning.set(
                    false
                )

                mainHandler.post {
                    if (shouldCrop) {
                        restoreSelectionOverlay()
                    }
                    showScanAnimation()
                }


                return
            }


            try {

                val hardwareBitmap =
                    Bitmap.wrapHardwareBuffer(
                        hardwareBuffer,
                        screenshot.colorSpace
                    )


                if (
                    hardwareBitmap == null
                ) {

                    Log.e(
                        TAG,
                        "BITMAP CREATION FAILED"
                    )


                    ocrRunning.set(
                        false
                    )


                    return
                }


                bitmap =
                    hardwareBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    )


                if (
                    bitmap == null
                ) {

                    Log.e(
                        TAG,
                        "SOFTWARE BITMAP FAILED"
                    )


                    ocrRunning.set(
                        false
                    )


                    return
                }


                Log.d(
                    TAG,
                    "SCREENSHOT BITMAP = " +
                            "${bitmap!!.width}x${bitmap!!.height}"
                )

            } finally {

                hardwareBuffer.close()
            }


            // -------------------------------------------------
            // CROP SELECTED AREA
            // -------------------------------------------------

            finalBitmap =
                if (shouldCrop) {

                    cropToSelectedArea(
                        bitmap!!
                    )

                } else {

                    bitmap
                }


            if (
                finalBitmap == null
            ) {

                ocrRunning.set(
                    false
                )

                mainHandler.post {
                    if (shouldCrop) {
                        restoreSelectionOverlay()
                    }
                    showScanAnimation()
                }


                return
            }


            // runOcr() takes ownership of finalBitmap and recycles it
            // only after ML Kit finishes. Do not recycle it here.
            runOcr(
                finalBitmap,
                shouldCrop
            )


            if (
                finalBitmap !== bitmap
            ) {

                // The cropped bitmap belongs to OCR. The original
                // screenshot bitmap is no longer needed.
                recycleBitmapSafely(bitmap!!)
            }


            finalBitmap = null
            bitmap = null

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "SCREENSHOT PROCESSING ERROR",
                exception
            )


            ocrRunning.set(
                false
            )

        } finally {

            try {

                bitmap?.recycle()

            } catch (
                _: Exception
            ) {
            }


            try {

                finalBitmap?.recycle()

            } catch (
                _: Exception
            ) {
            }


            // The asynchronous OCR operation owns final bitmap cleanup
            // and overlay restoration.

        }
    }


    // =========================================================
    // CROP SELECTED AREA
    // =========================================================

    private fun cropToSelectedArea(
        source: Bitmap
    ): Bitmap? {

        try {

            val frame =
                Rect(selectedRect)


            val sourceWidth =
                source.width


            val sourceHeight =
                source.height


            val left =
                frame.left.coerceIn(
                    0,
                    max(
                        0,
                        sourceWidth - 1
                    )
                )


            val top =
                frame.top.coerceIn(
                    0,
                    max(
                        0,
                        sourceHeight - 1
                    )
                )


            val right =
                frame.right.coerceIn(
                    left + 1,
                    sourceWidth
                )


            val bottom =
                frame.bottom.coerceIn(
                    top + 1,
                    sourceHeight
                )


            val width =
                right - left


            val height =
                bottom - top


            if (
                width < 10 ||
                height < 10
            ) {

                Log.e(
                    TAG,
                    "SELECTED AREA TOO SMALL"
                )


                return null
            }


            Log.d(
                TAG,
                "CROPPING AREA = " +
                        "$left,$top - $right,$bottom"
            )


            return Bitmap.createBitmap(
                source,
                left,
                top,
                width,
                height
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "CROP ERROR",
                exception
            )


            return null
        }
    }


    // =========================================================
    // OCR
    // =========================================================

    private fun runOcr(
        bitmap: Bitmap,
        shouldCrop: Boolean
    ) {

        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "OCR START"
        )

        Log.d(
            TAG,
            "================================"
        )


        try {

            val inputImage =
                InputImage.fromBitmap(
                    bitmap,
                    0
                )


            textRecognizer
                .process(inputImage)

                .addOnSuccessListener { result ->

                    val rawText =
                        result.text


                    val fullText =
                        formatOcrText(
                            rawText
                        )


                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "OCR SUCCESS"
                    )

                    Log.d(
                        TAG,
                        "OCR TEXT LENGTH = ${fullText.length}"
                    )


                    if (
                        fullText.length <
                        MIN_TEXT_LENGTH
                    ) {

                        Log.d(
                            TAG,
                            "OCR TEXT TOO SHORT"
                        )


                        return@addOnSuccessListener
                    }


                    // -------------------------------------------------
                    // Ignore exactly identical OCR result
                    // -------------------------------------------------

                    if (
                        fullText ==
                        lastOcrText
                    ) {

                        Log.d(
                            TAG,
                            "OCR SCREEN UNCHANGED"
                        )


                        return@addOnSuccessListener
                    }


                    lastOcrText =
                        fullText


                    Log.d(
                        TAG,
                        "----- OCR RESULT -----"
                    )


                    logTextInChunks(
                        fullText
                    )


                    Log.d(
                        TAG,
                        "----- END OCR RESULT -----"
                    )


                    // -------------------------------------------------
                    // SEND RESULT TO MAIN APP
                    // -------------------------------------------------

                    HelperAIResponseStore
                        .updateResponse(
                            fullText
                        )


                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "NEW RESPONSE CAPTURED"
                    )

                    Log.d(
                        TAG,
                        "RESPONSE SENT TO HELPER AI STORE"
                    )

                    Log.d(
                        TAG,
                        "SOURCE PACKAGE = $lastPackageName"
                    )

                    Log.d(
                        TAG,
                        "================================"
                    )
                }

                .addOnFailureListener { exception ->

                    Log.e(
                        TAG,
                        "OCR FAILED",
                        exception
                    )
                }

                .addOnCompleteListener {

                    // ML Kit is completely finished with the bitmap now.
                    // The visual scanning layer can safely return.
                    ocrRunning.set(
                        false
                    )

                    if (scanEnabled) {

                        mainHandler.post {

                            if (shouldCrop) {
                                restoreSelectionOverlay()
                            }

                            showScanAnimation()
                        }
                    }


                    Log.d(
                        TAG,
                        "OCR PROCESS COMPLETE"
                    )
                }

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "OCR START ERROR",
                exception
            )


            ocrRunning.set(
                false
            )

            if (scanEnabled) {
                mainHandler.post {
                    if (shouldCrop) {
                        restoreSelectionOverlay()
                    }
                    showScanAnimation()
                }
            }
        }
    }


    private fun recycleBitmapSafely(
        bitmap: Bitmap
    ) {

        try {

            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

        } catch (exception: Exception) {

            Log.d(
                TAG,
                "BITMAP RECYCLE ERROR",
                exception
            )
        }
    }


    // =========================================================
    // OCR TEXT FORMATTING
    // =========================================================

    private fun formatOcrText(
        text: String
    ): String {

        if (
            text.isBlank()
        ) {

            return ""
        }


        val lines =
            text
                .replace(
                    "\r\n",
                    "\n"
                )
                .replace(
                    "\r",
                    "\n"
                )
                .lines()


        val output =
            StringBuilder()


        for (
        rawLine in lines
        ) {

            val line =
                rawLine
                    .replace(
                        Regex("\\s+"),
                        " "
                    )
                    .trim()


            if (
                line.isEmpty()
            ) {

                continue
            }


            if (
                output.isNotEmpty()
            ) {

                output.append(
                    "\n"
                )
            }


            output.append(
                line
            )
        }


        return output
            .toString()
            .trim()
    }


    // =========================================================
    // FLOATING HAI LOGO
    // =========================================================

    private fun showFloatingButton() {

        if (!serviceConnected) {

            Log.d(
                TAG,
                "SERVICE NOT CONNECTED"
            )


            return
        }


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M
        ) {

            if (
                !Settings.canDrawOverlays(
                    this
                )
            ) {

                Log.e(
                    TAG,
                    "OVERLAY PERMISSION NOT GRANTED"
                )


                return
            }
        }


        if (
            floatingButton != null
        ) {

            Log.d(
                TAG,
                "FLOATING BUTTON ALREADY SHOWN"
            )


            return
        }


        val wm =
            windowManager
                ?: return


        // =====================================================
        // IMAGE BUTTON
        // =====================================================

        val button =
            ImageView(this)


        button.setImageResource(
            R.drawable.helper_ai_logo
        )


        button.scaleType =
            ImageView.ScaleType.CENTER_CROP


        // =====================================================
        // CIRCULAR CLIPPING
        // =====================================================

        button.outlineProvider =
            object : ViewOutlineProvider() {

                override fun getOutline(
                    view: View,
                    outline: Outline
                ) {

                    outline.setOval(
                        0,
                        0,
                        view.width,
                        view.height
                    )
                }
            }


        button.clipToOutline =
            true


        button.elevation =
            16f


        // =====================================================
        // TOUCH / DRAG
        // =====================================================

        button.setOnTouchListener(
            object : View.OnTouchListener {

                private var initialX =
                    0

                private var initialY =
                    0


                private var initialTouchX =
                    0f


                private var initialTouchY =
                    0f


                private var moved =
                    false


                override fun onTouch(
                    view: View?,
                    event: MotionEvent?
                ): Boolean {

                    if (
                        event == null
                    ) {

                        return false
                    }


                    when (
                        event.action
                    ) {

                        MotionEvent.ACTION_DOWN -> {

                            val params =
                                floatingButtonParams
                                    ?: return false


                            initialX =
                                params.x


                            initialY =
                                params.y


                            initialTouchX =
                                event.rawX


                            initialTouchY =
                                event.rawY


                            moved =
                                false


                            return true
                        }


                        MotionEvent.ACTION_MOVE -> {

                            val params =
                                floatingButtonParams
                                    ?: return false


                            val dx =
                                (
                                        event.rawX -
                                                initialTouchX
                                        ).toInt()


                            val dy =
                                (
                                        event.rawY -
                                                initialTouchY
                                        ).toInt()


                            if (
                                abs(dx) > 10 ||
                                abs(dy) > 10
                            ) {

                                moved =
                                    true
                            }


                            params.x =
                                initialX + dx


                            params.y =
                                initialY + dy


                            try {

                                wm.updateViewLayout(
                                    button,
                                    params
                                )

                            } catch (
                                exception: Exception
                            ) {

                                Log.e(
                                    TAG,
                                    "FLOATING BUTTON MOVE ERROR",
                                    exception
                                )
                            }


                            return true
                        }


                        MotionEvent.ACTION_UP -> {

                            if (!moved) {

                                showScanPopup(
                                    button
                                )
                            }


                            return true
                        }
                    }


                    return false
                }
            }
        )


        // =====================================================
        // WINDOW TYPE
        // =====================================================

        val overlayType =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")

                WindowManager.LayoutParams.TYPE_PHONE
            }


        // =====================================================
        // WINDOW PARAMS
        // =====================================================

        val params =
            WindowManager.LayoutParams(

                68,

                68,

                overlayType,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.TOP or
                    Gravity.END


        params.x =
            20


        params.y =
            300


        try {

            wm.addView(
                button,
                params
            )


            floatingButton =
                button


            floatingButtonParams =
                params


            Log.d(
                TAG,
                "================================"
            )

            Log.d(
                TAG,
                "CIRCULAR HAI LOGO SHOWN"
            )

            Log.d(
                TAG,
                "================================"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "FLOATING BUTTON ADD ERROR",
                exception
            )
        }
    }


    // =========================================================
    // SCAN POPUP
    // =========================================================

    private fun showScanPopup(
        anchor: View
    ) {

        if (
            scanPopup?.isShowing == true
        ) {

            scanPopup?.dismiss()

            return
        }


        val container =
            LinearLayout(this)


        container.orientation =
            LinearLayout.VERTICAL


        container.setPadding(
            24,
            18,
            24,
            18
        )


        val background =
            GradientDrawable()


        background.setColor(
            Color.rgb(
                35,
                36,
                42
            )
        )


        background.cornerRadius =
            28f


        container.background =
            background


        // =====================================================
        // TITLE
        // =====================================================

        val title =
            TextView(this)


        title.text =
            "Scan Screen"


        title.textSize =
            18f


        title.setTextColor(
            0xFFF25CBE.toInt()
        )


        title.setPadding(
            0,
            0,
            0,
            12
        )


        container.addView(
            title
        )


        // =====================================================
        // FULL SCREEN
        // =====================================================

        val fullButton =
            createPopupButton(
                "Full Screen"
            )


        fullButton.setOnClickListener {

            scanPopup?.dismiss()

            startFullScreenScan()
        }


        container.addView(
            fullButton
        )


        // =====================================================
        // SELECTED AREA
        // =====================================================

        val selectedButton =
            createPopupButton(
                "Selected Area"
            )


        selectedButton.setOnClickListener {

            scanPopup?.dismiss()

            startSelectedAreaSelection()
        }


        container.addView(
            selectedButton
        )


        // =====================================================
        // STOP SCAN
        // =====================================================

        val stopButton =
            createPopupButton(
                "Stop Scan"
            )


        stopButton.setOnClickListener {

            scanPopup?.dismiss()

            stopScan()
        }


        container.addView(
            stopButton
        )


        // =====================================================
        // POPUP
        // =====================================================

        val popup =
            PopupWindow(
                container,
                280,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
            )


        popup.setBackgroundDrawable(
            ColorDrawable(
                Color.TRANSPARENT
            )
        )


        popup.isOutsideTouchable =
            true


        popup.elevation =
            18f


        scanPopup =
            popup


        popup.showAtLocation(
            anchor,
            Gravity.CENTER,
            0,
            0
        )
    }


    // =========================================================
    // POPUP BUTTON
    // =========================================================

    private fun createPopupButton(
        text: String
    ): TextView {

        val button =
            TextView(this)


        button.text =
            text


        button.textSize =
            16f


        button.setTextColor(
            0xFFF25CBE.toInt()
        )


        button.gravity =
            Gravity.CENTER


        button.setPadding(
            16,
            18,
            16,
            18
        )


        val background =
            GradientDrawable()


        background.setColor(
            Color.rgb(
                55,
                57,
                64
            )
        )


        background.cornerRadius =
            18f


        button.background =
            background


        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )


        params.setMargins(
            0,
            6,
            0,
            6
        )


        button.layoutParams =
            params


        return button
    }


    // =========================================================
    // FULL SCREEN SCAN
    // =========================================================

    private fun startFullScreenScan() {

        selectedAreaMode =
            false


        scanMode =
            ScanMode.FULL_SCREEN


        scanEnabled =
            true


        removeSelectionOverlay()


        HelperAIResponseStore
            .clearResponse()


        lastOcrText =
            ""


        lastScreenshotTime =
            0L

        showScanAnimation()


        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "FULL SCREEN SCAN = ON"
        )

        Log.d(
            TAG,
            "SCROLL TO COLLECT TEXT"
        )

        Log.d(
            TAG,
            "================================"
        )
    }


    // =========================================================
    // SELECTED AREA SELECTION
    // =========================================================

    private fun startSelectedAreaSelection() {

        stopScan()


        scanMode =
            ScanMode.SELECTED_AREA


        selectedAreaMode =
            true


        HelperAIResponseStore
            .clearResponse()


        lastOcrText =
            ""


        lastScreenshotTime =
            0L


        showSelectionOverlay()
    }


    // =========================================================
    // SHOW SELECTION OVERLAY
    // =========================================================

    private fun showSelectionOverlay() {

        if (
            selectionOverlay != null
        ) {

            return
        }


        val wm =
            windowManager
                ?: return


        val root =
            SelectionOverlayView(
                this
            )


        selectionFrame =
            root.frameView


        root.frameView.setRect(
            selectedRect
        )


        root.frameView.onSelectionChanged =
            { rect ->

                selectedRect =
                    Rect(rect)

                scanAnimationOverlay?.setScanRect(
                    Rect(selectedRect)
                )
            }


        root.frameView.onScan =
            {

                selectedRect =
                    root.frameView.getRect()


                removeSelectionOverlay()


                scanEnabled =
                    true


                scanMode =
                    ScanMode.SELECTED_AREA


                selectedAreaMode =
                    true


                lastOcrText =
                    ""


                lastScreenshotTime =
                    0L


                Log.d(
                    TAG,
                    "================================"
                )

                Log.d(
                    TAG,
                    "SELECTED AREA SCAN = ON"
                )

                Log.d(
                    TAG,
                    "AREA = $selectedRect"
                )

                Log.d(
                    TAG,
                    "SCROLL TO COLLECT TEXT"
                )

                Log.d(
                    TAG,
                    "================================"
                )


                // -------------------------------------------------
                // Start the area-limited scanning animation.
                // -------------------------------------------------

                showScanAnimation()

                // -------------------------------------------------
                // Immediately scan the selected area once.
                // Later scrolling/content changes trigger more OCR.
                // -------------------------------------------------

                scheduleOcr()
            }


        root.frameView.onCancel =
            {

                removeSelectionOverlay()


                scanEnabled =
                    false


                scanMode =
                    ScanMode.NONE


                selectedAreaMode =
                    false
            }


        val overlayType =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")

                WindowManager.LayoutParams.TYPE_PHONE
            }


        val params =
            WindowManager.LayoutParams(

                WindowManager.LayoutParams.MATCH_PARENT,

                WindowManager.LayoutParams.MATCH_PARENT,

                overlayType,

                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

                PixelFormat.TRANSLUCENT
            )


        params.gravity =
            Gravity.TOP or
                    Gravity.START


        try {

            wm.addView(
                root,
                params
            )


            // -------------------------------------------------
            // IMPORTANT:
            // Store the View ONLY after addView succeeds.
            // -------------------------------------------------

            selectionOverlay =
                root


            selectionParams =
                params


            Log.d(
                TAG,
                "SELECTION FRAME SHOWN"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "SELECTION OVERLAY ERROR",
                exception
            )


            selectionOverlay =
                null


            selectionFrame =
                null


            selectionParams =
                null
        }
    }


    // =========================================================
    // REMOVE SELECTION OVERLAY
    // =========================================================

    private fun removeSelectionOverlay() {

        val overlay =
            selectionOverlay


        // -----------------------------------------------------
        // Clear references FIRST.
        //
        // This prevents a second removeView() call from using
        // an already detached View.
        // -----------------------------------------------------

        selectionOverlay =
            null


        selectionFrame =
            null


        selectionParams =
            null


        if (
            overlay == null
        ) {

            return
        }


        try {

            windowManager?.removeViewImmediate(
                overlay
            )


            Log.d(
                TAG,
                "SELECTION OVERLAY REMOVED"
            )

        } catch (
            exception: IllegalArgumentException
        ) {

            // -------------------------------------------------
            // View was already removed.
            // This is no longer treated as a real error.
            // -------------------------------------------------

            Log.d(
                TAG,
                "SELECTION OVERLAY ALREADY REMOVED"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "SELECTION REMOVE ERROR",
                exception
            )
        }
    }


    // =========================================================
    // TEMPORARILY REMOVE FRAME FOR SCREENSHOT
    // =========================================================

    private fun removeSelectionOverlayTemporarily() {

        val overlay =
            selectionOverlay


        if (
            overlay == null
        ) {

            return
        }


        // -----------------------------------------------------
        // Clear state BEFORE removing the View.
        // -----------------------------------------------------

        selectionOverlay =
            null


        selectionFrame =
            null


        selectionParams =
            null


        try {

            windowManager?.removeViewImmediate(
                overlay
            )


            Log.d(
                TAG,
                "SELECTION FRAME TEMPORARILY REMOVED"
            )

        } catch (
            exception: IllegalArgumentException
        ) {

            Log.d(
                TAG,
                "SELECTION FRAME ALREADY REMOVED"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "TEMP SELECTION REMOVE ERROR",
                exception
            )
        }
    }


    // =========================================================
    // RESTORE FRAME
    // =========================================================

    private fun restoreSelectionOverlay() {

        if (
            !selectedAreaMode
        ) {

            return
        }


        if (
            !scanEnabled
        ) {

            return
        }


        if (
            scanMode !=
            ScanMode.SELECTED_AREA
        ) {

            return
        }


        if (
            selectionOverlay != null
        ) {

            return
        }


        showSelectionOverlay()
    }


    // =========================================================
    // SCANNING ANIMATION
    // =========================================================

    private fun showScanAnimation() {

        if (!scanEnabled) {
            return
        }

        val wm =
            windowManager
                ?: return

        val isSelectedArea =
            scanMode == ScanMode.SELECTED_AREA

        val existing =
            scanAnimationOverlay

        if (existing != null) {
            existing.setScanRect(
                if (isSelectedArea) Rect(selectedRect) else null
            )
            existing.startAnimation()
            return
        }

        val animationView =
            ScanAnimationView(this)

        animationView.setScanRect(
            if (isSelectedArea) Rect(selectedRect) else null
        )

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

        params.gravity =
            Gravity.TOP or Gravity.START

        try {

            wm.addView(
                animationView,
                params
            )

            scanAnimationOverlay =
                animationView

            scanAnimationParams =
                params

            animationView.startAnimation()

            Log.d(
                TAG,
                if (isSelectedArea) {
                    "SELECTED AREA SCANNING ANIMATION ON"
                } else {
                    "FULL SCREEN SCANNING ANIMATION ON"
                }
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "SCAN ANIMATION ADD ERROR",
                exception
            )
        }
    }


    private fun hideScanAnimation() {

        val animationView =
            scanAnimationOverlay

        scanAnimationOverlay =
            null

        scanAnimationParams =
            null

        if (animationView == null) {
            return
        }

        animationView.stopAnimation()

        try {

            windowManager?.removeViewImmediate(
                animationView
            )

        } catch (exception: IllegalArgumentException) {

            Log.d(
                TAG,
                "SCAN ANIMATION ALREADY REMOVED"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "SCAN ANIMATION REMOVE ERROR",
                exception
            )
        }
    }


    // =========================================================
    // STOP SCAN
    // =========================================================

    private fun stopScan() {

        scanEnabled =
            false


        scanMode =
            ScanMode.NONE


        selectedAreaMode =
            false

        hideScanAnimation()


        ocrScheduled =
            false


        mainHandler.removeCallbacksAndMessages(
            null
        )


        removeSelectionOverlay()


        Log.d(
            TAG,
            "================================"
        )

        Log.d(
            TAG,
            "SCAN MODE = OFF"
        )

        Log.d(
            TAG,
            "OCR COLLECTION STOPPED"
        )

        Log.d(
            TAG,
            "================================"
        )
    }


    // =========================================================
    // HIDE FLOATING BUTTON
    // =========================================================

    private fun hideFloatingButton() {

        stopScan()


        scanPopup?.dismiss()

        scanPopup =
            null


        val button =
            floatingButton


        floatingButton =
            null


        floatingButtonParams =
            null


        if (
            button == null
        ) {

            return
        }


        try {

            windowManager?.removeViewImmediate(
                button
            )

        } catch (
            exception: IllegalArgumentException
        ) {

            Log.d(
                TAG,
                "FLOATING BUTTON ALREADY REMOVED"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "FLOATING BUTTON REMOVE ERROR",
                exception
            )
        }


        Log.d(
            TAG,
            "FLOATING HAI BUTTON HIDDEN"
        )
    }


    // =========================================================
    // LONG TEXT LOGGING
    // =========================================================

    private fun logTextInChunks(
        text: String
    ) {

        val chunkSize =
            3000


        var start =
            0


        while (
            start <
            text.length
        ) {

            val end =
                minOf(
                    start + chunkSize,
                    text.length
                )


            Log.d(
                TAG,
                text.substring(
                    start,
                    end
                )
            )


            start =
                end
        }
    }


    // =========================================================
    // INTERRUPT
    // =========================================================

    override fun onInterrupt() {

        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE INTERRUPTED"
        )
    }


    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        scanEnabled =
            false


        serviceConnected =
            false


        mainHandler.removeCallbacksAndMessages(
            null
        )


        scanPopup?.dismiss()

        scanPopup =
            null


        try {

            unregisterReceiver(
                commandReceiver
            )

        } catch (
            _: Exception
        ) {
        }


        hideScanAnimation()

        removeSelectionOverlay()


        val button =
            floatingButton


        floatingButton =
            null


        floatingButtonParams =
            null


        if (
            button != null
        ) {

            try {

                windowManager?.removeViewImmediate(
                    button
                )

            } catch (
                _: Exception
            ) {
            }
        }


        try {

            textRecognizer.close()

        } catch (
            _: Exception
        ) {
        }


        Log.d(
            TAG,
            "ACCESSIBILITY SERVICE DESTROYED"
        )


        super.onDestroy()
    }
}


// =============================================================
// SCANNING ANIMATION VIEW
//
// A subtle moving scan line. When a selected rectangle is supplied,
// everything outside that rectangle is left untouched.
// =============================================================

private class ScanAnimationView(
    context: Context
) : View(context) {

    private val paint =
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )

    private val glowPaint =
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )

    private var scanRect: Rect? =
        null

    private var scanY =
        0f

    private var animator:
            android.animation.ValueAnimator? =
        null

    init {

        setLayerType(
            View.LAYER_TYPE_SOFTWARE,
            null
        )
    }

    fun setScanRect(
        rect: Rect?
    ) {

        scanRect =
            rect?.let { Rect(it) }

        scanY =
            scanRect?.top?.toFloat() ?: 0f

        invalidate()
    }

    fun startAnimation() {

        if (animator?.isRunning == true) {
            return
        }

        val bounds =
            scanRect

        val start =
            bounds?.top?.toFloat() ?: 0f

        val end =
            bounds?.bottom?.toFloat()
                ?: height.toFloat()

        scanY =
            start

        animator =
            android.animation.ValueAnimator.ofFloat(
                start,
                end
            ).apply {

                duration =
                    1500L

                repeatCount =
                    android.animation.ValueAnimator.INFINITE

                repeatMode =
                    android.animation.ValueAnimator.RESTART

                interpolator =
                    android.view.animation.LinearInterpolator()

                addUpdateListener { valueAnimator ->

                    scanY =
                        valueAnimator.animatedValue as Float

                    invalidate()
                }

                start()
            }
    }

    fun stopAnimation() {

        animator?.cancel()

        animator =
            null
    }

    override fun onDraw(
        canvas: android.graphics.Canvas
    ) {

        super.onDraw(canvas)

        val bounds =
            scanRect

        if (bounds != null) {

            canvas.save()

            canvas.clipRect(bounds)

            drawScanLine(
                canvas,
                bounds
            )

            canvas.restore()

        } else {

            drawScanLine(
                canvas,
                Rect(
                    0,
                    0,
                    width,
                    height
                )
            )
        }
    }

    private fun drawScanLine(
        canvas: android.graphics.Canvas,
        bounds: Rect
    ) {

        val lineY =
            scanY.coerceIn(
                bounds.top.toFloat(),
                bounds.bottom.toFloat()
            )

        // Soft glow above/below the line.
        val gradient =
            android.graphics.LinearGradient(
                0f,
                lineY - 45f,
                0f,
                lineY + 45f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(35, 255, 255, 255),
                    Color.argb(150, 255, 255, 255),
                    Color.argb(35, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )

        glowPaint.shader =
            gradient

        canvas.drawRect(
            bounds.left.toFloat(),
            lineY - 45f,
            bounds.right.toFloat(),
            lineY + 45f,
            glowPaint
        )

        // Thin premium scan line.
        paint.shader =
            android.graphics.LinearGradient(
                bounds.left.toFloat(),
                0f,
                bounds.right.toFloat(),
                0f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.WHITE,
                    Color.TRANSPARENT
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )

        canvas.drawRect(
            bounds.left.toFloat(),
            lineY - 1.5f,
            bounds.right.toFloat(),
            lineY + 1.5f,
            paint
        )

        paint.shader =
            null

        glowPaint.shader =
            null
    }

    override fun onDetachedFromWindow() {

        stopAnimation()

        super.onDetachedFromWindow()
    }
}


// =============================================================
// SELECTION OVERLAY
// =============================================================

private class SelectionOverlayView(
    context: Context
) : LinearLayout(context) {

    val frameView =
        SelectionFrameView(context)


    init {

        orientation =
            VERTICAL


        setBackgroundColor(
            Color.argb(
                110,
                0,
                0,
                0
            )
        )


        addView(
            frameView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
    }
}


// =============================================================
// SELECTION FRAME
// =============================================================

private class SelectionFrameView(
    context: Context
) : View(context) {

    var onSelectionChanged:
            ((Rect) -> Unit)? =
        null


    var onScan:
            (() -> Unit)? =
        null


    var onCancel:
            (() -> Unit)? =
        null


    private var rect =
        Rect(
            100,
            300,
            900,
            900
        )


    private var action =
        FrameAction.NONE


    private var downX =
        0f


    private var downY =
        0f


    private var originalRect =
        Rect()


    private enum class FrameAction {

        NONE,

        MOVE,

        RESIZE
    }


    private val paint =
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )


    private val strokePaint =
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG
        )


    init {

        isFocusable =
            true


        strokePaint.style =
            android.graphics.Paint.Style.STROKE


        strokePaint.strokeWidth =
            5f


        strokePaint.color =
            Color.WHITE


        setWillNotDraw(
            false
        )
    }


    // =========================================================
    // SET RECT
    // =========================================================

    fun setRect(
        newRect: Rect
    ) {

        rect =
            Rect(newRect)


        invalidate()
    }


    // =========================================================
    // GET RECT
    // =========================================================

    fun getRect(): Rect {

        return Rect(rect)
    }


    // =========================================================
    // DRAW
    // =========================================================

    override fun onDraw(
        canvas: android.graphics.Canvas
    ) {

        super.onDraw(
            canvas
        )


        // -----------------------------------------------------
        // DARK AREA ABOVE
        // -----------------------------------------------------

        paint.color =
            Color.argb(
                120,
                0,
                0,
                0
            )


        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            rect.top.toFloat(),
            paint
        )


        // -----------------------------------------------------
        // DARK AREA BELOW
        // -----------------------------------------------------

        canvas.drawRect(
            0f,
            rect.bottom.toFloat(),
            width.toFloat(),
            height.toFloat(),
            paint
        )


        // -----------------------------------------------------
        // DARK AREA LEFT
        // -----------------------------------------------------

        canvas.drawRect(
            0f,
            rect.top.toFloat(),
            rect.left.toFloat(),
            rect.bottom.toFloat(),
            paint
        )


        // -----------------------------------------------------
        // DARK AREA RIGHT
        // -----------------------------------------------------

        canvas.drawRect(
            rect.right.toFloat(),
            rect.top.toFloat(),
            width.toFloat(),
            rect.bottom.toFloat(),
            paint
        )


        // -----------------------------------------------------
        // SELECTION BORDER
        // -----------------------------------------------------

        strokePaint.color =
            Color.WHITE


        strokePaint.strokeWidth =
            5f


        canvas.drawRect(
            rect,
            strokePaint
        )


        // -----------------------------------------------------
        // CORNER HANDLES
        // -----------------------------------------------------

        paint.color =
            Color.WHITE


        val handle =
            18f


        canvas.drawCircle(
            rect.left.toFloat(),
            rect.top.toFloat(),
            handle,
            paint
        )


        canvas.drawCircle(
            rect.right.toFloat(),
            rect.top.toFloat(),
            handle,
            paint
        )


        canvas.drawCircle(
            rect.left.toFloat(),
            rect.bottom.toFloat(),
            handle,
            paint
        )


        canvas.drawCircle(
            rect.right.toFloat(),
            rect.bottom.toFloat(),
            handle,
            paint
        )


        // -----------------------------------------------------
        // BOTTOM CONTROLS
        // -----------------------------------------------------

        paint.color =
            Color.WHITE


        paint.textSize =
            42f


        paint.typeface =
            android.graphics.Typeface.DEFAULT_BOLD


        canvas.drawText(
            "SCAN",
            40f,
            height - 70f,
            paint
        )


        canvas.drawText(
            "CANCEL",
            width - 190f,
            height - 70f,
            paint
        )
    }


    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (
            event.action
        ) {

            MotionEvent.ACTION_DOWN -> {

                downX =
                    event.x


                downY =
                    event.y


                originalRect =
                    Rect(rect)


                action =
                    detectAction(
                        event.x,
                        event.y
                    )


                return true
            }


            MotionEvent.ACTION_MOVE -> {

                val dx =
                    (
                            event.x -
                                    downX
                            ).toInt()


                val dy =
                    (
                            event.y -
                                    downY
                            ).toInt()


                when (
                    action
                ) {

                    FrameAction.MOVE -> {

                        val width =
                            originalRect.width()


                        val height =
                            originalRect.height()


                        var newLeft =
                            originalRect.left +
                                    dx


                        var newTop =
                            originalRect.top +
                                    dy


                        newLeft =
                            newLeft.coerceIn(
                                0,
                                max(
                                    0,
                                    this.width -
                                            width
                                )
                            )


                        newTop =
                            newTop.coerceIn(
                                0,
                                max(
                                    0,
                                    this.height -
                                            height
                                )
                            )


                        rect =
                            Rect(
                                newLeft,
                                newTop,
                                newLeft + width,
                                newTop + height
                            )


                        notifyChanged()


                        invalidate()
                    }


                    FrameAction.RESIZE -> {

                        var newRight =
                            originalRect.right +
                                    dx


                        var newBottom =
                            originalRect.bottom +
                                    dy


                        newRight =
                            newRight.coerceIn(
                                originalRect.left + 150,
                                this.width
                            )


                        newBottom =
                            newBottom.coerceIn(
                                originalRect.top + 150,
                                this.height
                            )


                        rect =
                            Rect(
                                originalRect.left,
                                originalRect.top,
                                newRight,
                                newBottom
                            )


                        notifyChanged()


                        invalidate()
                    }


                    FrameAction.NONE -> {
                    }
                }


                return true
            }


            MotionEvent.ACTION_UP -> {

                if (
                    isScanButton(
                        event.x,
                        event.y
                    )
                ) {

                    onScan?.invoke()


                    action =
                        FrameAction.NONE


                    return true
                }


                if (
                    isCancelButton(
                        event.x,
                        event.y
                    )
                ) {

                    onCancel?.invoke()


                    action =
                        FrameAction.NONE


                    return true
                }


                action =
                    FrameAction.NONE


                return true
            }
        }


        return true
    }


    // =========================================================
    // DETECT MOVE / RESIZE
    // =========================================================

    private fun detectAction(
        x: Float,
        y: Float
    ): FrameAction {

        val handleRadius =
            70f


        // -----------------------------------------------------
        // Bottom-right resize handle
        // -----------------------------------------------------

        val resizeDistance =
            distance(
                x,
                y,
                rect.right.toFloat(),
                rect.bottom.toFloat()
            )


        if (
            resizeDistance <=
            handleRadius
        ) {

            return FrameAction.RESIZE
        }


        // -----------------------------------------------------
        // Inside rectangle = MOVE
        // -----------------------------------------------------

        if (
            rect.contains(
                x.toInt(),
                y.toInt()
            )
        ) {

            return FrameAction.MOVE
        }


        return FrameAction.NONE
    }


    // =========================================================
    // DISTANCE
    // =========================================================

    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {

        val dx =
            x1 - x2


        val dy =
            y1 - y2


        return sqrt(
            dx * dx +
                    dy * dy
        )
    }


    // =========================================================
    // SCAN BUTTON
    // =========================================================

    private fun isScanButton(
        x: Float,
        y: Float
    ): Boolean {

        return y >
                height - 130f &&
                x <
                width / 2f
    }


    // =========================================================
    // CANCEL BUTTON
    // =========================================================

    private fun isCancelButton(
        x: Float,
        y: Float
    ): Boolean {

        return y >
                height - 130f &&
                x >
                width / 2f
    }


    // =========================================================
    // CALLBACK
    // =========================================================

    private fun notifyChanged() {

        onSelectionChanged?.invoke(
            Rect(rect)
        )
    }
}
