package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.VocalStudioViewModel
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainStudioScreen(
    viewModel: VocalStudioViewModel,
    modifier: Modifier = Modifier
) {
    val effects by viewModel.effectsState.collectAsState()
    val isInputActive by viewModel.isInputActive.collectAsState()
    val selectedMicInput by viewModel.selectedMicInput.collectAsState()
    val expandedIndex by viewModel.expandedRackIndex.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()

    val currentTheme by viewModel.currentTheme.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("main_studio_screen")
    ) {
        // --- 1. HEADER LOGO & CPU STATS DECK ---
        HeaderStatsDeck(viewModel)

        Spacer(modifier = Modifier.height(12.dp))

        // --- 2. INPUT TOGGLE CONTROLLER ---
        InputMonitoringController(
            isInputActive = isInputActive,
            onInputToggled = { viewModel.toggleInputActive() },
            selectedMicInput = selectedMicInput,
            onMicInputSelected = { viewModel.selectMicInput(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- 3. SPECTRUM ANALYZER & VU METERS ---
        StudioMeteringSystem(viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // --- 4. PRESETS SELECTOR ROW ---
        Text(
            text = "STUDIO PRESET PRO",
            color = primaryColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(6.dp))
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(StudioPreset.values()) { _, preset ->
                val isSelected = selectedPreset == preset
                val glowBorder = if (isSelected) Modifier.border(1.dp, primaryColor, RoundedCornerShape(8.dp)) else Modifier
                
                Surface(
                    onClick = { viewModel.applyStudioPreset(preset) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) primaryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.then(glowBorder).testTag("preset_${preset.name.lowercase()}")
                ) {
                    Text(
                        text = preset.label,
                        color = if (isSelected) primaryColor else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // --- 4B. CUSTOM USER PRESETS SECTION ---
        if (customPresets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CUSTOM USER PRESET",
                    color = secondaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(customPresets) { _, name ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, secondaryColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = name,
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { viewModel.loadUserPreset(name) }
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { viewModel.deleteUserPreset(name) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete preset",
                                    tint = PeakClipRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save preset CTA button
        Button(
            onClick = { showSaveDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .testTag("save_custom_preset_button"),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = secondaryColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Simpan Preset Kustom Baru", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 5. THE 12 EFFECT PLUGINS RACK ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DSP PLUGINS RACK (12 MODULES)",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Rack 1: EQ
        RackSlotItem(
            index = 0,
            title = "EFFECT RACK 1: PARAMETRIC EQ",
            isEnabled = effects.eq.isEnabled,
            isBypassed = effects.eq.isBypassed,
            isLocked = effects.eq.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.eq.isEnabled = !state.eq.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.eq.isBypassed = !state.eq.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.eq.isLocked = !state.eq.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.eq.reset() } },
            isExpanded = expandedIndex == 0,
            onHeaderClick = { viewModel.setExpandedRack(0) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Realtime 31-Band Graphic Tone Mapping", color = LightMutedText, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))
                
                // 31 EQ Bands Row Horizontal Scroll
                Text("31 EQ BANDS (20Hz - 20kHz)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(31) { bandIdx ->
                        val value = effects.eq.bands[bandIdx]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxHeight().width(32.dp)
                        ) {
                            Text(
                                text = "${value.roundToInt()}dB",
                                fontSize = 8.sp,
                                color = if (value == 0f) LightMutedText else primaryColor,
                                fontWeight = FontWeight.Bold
                            )
                            CustomVerticalSlider(
                                value = value,
                                onValueChange = { newVal: Float ->
                                    viewModel.updateEffects { state -> state.eq.bands[bandIdx] = newVal }
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("eq_band_$bandIdx"),
                                color = primaryColor
                            )
                            Text(
                                text = getBandHzText(bandIdx),
                                fontSize = 8.sp,
                                color = LightMutedText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // High Pass and Low Pass Horizontal Faders
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HorizontalFaderColumn(
                        label = "High Pass Filter (HPF)",
                        value = effects.eq.highPassHz,
                        valueRange = 20f..500f,
                        unitDisplay = "Hz",
                        onValueChange = { newVal -> viewModel.updateEffects { state -> state.eq.highPassHz = newVal } },
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalFaderColumn(
                        label = "Low Pass Filter (LPF)",
                        value = effects.eq.lowPassHz,
                        valueRange = 5000f..20000f,
                        unitDisplay = "Hz",
                        onValueChange = { newVal -> viewModel.updateEffects { state -> state.eq.lowPassHz = newVal } },
                        color = primaryColor
                    )
                }
            }
        }

        // Rack 2: Compressor
        RackSlotItem(
            index = 1,
            title = "EFFECT RACK 2: DYNAMIC COMPRESSOR",
            isEnabled = effects.compressor.isEnabled,
            isBypassed = effects.compressor.isBypassed,
            isLocked = effects.compressor.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.compressor.isEnabled = !state.compressor.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.compressor.isBypassed = !state.compressor.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.compressor.isLocked = !state.compressor.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.compressor.reset() } },
            isExpanded = expandedIndex == 1,
            onHeaderClick = { viewModel.setExpandedRack(1) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ValueSliderColumn(
                            label = "Threshold",
                            value = effects.compressor.thresholdDb,
                            valueRange = -60f..0f,
                            unit = "dB",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.thresholdDb = newVal } },
                            color = primaryColor
                        )
                        ValueSliderColumn(
                            label = "Ratio",
                            value = effects.compressor.ratio,
                            valueRange = 1f..15f,
                            unit = ":1",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.ratio = newVal } },
                            color = primaryColor
                        )
                        ValueSliderColumn(
                            label = "Makeup Gain",
                            value = effects.compressor.makeupGainDb,
                            valueRange = 0f..24f,
                            unit = "dB",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.makeupGainDb = newVal } },
                            color = primaryColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ValueSliderColumn(
                            label = "Attack Time",
                            value = effects.compressor.attackMs,
                            valueRange = 0.5f..100f,
                            unit = "ms",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.attackMs = newVal } },
                            color = secondaryColor
                        )
                        ValueSliderColumn(
                            label = "Release Time",
                            value = effects.compressor.releaseMs,
                            valueRange = 10f..1000f,
                            unit = "ms",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.releaseMs = newVal } },
                            color = secondaryColor
                        )
                        ValueSliderColumn(
                            label = "Knee Radius",
                            value = effects.compressor.kneeDb,
                            valueRange = 0f..12f,
                            unit = "dB",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.compressor.kneeDb = newVal } },
                            color = secondaryColor
                        )
                    }
                }
            }
        }

        // Rack 3: Limiter
        RackSlotItem(
            index = 2,
            title = "EFFECT RACK 3: MASTER LIMITER",
            isEnabled = effects.limiter.isEnabled,
            isBypassed = effects.limiter.isBypassed,
            isLocked = effects.limiter.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.limiter.isEnabled = !state.limiter.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.limiter.isBypassed = !state.limiter.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.limiter.isLocked = !state.limiter.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.limiter.reset() } },
            isExpanded = expandedIndex == 2,
            onHeaderClick = { viewModel.setExpandedRack(2) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(
                    label = "Ceiling Limit",
                    value = effects.limiter.ceilingDb,
                    valueRange = -12f..0f,
                    unit = "dB",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.limiter.ceilingDb = newVal } },
                    color = primaryColor
                )
                ValueSliderColumn(
                    label = "Threshold",
                    value = effects.limiter.thresholdDb,
                    valueRange = -30f..0f,
                    unit = "dB",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.limiter.thresholdDb = newVal } },
                    color = primaryColor
                )
                ValueSliderColumn(
                    label = "Release Release",
                    value = effects.limiter.releaseMs,
                    valueRange = 10f..500f,
                    unit = "ms",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.limiter.releaseMs = newVal } },
                    color = secondaryColor
                )
            }
        }

        // Rack 4: Reverb
        RackSlotItem(
            index = 3,
            title = "EFFECT RACK 4: REVERB PROFESSIONAL",
            isEnabled = effects.reverb.isEnabled,
            isBypassed = effects.reverb.isBypassed,
            isLocked = effects.reverb.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.reverb.isEnabled = !state.reverb.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.reverb.isBypassed = !state.reverb.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.reverb.isLocked = !state.reverb.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.reverb.reset() } },
            isExpanded = expandedIndex == 3,
            onHeaderClick = { viewModel.setExpandedRack(3) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Room type picker
                Text("REVERB ALGORITHM PROFILE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReverbType.values().forEach { rType ->
                        val isMatched = effects.reverb.reverbType == rType
                        Surface(
                            onClick = { viewModel.updateEffects { state -> state.reverb.reverbType = rType } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMatched) primaryColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isMatched) primaryColor else Color.Transparent)
                        ) {
                            Text(
                                rType.label,
                                fontSize = 9.sp,
                                color = if (isMatched) primaryColor else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        ValueSliderColumn(
                            label = "Decay Time",
                            value = effects.reverb.decaySec,
                            valueRange = 0.1f..10f,
                            unit = "sec",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.decaySec = newVal } },
                            color = primaryColor
                        )
                        ValueSliderColumn(
                            label = "Wet Mix",
                            value = effects.reverb.mixPercent,
                            valueRange = 0f..100f,
                            unit = "%",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.mixPercent = newVal } },
                            color = primaryColor
                        )
                        ValueSliderColumn(
                            label = "Pre Delay",
                            value = effects.reverb.preDelayMs,
                            valueRange = 0f..200f,
                            unit = "ms",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.preDelayMs = newVal } },
                            color = primaryColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        ValueSliderColumn(
                            label = "Room Size",
                            value = effects.reverb.roomSize,
                            valueRange = 0f..100f,
                            unit = "%",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.roomSize = newVal } },
                            color = secondaryColor
                        )
                        ValueSliderColumn(
                            label = "Stereo Width",
                            value = effects.reverb.width,
                            valueRange = 0f..100f,
                            unit = "%",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.width = newVal } },
                            color = secondaryColor
                        )
                        ValueSliderColumn(
                            label = "Damping Freq",
                            value = effects.reverb.damping,
                            valueRange = 0f..100f,
                            unit = "%",
                            onValueChange = { newVal -> viewModel.updateEffects { state -> state.reverb.damping = newVal } },
                            color = secondaryColor
                        )
                    }
                }
            }
        }

        // Rack 5: Delay
        RackSlotItem(
            index = 4,
            title = "EFFECT RACK 5: DIGITAL STEREO DELAY",
            isEnabled = effects.delay.isEnabled,
            isBypassed = effects.delay.isBypassed,
            isLocked = effects.delay.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.delay.isEnabled = !state.delay.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.delay.isBypassed = !state.delay.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.delay.isLocked = !state.delay.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.delay.reset() } },
            isExpanded = expandedIndex == 4,
            onHeaderClick = { viewModel.setExpandedRack(4) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ping Pong (Bounce Stereo)", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = effects.delay.isPingPong,
                        onCheckedChange = { newVal -> viewModel.updateEffects { state -> state.delay.isPingPong = newVal } },
                        colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                ValueSliderColumn(
                    label = "Delay Echo Time",
                    value = effects.delay.timeMs,
                    valueRange = 50f..1200f,
                    unit = "ms",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.delay.timeMs = newVal } },
                    color = primaryColor
                )
                ValueSliderColumn(
                    label = "Feedback (Repeats)",
                    value = effects.delay.feedbackPercent,
                    valueRange = 0f..95f,
                    unit = "%",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.delay.feedbackPercent = newVal } },
                    color = primaryColor
                )
                ValueSliderColumn(
                    label = "Delay Wet Mix",
                    value = effects.delay.mixPercent,
                    valueRange = 0f..100f,
                    unit = "%",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.delay.mixPercent = newVal } },
                    color = secondaryColor
                )
            }
        }

        // Rack 6: Harmony
        RackSlotItem(
            index = 5,
            title = "EFFECT RACK 6: MULTI-HARMONY ENGINE",
            isEnabled = effects.harmony.isEnabled,
            isBypassed = effects.harmony.isBypassed,
            isLocked = effects.harmony.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.harmony.isEnabled = !state.harmony.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.harmony.isBypassed = !state.harmony.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.harmony.isLocked = !state.harmony.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.harmony.reset() } },
            isExpanded = expandedIndex == 5,
            onHeaderClick = { viewModel.setExpandedRack(5) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Harmony (Follow Pitch Correction Key)", color = Color.White, fontSize = 11.sp)
                    Switch(
                        checked = effects.harmony.isAuto,
                        onCheckedChange = { newVal -> viewModel.updateEffects { state -> state.harmony.isAuto = newVal } },
                        colors = SwitchDefaults.colors(checkedThumbColor = primaryColor, checkedTrackColor = primaryColor.copy(alpha = 0.5f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("VOICES ARRAY LAYER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 6).forEach { count ->
                        val isSel = effects.harmony.voicesCount == count
                        Surface(
                            onClick = { viewModel.updateEffects { state -> state.harmony.voicesCount = count } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) primaryColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSel) primaryColor else Color.Transparent)
                        ) {
                            Text(
                                "$count Voices",
                                color = if (isSel) primaryColor else Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ValueSliderColumn(
                    label = "Manual Pitch Offset",
                    value = effects.harmony.pitchShiftSemitones,
                    valueRange = -12f..12f,
                    unit = "semis",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.harmony.pitchShiftSemitones = newVal } },
                    color = primaryColor
                )

                ValueSliderColumn(
                    label = "Gender Formant Factor",
                    value = effects.harmony.genderFormantFactor,
                    valueRange = 0f..100f,
                    unit = "%",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.harmony.genderFormantFactor = newVal } },
                    color = secondaryColor
                )
            }
        }

        // Rack 7: Pitch Correction
        RackSlotItem(
            index = 6,
            title = "EFFECT RACK 7: PRO PITCH TUNING (AUTO-TUNE)",
            isEnabled = effects.pitchCorrection.isEnabled,
            isBypassed = effects.pitchCorrection.isBypassed,
            isLocked = effects.pitchCorrection.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.pitchCorrection.isEnabled = !state.pitchCorrection.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.pitchCorrection.isBypassed = !state.pitchCorrection.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.pitchCorrection.isLocked = !state.pitchCorrection.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.pitchCorrection.reset() } },
            isExpanded = expandedIndex == 6,
            onHeaderClick = { viewModel.setExpandedRack(6) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Key and Scale selectors
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("ROOT MUSICAL KEY", color = LightMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(PitchKey.values()) { _, pKey ->
                                val isMatched = effects.pitchCorrection.key == pKey
                                Surface(
                                    onClick = { viewModel.updateEffects { state -> state.pitchCorrection.key = pKey } },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isMatched) primaryColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isMatched) primaryColor else Color.Transparent)
                                ) {
                                    Text(
                                        pKey.label,
                                        fontSize = 11.sp,
                                        color = if (isMatched) primaryColor else Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SCALE TYPE", color = LightMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsIndexed(PitchScale.values()) { _, pScale ->
                                val isMatched = effects.pitchCorrection.scale == pScale
                                Surface(
                                    onClick = { viewModel.updateEffects { state -> state.pitchCorrection.scale = pScale } },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isMatched) primaryColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (isMatched) primaryColor else Color.Transparent)
                                ) {
                                    Text(
                                        pScale.label,
                                        fontSize = 11.sp,
                                        color = if (isMatched) primaryColor else Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selection Natural, Pop, Hard Tune, Robot
                Text("AUTO-TUNE MODE", color = LightMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PitchMode.values().forEach { pMode ->
                        val isMatched = effects.pitchCorrection.mode == pMode
                        Surface(
                            onClick = { viewModel.updateEffects { state -> state.pitchCorrection.mode = pMode } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (isMatched) secondaryColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isMatched) secondaryColor else Color.Transparent)
                        ) {
                            Text(
                                pMode.label,
                                color = if (isMatched) secondaryColor else Color.White,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ValueSliderColumn(
                    label = "Retune Speed (Attack)",
                    value = effects.pitchCorrection.retuneSpeedPercent,
                    valueRange = 0f..100f,
                    unit = "%",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.pitchCorrection.retuneSpeedPercent = newVal } },
                    color = primaryColor
                )

                ValueSliderColumn(
                    label = "Humanize Factor",
                    value = effects.pitchCorrection.humanizePercent,
                    valueRange = 0f..100f,
                    unit = "%",
                    onValueChange = { newVal -> viewModel.updateEffects { state -> state.pitchCorrection.humanizePercent = newVal } },
                    color = secondaryColor
                )
            }
        }

        // Rack 8: Enhancer
        RackSlotItem(
            index = 7,
            title = "EFFECT RACK 8: VOCAL ENHANCER",
            isEnabled = effects.enhancer.isEnabled,
            isBypassed = effects.enhancer.isBypassed,
            isLocked = effects.enhancer.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.enhancer.isEnabled = !state.enhancer.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.enhancer.isBypassed = !state.enhancer.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.enhancer.isLocked = !state.enhancer.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.enhancer.reset() } },
            isExpanded = expandedIndex == 7,
            onHeaderClick = { viewModel.setExpandedRack(7) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(label = "Warmth (Low Saturation)", value = effects.enhancer.warmth, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.enhancer.warmth = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Presence (Spit Register)", value = effects.enhancer.presence, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.enhancer.presence = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Clarity (Definition)", value = effects.enhancer.clarity, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.enhancer.clarity = newVal } }, color = secondaryColor)
                ValueSliderColumn(label = "Air (Crisp Breath Shimmer)", value = effects.enhancer.air, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.enhancer.air = newVal } }, color = secondaryColor)
            }
        }

        // Rack 9: De-Esser
        RackSlotItem(
            index = 8,
            title = "EFFECT RACK 9: SMART DE-ESSER",
            isEnabled = effects.deEsser.isEnabled,
            isBypassed = effects.deEsser.isBypassed,
            isLocked = effects.deEsser.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.deEsser.isEnabled = !state.deEsser.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.deEsser.isBypassed = !state.deEsser.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.deEsser.isLocked = !state.deEsser.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.deEsser.reset() } },
            isExpanded = expandedIndex == 8,
            onHeaderClick = { viewModel.setExpandedRack(8) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(label = "Target S-Frequency", value = effects.deEsser.frequencyHz, valueRange = 3000f..9500f, unit = "Hz", onValueChange = { newVal -> viewModel.updateEffects { state -> state.deEsser.frequencyHz = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Sensitivity Threshold", value = effects.deEsser.thresholdDb, valueRange = -40f..0f, unit = "dB", onValueChange = { newVal -> viewModel.updateEffects { state -> state.deEsser.thresholdDb = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Gain Reduction Amount", value = effects.deEsser.amountPercent, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.deEsser.amountPercent = newVal } }, color = secondaryColor)
            }
        }

        // Rack 10: Noise Reduction
        RackSlotItem(
            index = 9,
            title = "EFFECT RACK 10: NOISE REDUCTION & GATE",
            isEnabled = effects.noiseReduction.isEnabled,
            isBypassed = effects.noiseReduction.isBypassed,
            isLocked = effects.noiseReduction.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.noiseReduction.isEnabled = !state.noiseReduction.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.noiseReduction.isBypassed = !state.noiseReduction.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.noiseReduction.isLocked = !state.noiseReduction.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.noiseReduction.reset() } },
            isExpanded = expandedIndex == 9,
            onHeaderClick = { viewModel.setExpandedRack(9) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(label = "Noise Floor Gate Threshold", value = effects.noiseReduction.noiseThresholdDb, valueRange = -80f..-30f, unit = "dB", onValueChange = { newVal -> viewModel.updateEffects { state -> state.noiseReduction.noiseThresholdDb = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Ambient Hum Attenuation", value = effects.noiseReduction.humReductionPercent, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.noiseReduction.humReductionPercent = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Hiss High-Cut Clamp", value = effects.noiseReduction.hissReductionPercent, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.noiseReduction.hissReductionPercent = newVal } }, color = secondaryColor)
            }
        }

        // Rack 11: Stereo Imager
        RackSlotItem(
            index = 10,
            title = "EFFECT RACK 11: STEREO IMAGER COLLAPSER",
            isEnabled = effects.stereoImager.isEnabled,
            isBypassed = effects.stereoImager.isBypassed,
            isLocked = effects.stereoImager.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.stereoImager.isEnabled = !state.stereoImager.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.stereoImager.isBypassed = !state.stereoImager.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.stereoImager.isLocked = !state.stereoImager.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.stereoImager.reset() } },
            isExpanded = expandedIndex == 10,
            onHeaderClick = { viewModel.setExpandedRack(10) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(label = "Stereo Image Field Width", value = effects.stereoImager.widthPercent, valueRange = 0f..200f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.stereoImager.widthPercent = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Low Frequency Mono Maker Floor", value = effects.stereoImager.monoMakerHz, valueRange = 20f..350f, unit = "Hz", onValueChange = { newVal -> viewModel.updateEffects { state -> state.stereoImager.monoMakerHz = newVal } }, color = secondaryColor)
            }
        }

        // Rack 12: Exciter
        RackSlotItem(
            index = 11,
            title = "EFFECT RACK 12: HARMONIC TUBE EXCITER",
            isEnabled = effects.exciter.isEnabled,
            isBypassed = effects.exciter.isBypassed,
            isLocked = effects.exciter.isLocked,
            onToggleEnabled = { viewModel.updateEffects { state -> state.exciter.isEnabled = !state.exciter.isEnabled } },
            onToggleBypass = { viewModel.updateEffects { state -> state.exciter.isBypassed = !state.exciter.isBypassed } },
            onToggleLock = { viewModel.updateEffects { state -> state.exciter.isLocked = !state.exciter.isLocked } },
            onReset = { viewModel.updateEffects { state -> state.exciter.reset() } },
            isExpanded = expandedIndex == 11,
            onHeaderClick = { viewModel.setExpandedRack(11) },
            theme = currentTheme
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ValueSliderColumn(label = "Analog Tube Saturation Factor", value = effects.exciter.tubeSaturationPercent, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.exciter.tubeSaturationPercent = newVal } }, color = primaryColor)
                ValueSliderColumn(label = "Harmonic Clarity Excite Focus", value = effects.exciter.brightFactor, valueRange = 0f..100f, unit = "%", onValueChange = { newVal -> viewModel.updateEffects { state -> state.exciter.brightFactor = newVal } }, color = secondaryColor)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Custom Save Preset Popup Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Simpan Preset Baru", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Tuliskan nama profil untuk menyimpan konfigurasi slider 12-rak aktif Anda.", color = LightMutedText, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("misal: Vokal Studio 1") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("preset_name_inputfield")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            viewModel.saveUserPreset(presetNameInput.trim())
                            showSaveDialog = false
                            presetNameInput = ""
                        }
                    }
                ) {
                    Text("SIMPAN", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("BATAL", color = Color.White)
                }
            }
        )
    }
}

