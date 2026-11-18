package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ShatrEvaluationUi
import com.example.ui.ShatrWeightStatus
import com.example.ui.WritingFootUi
import com.example.ui.WritingUiState
import com.example.ui.WritingVerseItem
import com.example.ui.WritingViewModel
import com.example.ui.theme.Amber200
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber900
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.Emerald300
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Gold100
import com.example.ui.theme.Gold600
import com.example.ui.theme.Gold700
import com.example.ui.theme.Navy900
import com.example.ui.theme.Red200
import com.example.ui.theme.Red50
import com.example.ui.theme.Red600
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.TajawalFontFamily

private data class HarakaKey(
    val label: String,
    val preview: String,
    val mark: String,
    val tag: String
)

private val HARAKA_ROW_1 = listOf(
    HarakaKey("فتحة", "ـَ", "\u064E", "haraka_fatha"),
    HarakaKey("ضمة", "ـُ", "\u064F", "haraka_damma"),
    HarakaKey("كسرة", "ـِ", "\u0650", "haraka_kasra"),
    HarakaKey("سكون", "ـْ", "\u0652", "haraka_sukun")
)

private val HARAKA_ROW_2 = listOf(
    HarakaKey("شدة", "ـّ", "\u0651", "haraka_shadda"),
    HarakaKey("تنوين فتح", "ـً", "\u064B", "haraka_tanwin_fath"),
    HarakaKey("تنوين ضم", "ـٌ", "\u064C", "haraka_tanwin_damm"),
    HarakaKey("تنوين كسر", "ـٍ", "\u064D", "haraka_tanwin_kasr")
)

