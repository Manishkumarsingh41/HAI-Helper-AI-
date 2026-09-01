package com.manish.helperai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.manish.helperai.ui.theme.HelperAITheme

import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {

    companion object {

        const val ACTION_SHOW_FLOATING_BUTTON =
            "com.manish.helperai.SHOW_FLOATING_BUTTON"

        const val ACTION_HIDE_FLOATING_BUTTON =
            "com.manish.helperai.HIDE_FLOATING_BUTTON"
    }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        enableEdgeToEdge()


        var latestResponse by
        mutableStateOf("")


        // =====================================================
        // COLLECT OCR RESULT
        // =====================================================

        lifecycleScope.launch {

            repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                HelperAIResponseStore
                    .latestResponse
                    .collect { response ->

                        latestResponse =
                            response
                    }
            }
        }


        // =====================================================
        // UI
        // =====================================================

        setContent {

            HelperAITheme {

                Scaffold(
                    modifier =
                        Modifier.fillMaxSize()
                ) { innerPadding ->

                    HelperAIScreen(

                        response =
                            latestResponse,

                        onClear = {

                            HelperAIResponseStore
                                .clearResponse()
                        },

                        onStartFloatingButton = {

                            openOverlayPermissionOrStart()
                        },

                        onAccessibilitySettings = {

                            openAccessibilitySettings()
                        },

                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )
                }
            }
        }
    }


    // =========================================================
    // OVERLAY PERMISSION / FLOATING BUTTON
    // =========================================================

    private fun openOverlayPermissionOrStart() {

        /*
         * Android requires explicit user permission
         * for drawing over other apps.
         */

        if (
            !Settings.canDrawOverlays(
                this
            )
        ) {

            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:$packageName"
                    )
                )

            startActivity(
                intent
            )

            return
        }


        /*
         * Overlay permission already available.
         *
         * Tell AccessibilityService to show
         * the floating HAI button.
         */

        sendBroadcast(
            Intent(
                ACTION_SHOW_FLOATING_BUTTON
            ).setPackage(
                packageName
            )
        )
    }


    // =========================================================
    // ACCESSIBILITY SETTINGS
    // =========================================================

    private fun openAccessibilitySettings() {

        try {

            val intent =
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )

            startActivity(
                intent
            )

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()
        }
    }


    // =========================================================
    // RESUME
    // =========================================================

    override fun onResume() {

        super.onResume()

        /*
         * When user returns from overlay settings,
         * do not automatically start scanning.
         *
         * User must explicitly press START SCAN.
         */
    }


    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        /*
         * Do NOT hide the floating button here.
         *
         * The whole point is that HAI should remain
         * available when the user leaves HelperAI
         * and opens Chrome, ChatGPT, YouTube, etc.
         */

        super.onDestroy()
    }
}


// =============================================================
// HELPER AI SCREEN
// =============================================================

@Composable
fun HelperAIScreen(
    response: String,

    onClear: () -> Unit,

    onStartFloatingButton: () -> Unit,

    onAccessibilitySettings: () -> Unit,

    modifier: Modifier = Modifier
) {

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(
                    rememberScrollState()
                ),

        verticalArrangement =
            Arrangement.Top
    ) {


        // =====================================================
        // TITLE
        // =====================================================

        Text(
            text = "HelperAI",

            style =
                MaterialTheme
                    .typography
                    .headlineMedium
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "Universal Screen Assistant",

            style =
                MaterialTheme
                    .typography
                    .titleMedium
        )


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =====================================================
        // START SCAN CARD
        // =====================================================

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "Floating HAI",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                Text(
                    text =
                        "Start the floating HAI button. " +
                                "It will remain available while " +
                                "you use other apps.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )


                Button(
                    onClick =
                        onStartFloatingButton,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "START HAI"
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        // =====================================================
        // ACCESSIBILITY SETTINGS
        // =====================================================

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "Accessibility Service",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                Text(
                    text =
                        "Accessibility permission is required " +
                                "for screen and scroll detection.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )


                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )


                Button(
                    onClick =
                        onAccessibilitySettings,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "OPEN ACCESSIBILITY SETTINGS"
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        // =====================================================
        // OCR RESULT
        // =====================================================

        Text(
            text =
                "Captured Screen Text",

            style =
                MaterialTheme
                    .typography
                    .titleMedium
        )


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        if (
            response.isBlank()
        ) {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "No screen text captured yet.",

                    modifier =
                        Modifier.padding(16.dp),

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }

        } else {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Text(
                        text =
                            "Latest OCR",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )


                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )


                    Text(
                        text =
                            response,

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.End
            ) {

                Button(
                    onClick =
                        onClear
                ) {

                    Text(
                        text =
                            "CLEAR"
                    )
                }
            }
        }
    }
}