// ---------------- HEADER STATS DECK ----------------
@Composable
fun HeaderStatsDeck(viewModel: VocalStudioViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val isInputActive by viewModel.isInputActive.collectAsState()
    
    val masterCpu by viewModel.audioProcessor.cpuUsagePercent.collectAsState()
    val masterLatency by viewModel.audioProcessor.latencyMs.collectAsState()

    val primaryColor = when(currentTheme) {
        WaveTheme.DARK_NEON -> DarkNeonPrimary
        WaveTheme.CYBER_BLUE -> CyberBluePrimary
        WaveTheme.GOLD_PRO -> GoldProPrimary
        WaveTheme.RED_STUDIO -> RedStudioPrimary
        WaveTheme.PURPLE_GALAXY -> PurpleGalaxyPrimary
        WaveTheme.EMERALD_PRO -> EmeraldProPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(1.dp, primaryColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "BRO AUDIO LABS",
                color = primaryColor.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "BRO EFEK VOCAL",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
        
        // Monitoring Stats Grid block
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBlock(label = "SAMPLE RATE", value = "44.1 kHz")
            StatBlock(label = "LATENCY", value = "$masterLatency ms")
            StatBlock(label = "CPU PROCESS", value = "$masterCpu%", color = if (masterCpu > 15) PeakClipRed else primaryColor)
        }
    }
}