private val MAJRA_OPTIONS = listOf("فتحة", "ضمة", "كسرة", "مقيدة")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingScreen(
    viewModel: WritingViewModel,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showNewDraftConfirm by remember { mutableStateOf(false) }

    val copyToClipboard: (String, String) -> Unit = { text, msg ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Poem", text))
        onShowToast(msg)
    }

    if (showNewDraftConfirm) {
        AlertDialog(
            onDismissRequest = { showNewDraftConfirm = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = "مسودة جديدة",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Navy900
                )
            },
            text = {
                Text(
                    text = "سيتم مسح الأبيات الحالية والبدء بمسودة جديدة. هل تريد المتابعة؟",
                    fontFamily = TajawalFontFamily,
                    fontSize = 14.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewDraftConfirm = false
                        viewModel.resetNewDraft()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold600, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_new_draft_button")
                ) {
                    Text(
                        text = "نعم، ابدأ مسودة",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewDraftConfirm = false },
                    modifier = Modifier.testTag("cancel_new_draft_button")
                ) {
                    Text(
                        text = "إلغاء",
                        fontFamily = TajawalFontFamily,
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1) ملخص أعلى الشاشة
            WritingSummaryBanner(state = state)

            // 2) بطاقة الإعداد القابلة للطي
            WritingSetupCard(
                state = state,
                onSelectMeter = { viewModel.selectMeter(it) },
                onSelectForm = { viewModel.selectFormType(it) },
                onToggleNoFixedQafiya = { viewModel.setNoFixedQafiya(it) },
                onRoiChange = { viewModel.updateRoiInput(it) },
                onSelectMajra = { viewModel.selectMajraOption(it) },
                onToggleTasri = { viewModel.setTasriFirstVerse(it) },
                onStartWriting = { viewModel.startWriting() },
                onExpandSettings = { viewModel.setSettingsExpanded(true) }
            )

            // 3) محرر الأبيات
            state.verses.forEachIndexed { index, verse ->
                WritingVerseCard(
                    index = index,
                    verse = verse,
                    canDelete = state.verses.size > 1 || verse.sadr.text.isNotEmpty() || verse.ajuz.text.isNotEmpty(),
                    onSadrChange = { viewModel.updateSadr(verse.id, it) },
                    onAjuzChange = { viewModel.updateAjuz(verse.id, it) },
                    onFocusField = { isAjuz -> viewModel.setActiveField(verse.id, isAjuz) },
                    onDeleteVerse = { viewModel.deleteVerse(verse.id) }
                )
            }

            // 4) شريط الإجراءات السفلي (داخل التمرير، دون FAB عائم)
            WritingActionBar(
                onAddVerse = { viewModel.addVerse() },
                onCopyPoem = {
                    val formatted = viewModel.formatPoemForCopy()
                    copyToClipboard(formatted, "تم نسخ القصيدة ✓")
                    viewModel.showToast("تم نسخ القصيدة ✓")
                },
                onSavePoem = { viewModel.savePoem() },
                onRequestNewDraft = { showNewDraftConfirm = true }
            )

            Spacer(modifier = Modifier.height(4.dp))
        }

        // 5) شريط الحركات المثبّت فوق لوحة المفاتيح
        HarakaToolbar(
            onInsertHaraka = { mark -> viewModel.insertHaraka(mark) }
        )
    }
}

@Composable
private fun WritingSummaryBanner(state: WritingUiState) {
    val total = state.verses.size
    val sound = state.soundVerseCount
    val summaryText = "${formatArabicVersesCount(total)} • ${formatArabicSoundCount(sound)}"
    val meterName = state.selectedMeter?.name ?: ""
    val formType = state.selectedForm?.type ?: ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("writing_summary_banner"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summaryText,
                fontFamily = TajawalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Navy900
            )
            if (meterName.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Gold100)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$meterName ($formType)",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Gold700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingSetupCard(
    state: WritingUiState,
    onSelectMeter: (String) -> Unit,
    onSelectForm: (String) -> Unit,
    onToggleNoFixedQafiya: (Boolean) -> Unit,
    onRoiChange: (String) -> Unit,
    onSelectMajra: (String) -> Unit,
    onToggleTasri: (Boolean) -> Unit,
    onStartWriting: () -> Unit,
    onExpandSettings: () -> Unit
) {
    var meterDropdownExpanded by remember { mutableStateOf(false) }
    val selectedMeter = state.selectedMeter
    val selectedForm = state.selectedForm

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("writing_setup_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        if (!state.isSettingsExpanded) {
            val qafiyaSummary = if (state.noFixedQafiya || state.roiInput.isBlank()) {
                "بدون قافية محددة"
            } else {
                "روي ${state.roiInput} (${state.majraOption})"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إعداد القصيدة",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Navy900
                    )
                    Text(
                        text = "البحر: ${selectedMeter?.name ?: ""} (${selectedForm?.type ?: ""}) • $qafiyaSummary",
                        fontFamily = TajawalFontFamily,
                        fontSize = 12.sp,
                        color = Slate600
                    )
                }
                OutlinedButton(
                    onClick = onExpandSettings,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate200),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("writing_edit_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل الإعداد",
                        tint = Navy900,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تعديل الإعداد",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Navy900
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "إعداد القصيدة",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Navy900
                )

                // قائمة البحور المنسدلة
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "البحر",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Navy900
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { meterDropdownExpanded = true }
                                .testTag("writing_meter_dropdown"),
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedMeter?.name ?: "اختر البحر",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Navy900
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "فتح قائمة البحور",
                                    tint = Slate600
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = meterDropdownExpanded,
                            onDismissRequest = { meterDropdownExpanded = false },
                            modifier = Modifier
                                .background(Color.White)
                                .heightIn(max = 320.dp)
                        ) {
                            state.meters.forEach { meter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = meter.name,
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = if (meter.id == state.selectedMeterId) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            color = if (meter.id == state.selectedMeterId) Gold700 else Navy900
                                        )
                                    },
                                    onClick = {
                                        meterDropdownExpanded = false
                                        onSelectMeter(meter.id)
                                    }
                                )
                            }
                        }
                    }
                }

                // النوع (تام / مجزوء)
                if (selectedMeter != null && selectedMeter.forms.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "النوع",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Navy900
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedMeter.forms.forEach { form ->
                                val selected = form.type == state.selectedFormType
                                Surface(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectForm(form.type) }
                                        .testTag("writing_form_chip_${form.type}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) Gold100 else Slate50,
                                    border = BorderStroke(1.dp, if (selected) Gold600 else Slate200)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = form.type,
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selected) Gold700 else Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // التفاعيل الأصلية
                if (selectedForm != null) {
                    Text(
                        text = "التفاعيل الأصلية: ${selectedForm.bases.joinToString(" ")}",
                        fontFamily = AmiriFontFamily,
                        fontSize = 15.sp,
                        color = Slate600
                    )
                }

                // إعداد القافية
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "القافية",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Navy900
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleNoFixedQafiya(!state.noFixedQafiya) }
                            .testTag("writing_no_fixed_qafiya_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.noFixedQafiya,
                            onCheckedChange = onToggleNoFixedQafiya,
                            colors = CheckboxDefaults.colors(checkedColor = Gold600),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                        Text(
                            text = "بدون قافية محددة",
                            fontFamily = TajawalFontFamily,
                            fontSize = 13.sp,
                            color = Slate800
                        )
                    }

                    AnimatedVisibility(visible = !state.noFixedQafiya) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "حرف الروي",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Navy900
                                )
                                OutlinedTextField(
                                    value = state.roiInput,
                                    onValueChange = onRoiChange,
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            text = "م",
                                            fontFamily = AmiriFontFamily,
                                            fontSize = 16.sp,
                                            color = Slate400
                                        )
                                    },
                                    textStyle = TextStyle(
                                        fontFamily = AmiriFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Navy900,
                                        textAlign = TextAlign.Center
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Gold600,
                                        unfocusedBorderColor = Slate200,
                                        errorBorderColor = Red600
                                    ),
                                    isError = state.roiError != null,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .testTag("writing_roi_input")
                                )
                            }

                            if (state.roiError != null) {
                                Text(
                                    text = state.roiError,
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 12.sp,
                                    color = Red600
                                )
                            }

                            Text(
                                text = "حركة الروي (المجرى)",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MAJRA_OPTIONS.forEach { option ->
                                    val selected = state.majraOption == option
                                    Surface(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSelectMajra(option) }
                                            .testTag("writing_majra_chip_$option"),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) Gold100 else Slate50,
                                        border = BorderStroke(1.dp, if (selected) Gold600 else Slate200)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option,
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (selected) Gold700 else Slate700
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleTasri(!state.tasriFirstVerse) }
                            .testTag("writing_tasri_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.tasriFirstVerse,
                            onCheckedChange = onToggleTasri,
                            colors = CheckboxDefaults.colors(checkedColor = Gold600),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                        Text(
                            text = "تصريع البيت الأول",
                            fontFamily = TajawalFontFamily,
                            fontSize = 13.sp,
                            color = Slate800
                        )
                    }
                }

                Button(
                    onClick = onStartWriting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold600,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("writing_start_button")
                ) {
                    Text(
                        text = "ابدأ الكتابة",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WritingVerseCard(
    index: Int,
    verse: WritingVerseItem,
    canDelete: Boolean,
    onSadrChange: (TextFieldValue) -> Unit,
    onAjuzChange: (TextFieldValue) -> Unit,
    onFocusField: (Boolean) -> Unit,
    onDeleteVerse: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("writing_verse_card_${verse.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // رأس بطاقة البيت
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "البيت ${toArabicDigits(index + 1)}",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Navy900
                )

                if (canDelete) {
                    IconButton(
                        onClick = onDeleteVerse,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("writing_delete_verse_${verse.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف البيت",
                            tint = Red600
                        )
                    }
                }
            }

            // الصدر
            WritingShatrBlock(
                label = "الصدر",
                placeholder = "أدخل الصدر مشكولًا...",
                value = verse.sadr,
                eval = verse.eval.sadr,
                inputTag = "writing_sadr_input_${verse.id}",
                statusTag = "writing_sadr_status_${verse.id}",
                onValueChange = onSadrChange,
                onFocused = { onFocusField(false) }
            )

            // العجز
            WritingShatrBlock(
                label = "العجز",
                placeholder = "أدخل العجز مشكولًا...",
                value = verse.ajuz,
                eval = verse.eval.ajuz,
                inputTag = "writing_ajuz_input_${verse.id}",
                statusTag = "writing_ajuz_status_${verse.id}",
                onValueChange = onAjuzChange,
                onFocused = { onFocusField(true) }
            )

            // نتيجة القافية للبيت
            val qRes = verse.eval.qafiyaResult
            val qMsgs = verse.eval.qafiyaMessages
            if (qRes != null || qMsgs.isNotEmpty()) {
                val isError = verse.eval.qafiyaIsHardError
                val hasIssue = verse.eval.qafiyaOk == false
                val bg = when {
                    isError -> Red50
                    hasIssue -> Amber50
                    else -> Emerald50
                }
                val borderCol = when {
                    isError -> Red200
                    hasIssue -> Amber200
                    else -> Emerald300
                }
                val textCol = when {
                    isError -> Red600
                    hasIssue -> Gold700
                    else -> Emerald700
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("writing_qafiya_box_${verse.id}"),
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = BorderStroke(1.dp, borderCol)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (qRes != null) {
                            Text(
                                text = "القافية: ${qRes.qafiyaText} • الروي: ${qRes.roi} (${if (qRes.isMutlaqa) qRes.majra ?: "مطلقة" else "مقيدة"})",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = textCol
                            )
                        }
                        qMsgs.forEach { msg ->
                            Text(
                                text = msg,
                                fontFamily = TajawalFontFamily,
                                fontSize = 12.sp,
                                color = if (isError) Red600 else Amber900
                            )
                        }
                    }
                }
            }

            // السطر المصغّر أسفل البطاقة: الوزن ✓/✗ • القافية ✓/✗
            val weightSymbol = when (verse.eval.weightOk) {
                true -> "✓"
                false -> "✗"
                null -> "—"
            }
            val weightColor = when (verse.eval.weightOk) {
                true -> Emerald700
                false -> Red600
                null -> Slate500
            }
            val qafiyaSymbol = when (verse.eval.qafiyaOk) {
                true -> "✓"
                false -> "✗"
                null -> "—"
            }
            val qafiyaColor = when (verse.eval.qafiyaOk) {
                true -> Emerald700
                false -> if (verse.eval.qafiyaIsHardError) Red600 else Gold700
                null -> Slate500
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الوزن $weightSymbol",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = weightColor
                )
                Text(
                    text = "•",
                    fontFamily = TajawalFontFamily,
                    fontSize = 12.sp,
                    color = Slate400
                )
                Text(
                    text = "القافية $qafiyaSymbol",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = qafiyaColor
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingShatrBlock(
    label: String,
    placeholder: String,
    value: TextFieldValue,
    eval: ShatrEvaluationUi,
    inputTag: String,
    statusTag: String,
    onValueChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = TajawalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Navy900
            )
            ShatrStatusBadge(eval = eval, tag = statusTag)
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = TajawalFontFamily,
                    fontSize = 14.sp,
                    color = Slate400
                )
            },
            textStyle = TextStyle(
                fontFamily = AmiriFontFamily,
                fontSize = 19.sp,
                color = Navy900,
                textDirection = TextDirection.Rtl
            ),
            singleLine = false,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = when (eval.status) {
                    ShatrWeightStatus.SOUND -> Emerald700
                    ShatrWeightStatus.BROKEN, ShatrWeightStatus.INVALID_INPUT -> Red600
                    else -> Gold600
                },
                unfocusedBorderColor = when (eval.status) {
                    ShatrWeightStatus.SOUND -> Emerald300
                    ShatrWeightStatus.BROKEN, ShatrWeightStatus.INVALID_INPUT -> Red200
                    else -> Slate200
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() }
                .testTag(inputTag)
        )

        if (eval.status == ShatrWeightStatus.INVALID_INPUT && eval.message != null) {
            Text(
                text = eval.message,
                fontFamily = TajawalFontFamily,
                fontSize = 12.sp,
                color = Red600
            )
        }

        // عرض التفاعيل تحت الشطر
        if (eval.feet.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                eval.feet.forEach { foot ->
                    WritingFootChip(foot = foot)
                }
            }
        }

        // تفاصيل الكسر إن كان الشطر مكسورًا (موضع الكسر باللون الأحمر + رقم التفعيلة + التعديل العروضي دون اقتراح كلمات)
        if (eval.status == ShatrWeightStatus.BROKEN) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Red50,
                border = BorderStroke(1.dp, Red200)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (eval.brokenCharRanges.isNotEmpty() && value.text.isNotEmpty()) {
                        val highlighted = remember(value.text, eval.brokenCharRanges) {
                            buildAnnotatedString {
                                val text = value.text
                                val brokenIndices = buildSet {
                                    eval.brokenCharRanges.forEach { range ->
                                        for (idx in range) {
                                            if (idx in text.indices) add(idx)
                                        }
                                    }
                                }
                                text.forEachIndexed { idx, ch ->
                                    if (idx in brokenIndices) {
                                        withStyle(
                                            SpanStyle(
                                                background = Red200,
                                                color = Red600,
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(ch)
                                        }
                                    } else {
                                        append(ch)
                                    }
                                }
                            }
                        }
                        Text(
                            text = highlighted,
                            fontFamily = AmiriFontFamily,
                            fontSize = 18.sp,
                            color = Navy900
                        )
                    }

                    if (!eval.message.isNullOrBlank() && eval.message != "مكسور") {
                        Text(
                            text = eval.message,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Red600
                        )
                    }

                    if (eval.brokenFootNumbers.isNotEmpty()) {
                        val nums = eval.brokenFootNumbers.joinToString("، ") { toArabicDigits(it) }
                        val wordsPart = if (eval.brokenWords.isNotEmpty()) {
                            " • الكلمة: ${eval.brokenWords.joinToString("، ")}"
                        } else {
                            ""
                        }
                        Text(
                            text = "موضع الكسر: التفعيلة $nums$wordsPart",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Red600
                        )
                    }

                    if (eval.currentBinary.isNotEmpty() && eval.expectedBinary.isNotEmpty()) {
                        Text(
                            text = "الحالي: ${binaryToProsodySymbols(eval.currentBinary)}   |   المطلوب: ${binaryToProsodySymbols(eval.expectedBinary)}",
                            fontFamily = TajawalFontFamily,
                            fontSize = 12.sp,
                            color = Slate700
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShatrStatusBadge(eval: ShatrEvaluationUi, tag: String) {
    val label = when (eval.status) {
        ShatrWeightStatus.EMPTY -> null
        ShatrWeightStatus.INVALID_INPUT -> "تنبيه إدخال"
        ShatrWeightStatus.NEEDS_DIACRITICS -> "شكّل الشطر لتظهر النتيجة"
        ShatrWeightStatus.INCOMPLETE -> "لم يكتمل"
        ShatrWeightStatus.SOUND -> "سليم"
        ShatrWeightStatus.BROKEN -> "مكسور"
    } ?: return

    val (bg, textCol, borderCol) = when (eval.status) {
        ShatrWeightStatus.SOUND -> Triple(Emerald50, Emerald700, Emerald300)
        ShatrWeightStatus.BROKEN, ShatrWeightStatus.INVALID_INPUT -> Triple(Red50, Red600, Red200)
        ShatrWeightStatus.INCOMPLETE, ShatrWeightStatus.NEEDS_DIACRITICS -> Triple(Amber50, Gold700, Amber200)
        ShatrWeightStatus.EMPTY -> Triple(Slate100, Slate500, Slate200)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            fontFamily = TajawalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = textCol
        )
    }
}

@Composable
private fun WritingFootChip(foot: WritingFootUi) {
    val bg = if (foot.isBroken) Red50 else Emerald50
    val border = if (foot.isBroken) Red200 else Emerald300
    val nameColor = if (foot.isBroken) Red600 else Emerald700

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = foot.name,
            fontFamily = AmiriFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = nameColor
        )
        Text(
            text = binaryToProsodySymbols(foot.pattern),
            fontFamily = TajawalFontFamily,
            fontSize = 11.sp,
            color = Slate700
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingActionBar(
    onAddVerse: () -> Unit,
    onCopyPoem: () -> Unit,
    onSavePoem: () -> Unit,
    onRequestNewDraft: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onAddVerse,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Slate200),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Navy900),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("writing_add_verse_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("بيت جديد", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = onCopyPoem,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Slate200),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Navy900),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("writing_copy_poem_button")
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("نسخ القصيدة", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Button(
            onClick = onSavePoem,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold600, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("writing_save_poem_button")
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("حفظ", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        OutlinedButton(
            onClick = onRequestNewDraft,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Slate200),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Red600),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("writing_new_draft_button")
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("مسودة جديدة", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HarakaToolbar(
    onInsertHaraka: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("writing_haraka_toolbar"),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HarakaButtonRow(items = HARAKA_ROW_1, onInsertHaraka = onInsertHaraka)
            HarakaButtonRow(items = HARAKA_ROW_2, onInsertHaraka = onInsertHaraka)
        }
    }
}

@Composable
private fun HarakaButtonRow(
    items: List<HarakaKey>,
    onInsertHaraka: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { key ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onInsertHaraka(key.mark) }
                    .testTag(key.tag),
                shape = RoundedCornerShape(10.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = key.preview,
                        fontFamily = AmiriFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Gold700,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = key.label,
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = Navy900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

private fun binaryToProsodySymbols(binary: String): String {
    return binary.map { if (it == '1') '/' else 'ْ' }.joinToString("")
}

private fun toArabicDigits(number: Int): String {
    val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    return number.toString().map { ch ->
        if (ch in '0'..'9') arabicDigits[ch - '0'] else ch
    }.joinToString("")
}

private fun formatArabicVersesCount(count: Int): String {
    return when (count) {
        1 -> "١ بيت"
        2 -> "٢ بيتان"
        in 3..10 -> "${toArabicDigits(count)} أبيات"
        else -> "${toArabicDigits(count)} بيتًا"
    }
}

private fun formatArabicSoundCount(count: Int): String {
    return when (count) {
        0 -> "٠ سليمة"
        1 -> "١ سليم"
        2 -> "٢ سليمان"
        in 3..10 -> "${toArabicDigits(count)} سليمة"
        else -> "${toArabicDigits(count)} سليمًا"
    }
}
