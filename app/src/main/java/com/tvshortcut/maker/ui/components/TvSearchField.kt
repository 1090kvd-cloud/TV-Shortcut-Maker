package com.tvshortcut.maker.ui.components

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.tvshortcut.maker.R
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Search box sized and styled for a remote control.
 *
 * On Android TV, giving focus to a text field and pressing OK brings up the
 * system on-screen keyboard, so no custom input handling is needed. What does
 * need care is the focus state: the field must look obviously selected from
 * across a room, hence the accent outline and the slight scale-up shared with
 * the rest of the UI.
 *
 * The clear button only appears once there is something to clear, keeping the
 * D-Pad path short while the field is empty.
 */
@Composable
fun TvSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    // Voice input is delegated to the system recogniser rather than captured in
    // process: that way the app needs no RECORD_AUDIO permission and the viewer
    // gets the familiar TV microphone screen.
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
        }
    }
    val voiceAvailable = remember {
        runCatching {
            context.packageManager.queryIntentActivities(voiceIntent, 0).isNotEmpty()
        }.getOrDefault(false)
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) onQueryChange(spoken)
    }
    val voicePrompt = stringResource(R.string.search_voice_prompt)
    val voiceUnavailable = stringResource(R.string.search_voice_unavailable)

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "searchScale"
    )
    val shape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .scale(scale)
            .height(44.dp)
            .clip(shape)
            .background(if (focused) TvColors.SurfaceFocused else TvColors.Surface)
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvColors.BorderFocused else TvColors.Border
                ),
                shape
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (focused) TvColors.TextPrimary else TvColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))

        Box(modifier = Modifier.width(200.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TvColors.TextPrimary),
                cursorBrush = SolidColor(TvColors.Primary),
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Closing the keyboard on "search" hands focus straight back to
                // the grid, which is what the viewer expects after typing.
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
            )
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.TextTertiary
                )
            }
        }

        // Microphone: always present, so the D-Pad path to it does not move
        // around as the query changes.
        Spacer(Modifier.width(8.dp))
        val micInteraction = remember { MutableInteractionSource() }
        val micFocused = micInteraction.collectIsFocusedAsStateCompat()
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (micFocused) TvColors.Primary else TvColors.SurfaceElevated)
                .tvClickable(
                    interactionSource = micInteraction,
                    onClick = {
                        if (voiceAvailable) {
                            runCatching {
                                voiceLauncher.launch(
                                    Intent(voiceIntent)
                                        .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                )
                            }.onFailure {
                                Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.search_voice),
                tint = when {
                    micFocused -> TvColors.OnPrimary
                    voiceAvailable -> TvColors.TextSecondary
                    else -> TvColors.TextTertiary
                },
                modifier = Modifier.size(15.dp)
            )
        }

        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            val clearInteraction = remember { MutableInteractionSource() }
            val clearFocused = clearInteraction.collectIsFocusedAsStateCompat()
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (clearFocused) TvColors.Primary else TvColors.SurfaceElevated)
                    .tvClickable(
                        interactionSource = clearInteraction,
                        onClick = { onQueryChange("") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = if (clearFocused) TvColors.OnPrimary else TvColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