@Composable
fun StatBlock(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = label, color = LightMutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}


// ---------------- INPUT ON / OFF BIG BUTTON CONTROLLER ----------------
@Composable
fun InputMonitoringController(
    isInputActive: Boolean,
    onInputToggled: () -> Unit,
    selectedMicInput: com.example.viewmodel.VocalStudioViewModel.MicrophoneInput,
    onMicInputSelected: (com.example.viewmodel.VocalStudioViewModel.MicrophoneInput) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main Mic Processing toggle (Large Neon Button)
        Button(
            onClick = onInputToggled,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("input_on_off_toggle"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInputActive) DarkNeonPrimary.copy(alpha = 0.15f) else PeakClipRed.copy(alpha = 0.12f)
            ),
            border = BorderStroke(2.dp, if (isInputActive) DarkNeonPrimary else PeakClipRed)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isInputActive) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isInputActive) DarkNeonPrimary else PeakClipRed,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (isInputActive) "PROSES VOCAL AKTIF" else "MONITOR MIK NONAKTIF",
                        color = if (isInputActive) DarkNeonPrimary else PeakClipRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isInputActive) "Mikrofon diproses real-time dengan efek studio" else "Ketuk untuk mengaktifkan pendengaran mikrofon",
                        color = if (isInputActive) Color.White.copy(alpha = 0.9f) else LightMutedText,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // Custom Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )

        // Mic Routing Grid Selector
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "SUMBER INPUT MIKROFON (HARDWARE ROUTING):",
                color = LightMutedText,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.values().forEach { inputMode ->
                    val isSelected = selectedMicInput == inputMode
                    val textLabel = when (inputMode) {
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.SYSTEM_DEFAULT -> "Default"
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.INTERNAL_MIC -> "Internal"
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.HEADSET_MIC -> "Headset"
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.BLUETOOTH_MIC -> "Bluetooth"
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.USB_MIC -> "USB/Card"
                    }
                    val icon = when (inputMode) {
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.SYSTEM_DEFAULT -> Icons.Default.Settings
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.INTERNAL_MIC -> Icons.Default.Call
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.HEADSET_MIC -> Icons.Default.Build
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.BLUETOOTH_MIC -> Icons.Default.Refresh
                        com.example.viewmodel.VocalStudioViewModel.MicrophoneInput.USB_MIC -> Icons.Default.Share
                    }
                    
                    Button(
                        onClick = { onMicInputSelected(inputMode) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("mic_input_${inputMode.name.lowercase()}"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f) else Color.DarkGray.copy(alpha = 0.22f)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = textLabel,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}


// ---------------- SPECTRUM & VU METERS ----------------
@Composable
fun StudioMeteringSystem(viewModel: VocalStudioViewModel) {
    val spectrum by viewModel.audioProcessor.spectrumData.collectAsState()
    val vuLevels by viewModel.audioProcessor.vuLevels.collectAsState()
    val vuPeaks by viewModel.audioProcessor.vuPeak.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // A. Real Time Wave Spectrum Canvas (60FPS FFT Visualization emulation)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("spectrum_analyzer_canvas")
        ) {
            val barW = size.width / 32f
            val spacing = 2f
            
            // Draw background reference grid lines
            for (g in 1..4) {
                val y = size.height * (g / 5f)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // Draw spectrum bars
            for (i in 0..31) {
                val amplitude = spectrum[i]
                val scaledHeight = amplitude * size.height
                val x = i * barW
                
                // Draw Neon Gradient Color Bar
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.2f)),
                        startY = size.height - scaledHeight,
                        endY = size.height
                    ),
                    topLeft = Offset(x + spacing, size.height - scaledHeight),
                    size = Size(barW - spacing * 2, scaledHeight),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        // B. Stereo VU Meters (Left / Right independently)
        Column(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VuMeterVerticalBar(label = "L", value = vuLevels.first, peak = vuPeaks.first, modifier = Modifier.weight(1f), tint = primaryColor)
            VuMeterVerticalBar(label = "R", value = vuLevels.second, peak = vuPeaks.second, modifier = Modifier.weight(1f), tint = primaryColor)
        }
    }
}

