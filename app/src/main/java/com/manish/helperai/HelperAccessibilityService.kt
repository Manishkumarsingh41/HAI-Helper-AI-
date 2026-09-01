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
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


class HelperAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "HelperAI"

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

    private val textRecognizer: TextRecognizer =
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

    private var windowManager: WindowManager? =
        null


    // =========================================================
    // FLOATING BUTTON
    // =========================================================

    private var floatingButton: ImageView? =
        null

    private var floatingButtonParams:
            WindowManager.LayoutParams? =
        null


    // =========================================================
    // POPUP
    // =========================================================

    private var scanPopup: PopupWindow? =
        null


    // =========================================================
    // SELECTED AREA
    // =========================================================

    private var selectionOverlay:
            SelectionOverlayView? =
        null

    private var selectionFrame:
            SelectionFrameView? =
        null

    private var selectionParams:
            WindowManager.LayoutParams? =
        null

    private var selectedAreaMode =
        false


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

    private var scanEnabled =
        false

    private var serviceConnected =
        false

    private var ocrScheduled =
        false

    private var lastScreenshotTime =
        0L

    private var lastOcrText =
        ""

    private var lastPackageName =
        ""

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

                when (intent?.action) {

                    ACTION_SHOW_FLOATING_BUTTON -> {

                        showFloatingButton()
                    }


                    ACTION_HIDE_FLOATING_BUTTON -> {

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


        serviceConnected =
            true


        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        try {

            val filter =
                IntentFilter().apply {

                    addAction(
                        ACTION_SHOW_FLOATING_BUTTON
                    )

                    addAction(
                        ACTION_HIDE_FLOATING_BUTTON
                    )
                }


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


        // -----------------------------------------------------
        // Automatically show floating logo when service starts.
        // -----------------------------------------------------

        mainHandler.postDelayed({

            showFloatingButton()

        }, 500L)
    }


    // =========================================================
    // ACCESSIBILITY EVENT
    // =========================================================

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (
            event == null ||
            !serviceConnected ||
            !scanEnabled
        ) {

            return
        }


        val packageName =
            event.packageName
                ?.toString()
                ?: return


        // -----------------------------------------------------
        // Never OCR HelperAI itself.
        // -----------------------------------------------------

        if (
            packageName ==
            this.packageName
        ) {

            return
        }


        lastPackageName =
            packageName


        when (
            event.eventType
        ) {

            AccessibilityEvent.TYPE_VIEW_SCROLLED,

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {

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


        ocrScheduled =
            true


        mainHandler.postDelayed({

            ocrScheduled =
                false


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

            Log.e(
                TAG,
                "SCREENSHOT REQUIRES ANDROID 11+"
            )

            return
        }


        if (
            ocrRunning.get()
        ) {

            return
        }


        if (
            lastPackageName ==
            this.packageName
        ) {

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


        if (
            !ocrRunning.compareAndSet(
                false,
                true
            )
        ) {

            return
        }


        lastScreenshotTime =
            now


        val shouldCrop =
            scanMode ==
                    ScanMode.SELECTED_AREA


        // -----------------------------------------------------
        // Hide selection frame before screenshot.
        // -----------------------------------------------------

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
                            "SCREENSHOT FAILED: $errorCode"
                        )


                        ocrRunning.set(
                            false
                        )


                        if (shouldCrop) {

                            mainHandler.post {

                                restoreSelectionOverlay()
                            }
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


            if (shouldCrop) {

                mainHandler.post {

                    restoreSelectionOverlay()
                }
            }
        }
    }


    // =========================================================
    // PROCESS SCREENSHOT
    //
    // IMPORTANT:
    // Bitmap is NOT recycled here.
    //
    // ML Kit works asynchronously. The bitmap is recycled only
    // after OCR completes.
    // =========================================================

    private fun processScreenshot(
        screenshot: ScreenshotResult,
        shouldCrop: Boolean
    ) {

        var sourceBitmap:
                Bitmap? =
            null

        var ocrBitmap:
                Bitmap? =
            null


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


                finishOcrBitmap(
                    sourceBitmap,
                    ocrBitmap,
                    shouldCrop
                )


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


                    finishOcrBitmap(
                        sourceBitmap,
                        ocrBitmap,
                        shouldCrop
                    )


                    return
                }


                sourceBitmap =
                    hardwareBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    )


                hardwareBitmap.recycle()


                if (
                    sourceBitmap == null
                ) {

                    Log.e(
                        TAG,
                        "SOFTWARE BITMAP FAILED"
                    )


                    finishOcrBitmap(
                        sourceBitmap,
                        ocrBitmap,
                        shouldCrop
                    )


                    return
                }


                Log.d(
                    TAG,
                    "SCREENSHOT BITMAP = " +
                            "${sourceBitmap.width}x${sourceBitmap.height}"
                )

            } finally {

                hardwareBuffer.close()
            }


            // -------------------------------------------------
            // Crop only for Selected Area.
            // -------------------------------------------------

            ocrBitmap =
                if (shouldCrop) {

                    cropToSelectedArea(
                        sourceBitmap
                    )

                } else {

                    sourceBitmap
                }


            if (
                ocrBitmap == null
            ) {

                Log.e(
                    TAG,
                    "OCR BITMAP IS NULL"
                )


                finishOcrBitmap(
                    sourceBitmap,
                    null,
                    shouldCrop
                )


                return
            }


            // -------------------------------------------------
            // If cropped, source is no longer needed by OCR.
            // It can be recycled immediately.
            // The cropped bitmap stays alive until OCR finishes.
            // -------------------------------------------------

            if (
                ocrBitmap !== sourceBitmap
            ) {

                recycleBitmap(
                    sourceBitmap
                )


                sourceBitmap =
                    null
            }


            // -------------------------------------------------
            // IMPORTANT:
            // runOcr() owns ocrBitmap from this point.
            //
            // It will recycle it ONLY after ML Kit completes.
            // -------------------------------------------------

            val bitmapForOcr =
                ocrBitmap


            ocrBitmap =
                null


            runOcr(
                bitmapForOcr
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "SCREENSHOT PROCESSING ERROR",
                exception
            )


            finishOcrBitmap(
                sourceBitmap,
                ocrBitmap,
                shouldCrop
            )
        }
    }


    // =========================================================
    // FINISH BITMAP
    // =========================================================

    private fun finishOcrBitmap(
        sourceBitmap: Bitmap?,
        ocrBitmap: Bitmap?,
        shouldCrop: Boolean
    ) {

        recycleBitmap(
            sourceBitmap
        )


        recycleBitmap(
            ocrBitmap
        )


        ocrRunning.set(
            false
        )


        if (shouldCrop) {

            mainHandler.post {

                restoreSelectionOverlay()
            }
        }
    }


    // =========================================================
    // RECYCLE BITMAP SAFELY
    // =========================================================

    private fun recycleBitmap(
        bitmap: Bitmap?
    ) {

        if (
            bitmap == null
        ) {

            return
        }


        try {

            if (
                !bitmap.isRecycled
            ) {

                bitmap.recycle()
            }

        } catch (
            exception: Exception
        ) {

            Log.d(
                TAG,
                "BITMAP RECYCLE ERROR",
                exception
            )
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
                Rect(
                    selectedRect
                )


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
                width < 20 ||
                height < 20
            ) {

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
    //
    // Bitmap lifecycle is handled HERE.
    // =========================================================

    private fun runOcr(
        bitmap: Bitmap
    ) {

        Log.d(
            TAG,
            "OCR START"
        )


        try {

            val inputImage =
                InputImage.fromBitmap(
                    bitmap,
                    0
                )


            textRecognizer
                .process(
                    inputImage
                )

                .addOnSuccessListener { result ->

                    try {

                        val formattedText =
                            formatOcrResult(
                                result.text
                            )


                        if (
                            formattedText.length <
                            MIN_TEXT_LENGTH
                        ) {

                            Log.d(
                                TAG,
                                "OCR TEXT TOO SHORT"
                            )


                            return@addOnSuccessListener
                        }


                        // -------------------------------------------------
                        // Ignore exactly identical result.
                        // -------------------------------------------------

                        if (
                            formattedText ==
                            lastOcrText
                        ) {

                            Log.d(
                                TAG,
                                "OCR RESULT UNCHANGED"
                            )


                            return@addOnSuccessListener
                        }


                        lastOcrText =
                            formattedText


                        Log.d(
                            TAG,
                            "OCR SUCCESS"
                        )


                        Log.d(
                            TAG,
                            "OCR TEXT LENGTH = " +
                                    formattedText.length
                        )


                        Log.d(
                            TAG,
                            "----- FORMATTED OCR -----"
                        )


                        logTextInChunks(
                            formattedText
                        )


                        Log.d(
                            TAG,
                            "----- END FORMATTED OCR -----"
                        )


                        HelperAIResponseStore
                            .updateResponse(
                                formattedText
                            )

                    } catch (
                        exception: Exception
                    ) {

                        Log.e(
                            TAG,
                            "OCR RESULT PROCESSING ERROR",
                            exception
                        )
                    }
                }

                .addOnFailureListener { exception ->

                    Log.e(
                        TAG,
                        "OCR FAILED",
                        exception
                    )
                }

                .addOnCompleteListener {

                    // -------------------------------------------------
                    // CRITICAL FIX:
                    // Recycle bitmap ONLY after ML Kit completes.
                    // -------------------------------------------------

                    recycleBitmap(
                        bitmap
                    )


                    ocrRunning.set(
                        false
                    )


                    if (
                        selectedAreaMode &&
                        scanEnabled &&
                        scanMode ==
                        ScanMode.SELECTED_AREA
                    ) {

                        mainHandler.post {

                            restoreSelectionOverlay()
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


            recycleBitmap(
                bitmap
            )


            ocrRunning.set(
                false
            )


            if (
                selectedAreaMode &&
                scanEnabled
            ) {

                mainHandler.post {

                    restoreSelectionOverlay()
                }
            }
        }
    }


    // =========================================================
    // OCR FORMATTER
    //
    // Goal:
    // Raw OCR
    //     ↓
    // clean readable text
    // =========================================================

    private fun formatOcrResult(
        text: String
    ): String {

        if (
            text.isBlank()
        ) {

            return ""
        }


        val normalized =
            text
                .replace(
                    "\r\n",
                    "\n"
                )
                .replace(
                    "\r",
                    "\n"
                )


        val rawLines =
            normalized
                .split(
                    "\n"
                )


        val cleanedLines =
            ArrayList<String>()


        for (
        rawLine in rawLines
        ) {

            var line =
                rawLine
                    .replace(
                        Regex("[\\t ]+"),
                        " "
                    )
                    .trim()


            if (
                line.isEmpty()
            ) {

                continue
            }


            // -------------------------------------------------
            // Remove spaces before punctuation.
            // -------------------------------------------------

            line =
                line.replace(
                    Regex("\\s+([,.!?;:%])"),
                    "$1"
                )


            // -------------------------------------------------
            // Normalize brackets.
            // -------------------------------------------------

            line =
                line.replace(
                    Regex("\\(\\s+"),
                    "("
                )


            line =
                line.replace(
                    Regex("\\s+\\)"),
                    ")"
                )


            // -------------------------------------------------
            // Common OCR spacing around colon.
            // -------------------------------------------------

            line =
                line.replace(
                    Regex("\\s*:\\s*"),
                    ": "
                )


            // -------------------------------------------------
            // Keep markdown-like bullets readable.
            // -------------------------------------------------

            line =
                line.replace(
                    Regex("^[-•●]\\s*"),
                    "• "
                )


            cleanedLines.add(
                line.trim()
            )
        }


        if (
            cleanedLines.isEmpty()
        ) {

            return ""
        }


        // =====================================================
        // MERGE WRAPPED SENTENCE LINES
        //
        // Example:
        //
        // Natural Language Processing is a branch
        // of Artificial Intelligence.
        //
        // becomes:
        //
        // Natural Language Processing is a branch of
        // Artificial Intelligence.
        // =====================================================

        val formatted =
            StringBuilder()


        for (
        index in cleanedLines.indices
        ) {

            val current =
                cleanedLines[index]


            if (
                formatted.isEmpty()
            ) {

                formatted.append(
                    current
                )

                continue
            }


            val previous =
                formatted
                    .toString()
                    .takeLastWhile {
                        it != '\n'
                    }


            val shouldJoin =
                shouldJoinLines(
                    previous,
                    current
                )


            if (shouldJoin) {

                formatted.append(
                    " "
                )

                formatted.append(
                    current
                )

            } else {

                formatted.append(
                    "\n"
                )

                formatted.append(
                    current
                )
            }
        }


        return formatted
            .toString()
            .replace(
                Regex("[ ]{2,}"),
                " "
            )
            .replace(
                Regex("\n{3,}"),
                "\n\n"
            )
            .trim()
    }


    // =========================================================
    // SHOULD JOIN OCR LINES
    // =========================================================

    private fun shouldJoinLines(
        previous: String,
        current: String
    ): Boolean {

        if (
            previous.isBlank() ||
            current.isBlank()
        ) {

            return false
        }


        // -----------------------------------------------------
        // Never join bullets/lists.
        // -----------------------------------------------------

        if (
            current.matches(
                Regex(
                    "^(•|[-*]|\\d+[.)]|[a-zA-Z][.)])\\s+.*"
                )
            )
        ) {

            return false
        }


        // -----------------------------------------------------
        // Never join obvious headings.
        // -----------------------------------------------------

        if (
            current.length <= 60 &&
            !current.endsWith(".") &&
            !current.endsWith(",") &&
            !current.endsWith(":") &&
            current.firstOrNull()
                ?.isUpperCase() == true
        ) {

            val words =
                current.split(
                    Regex("\\s+")
                )


            if (
                words.size <= 8
            ) {

                return false
            }
        }


        // -----------------------------------------------------
        // If previous ends with punctuation, new sentence.
        // -----------------------------------------------------

        if (
            previous.endsWith(".") ||
            previous.endsWith("!") ||
            previous.endsWith("?") ||
            previous.endsWith(":")
        ) {

            return false
        }


        // -----------------------------------------------------
        // If current starts with lowercase, it is very likely
        // a wrapped continuation.
        // -----------------------------------------------------

        if (
            current.firstOrNull()
                ?.isLowerCase() == true
        ) {

            return true
        }


        // -----------------------------------------------------
        // Short OCR lines are generally separate content.
        // -----------------------------------------------------

        if (
            previous.length < 25
        ) {

            return false
        }


        // -----------------------------------------------------
        // If previous looks incomplete, join it.
        // -----------------------------------------------------

        return !previous.endsWith(
            ","
        ) &&
                !previous.endsWith(
                    ";"
                ) &&
                previous.split(
                    Regex("\\s+")
                ).size >= 5
    }


    // =========================================================
    // FLOATING HAI LOGO
    // =========================================================

    private fun showFloatingButton() {

        if (
            !serviceConnected
        ) {

            return
        }


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {

            Log.e(
                TAG,
                "OVERLAY PERMISSION NOT GRANTED"
            )


            return
        }


        if (
            floatingButton != null
        ) {

            return
        }


        val wm =
            windowManager
                ?: return


        val button =
            ImageView(this)


        button.setImageResource(
            R.drawable.helper_ai_logo
        )


        button.scaleType =
            ImageView.ScaleType.CENTER_CROP


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
        // DRAG + TAP
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
                "CIRCULAR HAI LOGO SHOWN"
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


        val title =
            TextView(this)


        title.text =
            "Scan Screen"


        title.textSize =
            18f


        title.setTextColor(
            Color.WHITE
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
        // STOP
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
            Color.WHITE
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

        stopScan()


        selectedAreaMode =
            false


        scanMode =
            ScanMode.FULL_SCREEN


        scanEnabled =
            true


        HelperAIResponseStore
            .clearResponse()


        lastOcrText =
            ""


        lastScreenshotTime =
            0L


        Log.d(
            TAG,
            "FULL SCREEN SCAN = ON"
        )
    }


    // =========================================================
    // SELECTED AREA MODE
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
                    "SELECTED AREA SCAN = ON"
                )


                Log.d(
                    TAG,
                    "AREA = $selectedRect"
                )


                // -------------------------------------------------
                // Scan immediately.
                // -------------------------------------------------

                captureScreenForOcr()
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


            selectionOverlay =
                root


            selectionFrame =
                root.frameView


            selectionParams =
                params


            Log.d(
                TAG,
                "SELECTION FRAME SHOWN"
            )

        } catch (
            exception: Exception
        ) {

            selectionOverlay =
                null


            selectionFrame =
                null


            selectionParams =
                null


            Log.e(
                TAG,
                "SELECTION OVERLAY ERROR",
                exception
            )
        }
    }


    // =========================================================
    // REMOVE SELECTION OVERLAY
    // =========================================================

    private fun removeSelectionOverlay() {

        val overlay =
            selectionOverlay


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

        } catch (
            exception: IllegalArgumentException
        ) {

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
    // TEMPORARILY REMOVE SELECTION FRAME
    // =========================================================

    private fun removeSelectionOverlayTemporarily() {

        val overlay =
            selectionOverlay


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
    // RESTORE SELECTION FRAME
    // =========================================================

    private fun restoreSelectionOverlay() {

        if (
            !selectedAreaMode ||
            !scanEnabled ||
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
    // STOP SCAN
    // =========================================================

    private fun stopScan() {

        scanEnabled =
            false


        scanMode =
            ScanMode.NONE


        selectedAreaMode =
            false


        ocrScheduled =
            false


        mainHandler.removeCallbacksAndMessages(
            null
        )


        removeSelectionOverlay()


        Log.d(
            TAG,
            "SCAN MODE = OFF"
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
    }


    // =========================================================
    // LOG LONG TEXT
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


        super.onDestroy()
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
        canvas:
        android.graphics.Canvas
    ) {

        super.onDraw(
            canvas
        )


        // -----------------------------------------------------
        // Outside dark overlay
        // -----------------------------------------------------

        paint.color =
            Color.argb(
                125,
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


        canvas.drawRect(
            0f,
            rect.bottom.toFloat(),
            width.toFloat(),
            height.toFloat(),
            paint
        )


        canvas.drawRect(
            0f,
            rect.top.toFloat(),
            rect.left.toFloat(),
            rect.bottom.toFloat(),
            paint
        )


        canvas.drawRect(
            rect.right.toFloat(),
            rect.top.toFloat(),
            width.toFloat(),
            rect.bottom.toFloat(),
            paint
        )


        // -----------------------------------------------------
        // Selection border
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
        // Handles
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
        // Bottom controls
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

                        val frameWidth =
                            originalRect.width()


                        val frameHeight =
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
                                    width -
                                            frameWidth
                                )
                            )


                        newTop =
                            newTop.coerceIn(
                                0,
                                max(
                                    0,
                                    height -
                                            frameHeight
                                )
                            )


                        rect =
                            Rect(
                                newLeft,
                                newTop,
                                newLeft + frameWidth,
                                newTop + frameHeight
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
                                width
                            )


                        newBottom =
                            newBottom.coerceIn(
                                originalRect.top + 150,
                                height
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
    // DETECT ACTION
    // =========================================================

    private fun detectAction(
        x: Float,
        y: Float
    ): FrameAction {

        val handleRadius =
            75f


        val distance =
            distance(
                x,
                y,
                rect.right.toFloat(),
                rect.bottom.toFloat()
            )


        if (
            distance <=
            handleRadius
        ) {

            return FrameAction.RESIZE
        }


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