@Composable
fun VuMeterVerticalBar(
    label: String,
    value: Float, // 0.0f to 1.0f
    peak: Float,
    modifier: Modifier = Modifier,
    tint: Color
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = LightMutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Black)
        ) {
            // Level indicator
            val animVal by animateFloatAsState(targetValue = value, animationSpec = spring(stiffness = Spring.StiffnessHigh))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(animVal.coerceIn(0.01f, 1.0f))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(PeakClipRed, tint),
                        )
                    )
            )

            // Over-Scale Clipping warning
            if (value > 0.85f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(PeakClipRed)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}


// --- ---------------- RACK MODULE SLOT RENDERER ---------------- ---
@Composable
fun RackSlotItem(
    index: Int,
    title: String,
    isEnabled: Boolean,
    isBypassed: Boolean,
    isLocked: Boolean,
    onToggleEnabled: () -> Unit,
    onToggleBypass: () -> Unit,
    onToggleLock: () -> Unit,
    onReset: () -> Unit,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    theme: WaveTheme,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val cardBG = if (isEnabled && !isBypassed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) primaryColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBG)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar clicking to collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHeaderClick() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Power button inside rack slot
                    IconButton(
                        onClick = onToggleEnabled,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isEnabled) Color(0xFF39FF14).copy(alpha = 0.18f) else Color(0xFFFF3B30).copy(alpha = 0.18f))
                            .border(1.dp, if (isEnabled) Color(0xFF39FF14) else Color(0xFFFF3B30), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "Power",
                            tint = if (isEnabled) Color(0xFF39FF14) else Color(0xFFFF3B30),
                            modifier = Modifier.size(11.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = title,
                        color = if (isEnabled && !isBypassed) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock preset indicator
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Presets",
                        tint = if (isLocked) secondaryColor else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onToggleLock() }
                    )

                    // Bypass tag toggle
                    Surface(
                        onClick = onToggleBypass,
                        shape = RoundedCornerShape(4.dp),
                        color = if (isBypassed) PeakClipRed.copy(alpha = 0.2f) else if (isEnabled) Color(0xFF39FF14).copy(alpha = 0.08f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isBypassed) PeakClipRed else Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (isBypassed) "BYPASS" else "WET",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBypassed) PeakClipRed else if (isEnabled) Color(0xFF39FF14) else Color.LightGray,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Reset button
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset Rack",
                        tint = Color.LightGray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onReset() }
                    )

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Expand",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(bottom = 6.dp)
                ) {
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    content()
                }
            }
        }
    }
}


// --- ---------------- CONTROLS & PARAMS UTILS ---------------- ---

@Composable
fun ValueSliderColumn(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
            Text(
                text = "${"%.1f".format(value)} $unit",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun KnobControlColumn(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unitDisplay: String,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(6.dp)
    ) {
        Text(label, color = LightMutedText, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        
        // Draw responsive knob indicator representation
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color.DarkGray.copy(alpha = 0.3f), CircleShape)
                .border(1.5.dp, color.copy(alpha = 0.4f), CircleShape)
                .pointerInput(valueRange) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val dragScale = 0.005f
                        val currentFraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                        val newFraction = (currentFraction - dragAmount.y * dragScale).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
                .testTag("knob_${label.lowercase().replace(" ","_")}"),
            contentAlignment = Alignment.Center
        ) {
            val fraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            val angle = 135f + fraction * 270f // Knob starts at 135 deg to 405 deg

            // Draw pointer block
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val rad = size.width / 2f - 6.dp.toPx()
                val angleRad = Math.toRadians(angle.toDouble())
                val endX = center.x + rad * cos(angleRad).toFloat()
                val endY = center.y + rad * sin(angleRad).toFloat()
                
                drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(endX, endY))
            }
            Text(
                "${value.roundToInt()}",
                fontSize = 9.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text("$unitDisplay", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CustomVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    BoxWithConstraints(
        modifier = modifier
            .width(36.dp)
            .fillMaxHeight()
            .pointerInput(valueRange) {
                detectTapGestures(
                    onPress = { offset ->
                        val peakY = size.height.toFloat()
                        if (peakY > 0) {
                            val rawFraction = 1f - (offset.y / peakY)
                            val fraction = rawFraction.coerceIn(0f, 1f)
                            val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
                )
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val peakY = size.height.toFloat()
                    if (peakY > 0) {
                        val currentFraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                        val dragFractionDiff = -dragAmount.y / peakY
                        val newFraction = (currentFraction + dragFractionDiff).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
    ) {
        val totalHeight = maxHeight
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Track
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            )
            // Active Track
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(fraction)
                    .background(color, RoundedCornerShape(2.dp))
            )
            // Thumb
            Box(
                modifier = Modifier
                    .offset(y = totalHeight * (1f - fraction) - totalHeight / 2f)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
                    .border(1.5.dp, color, CircleShape)
            )
        }
    }
}

fun getBandHzText(idx: Int): String {
    val hz = listOf(
        20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800,
        1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000
    )
    val v = hz.getOrNull(idx) ?: 1000
    return if (v >= 1000) "${v / 1000}k" else "$v"
}

@Composable
fun HorizontalFaderColumn(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unitDisplay: String,
    onValueChange: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = LightMutedText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${value.roundToInt()} $unitDisplay",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .pointerInput(valueRange) {
                    detectTapGestures(
                        onPress = { offset ->
                            val width = size.width.toFloat()
                            if (width > 0) {
                                val fraction = (offset.x / width).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newValue)
                            }
                        }
                    )
                }
                .pointerInput(valueRange) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val width = size.width.toFloat()
                        if (width > 0) {
                            val currentFraction = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                            val dragFractionDiff = dragAmount.x / width
                            val newFraction = (currentFraction + dragFractionDiff).coerceIn(0f, 1f)
                            val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
                }
                .testTag("fader_horizontal_${label.lowercase().replace(" ", "_")}")
        ) {
            val totalWidth = maxWidth
            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Background track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                )
                // Active track
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
                // Thumb
                Box(
                    modifier = Modifier
                        .offset(x = (totalWidth - 24.dp) * fraction)
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Small inner dot for style
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(color, CircleShape)
                    )
                }
            }
        }
    }
}
