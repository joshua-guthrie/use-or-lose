package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westmouthbay.fedleave.R
import com.westmouthbay.fedleave.core.CoreError
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SETUP_DATE_DISPLAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
private val SETUP_SHORT_NOTE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val SETUP_RANGE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
private val SETUP_NOTE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/**
 * The leave-year setup flow: method choice, the start-of-year or Today
 * balance forms, and the review, per the annotated setup boards. The
 * created year publishes once through the setup view model.
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onExit: () -> Unit,
    onCreated: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.published) {
        if (state.published) {
            onCreated()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onExit) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_to_calendar),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (state.step) {
                SetupStep.METHOD -> MethodStep(viewModel, state, onExit)
                SetupStep.START_FORM -> StartFormStep(viewModel, state)
                SetupStep.TODAY_FORM -> TodayFormStep(viewModel, state)
                SetupStep.REVIEW -> ReviewStep(viewModel, state, context)
            }
            if (state.stale) {
                Text(
                    text = stringResource(R.string.setup_stale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            state.error?.let { error ->
                Text(
                    text = errorText(context, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.calendar_choose_date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun errorText(context: android.content.Context, error: CoreError): String =
    if (error.message.isNullOrBlank()) {
        context.getString(R.string.unavailable)
    } else {
        error.message.orEmpty()
    }

@Composable
private fun MethodStep(viewModel: SetupViewModel, state: SetupUiState, onExit: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Text(
        text = stringResource(R.string.setup_new_year, state.year),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    SetupFieldRow(
        label = stringResource(R.string.setup_first_period),
        value = state.leaveYearStart,
        onChange = viewModel::onStartChanged,
        tag = "setup_start_date",
    )
    PayPeriodRow(viewModel, state)
    SetupFieldRow(
        label = stringResource(R.string.setup_annual_accrual),
        value = state.annualAccrual,
        onChange = viewModel::onAnnualAccrualChanged,
        tag = "setup_accrual",
        suffix = stringResource(R.string.setup_hours_per_period, "").trim(),
    )
    SetupFieldRow(
        label = stringResource(R.string.setup_sick_accrual),
        value = state.sickAccrual,
        onChange = viewModel::onSickAccrualChanged,
        tag = "setup_sick",
        suffix = stringResource(R.string.setup_hours_per_period, "").trim(),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.setup_which_balances),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    SecondaryAction(
        text = stringResource(R.string.setup_start_choice),
        onClick = viewModel::onChooseStartOfYear,
        modifier = Modifier.padding(vertical = 4.dp).testTag("setup_start_choice"),
    )
    if (viewModel.todayInRange()) {
        SecondaryAction(
            text = stringResource(R.string.setup_today_choice),
            onClick = viewModel::onChooseToday,
            modifier = Modifier.padding(vertical = 4.dp).testTag("setup_today_choice"),
        )
    }
    SecondaryAction(
        text = stringResource(R.string.cancel),
        onClick = {
            viewModel.onCancel()
            onExit()
        },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun PayPeriodRow(viewModel: SetupViewModel, state: SetupUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.setup_pay_periods),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        listOf(26, 27).forEach { count ->
            FilterChip(
                selected = state.payPeriods == count,
                onClick = { viewModel.onPayPeriodsChanged(count) },
                label = { Text(count.toString()) },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun SetupFieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
    suffix: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            modifier = Modifier
                .width(140.dp)
                .testTag(tag),
        )
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun StartFormStep(viewModel: SetupViewModel, state: SetupUiState) {
    Text(
        text = stringResource(R.string.setup_start_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Text(
        text = stringResource(R.string.setup_new_year, state.year),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    SetupFieldRow(
        label = stringResource(R.string.setup_first_period),
        value = state.leaveYearStart,
        onChange = viewModel::onStartChanged,
        tag = "setup_start_date",
    )
    PayPeriodRow(viewModel, state)
    SetupFieldRow(
        label = stringResource(R.string.setup_annual_accrual),
        value = state.annualAccrual,
        onChange = viewModel::onAnnualAccrualChanged,
        tag = "setup_accrual",
        suffix = stringResource(R.string.setup_hours_per_period, "").trim(),
    )
    SetupFieldRow(
        label = stringResource(R.string.setup_annual_opening),
        value = state.openings["annual"] ?: "",
        onChange = { viewModel.onOpeningChanged("annual", it) },
        tag = "setup_opening_annual",
        suffix = "h",
    )
    SetupFieldRow(
        label = stringResource(R.string.setup_sick_opening),
        value = state.openings["sick"] ?: "",
        onChange = { viewModel.onOpeningChanged("sick", it) },
        tag = "setup_opening_sick",
        suffix = "h",
    )
    SecondaryAction(
        text = stringResource(R.string.setup_other_starting),
        onClick = viewModel::onShowOtherOpenings,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (state.showOtherOpenings) {
        SetupUiState.OTHER_CATEGORIES.forEach { category ->
            SetupFieldRow(
                label = BalancePresentation.fullLabel(LocalContext.current, category),
                value = state.openings[category] ?: "",
                onChange = { viewModel.onOpeningChanged(category, it) },
                tag = "setup_opening_$category",
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.setup_independent_card),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_independent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    PrimaryAction(
        text = stringResource(R.string.setup_review_action),
        onClick = viewModel::onPreview,
        modifier = Modifier.padding(vertical = 4.dp).testTag("setup_review_action"),
    )
    SecondaryAction(
        text = stringResource(R.string.setup_back_form),
        onClick = viewModel::onBackToMethod,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TodayFormStep(viewModel: SetupViewModel, state: SetupUiState) {
    val today = LocalDate.parse(state.todayIso ?: java.time.LocalDate.now().toString())
    Text(
        text = stringResource(R.string.setup_today_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Text(
        text = today.format(SETUP_DATE_DISPLAY),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Text(
        text = stringResource(R.string.setup_today_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    SetupFieldRow(
        label = BalancePresentation.fullLabel(LocalContext.current, "annual"),
        value = state.todayTargets["annual"] ?: "",
        onChange = { viewModel.onTargetChanged("annual", it) },
        tag = "setup_today_annual",
        suffix = "h",
    )
    SetupFieldRow(
        label = BalancePresentation.fullLabel(LocalContext.current, "sick"),
        value = state.todayTargets["sick"] ?: "",
        onChange = { viewModel.onTargetChanged("sick", it) },
        tag = "setup_today_sick",
        suffix = "h",
    )
    SetupFieldRow(
        label = BalancePresentation.fullLabel(LocalContext.current, "credit"),
        value = state.todayTargets["credit"] ?: "",
        onChange = { viewModel.onTargetChanged("credit", it) },
        tag = "setup_today_credit",
        suffix = "h",
    )
    SecondaryAction(
        text = stringResource(R.string.setup_other_balances),
        onClick = viewModel::onShowOtherTargets,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (state.showOtherTargets) {
        SetupUiState.OTHER_CATEGORIES.forEach { category ->
            SetupFieldRow(
                label = BalancePresentation.fullLabel(LocalContext.current, category),
                value = state.todayTargets[category] ?: "",
                onChange = { viewModel.onTargetChanged(category, it) },
                tag = "setup_today_$category",
            )
        }
    }
    PrimaryAction(
        text = stringResource(R.string.setup_review_action),
        onClick = viewModel::onPreview,
        modifier = Modifier.padding(vertical = 4.dp).testTag("setup_review_action"),
    )
    SecondaryAction(
        text = stringResource(R.string.setup_back_form),
        onClick = viewModel::onBackToMethod,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ReviewStep(
    viewModel: SetupViewModel,
    state: SetupUiState,
    context: android.content.Context,
) {
    val preview = state.preview
    if (preview == null) {
        return
    }
    Text(
        text = context.getString(R.string.setup_review_title, state.year),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 12.dp),
    )
    Text(
        text = context.getString(
            R.string.setup_review_range,
            preview.start.format(SETUP_RANGE_FORMAT),
            preview.end.format(SETUP_RANGE_FORMAT),
            preview.payPeriodCount,
        ),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    when (state.previewKind) {
        SetupKind.MANUAL -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.setup_independent_card),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.setup_independent_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            preview.openings.forEach { (category, hours) ->
                ReviewRow(
                    label = BalancePresentation.fullLabel(context, category),
                    value = BalancePresentation.formatHours(hours),
                )
            }
            if ((preview.openings["travel_comp"] ?: 0.0) > 0.0) {
                Text(
                    text = stringResource(R.string.setup_travel_comp_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        SetupKind.TODAY -> {
            val today = LocalDate.parse(state.todayIso ?: state.previewedToday ?: return)
            val zeroStart = preview.start
            val zeroEnd = today.minusDays(1)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = context.getString(
                            R.string.setup_review_today_card,
                            today.format(SETUP_NOTE_FORMAT),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(
                            R.string.setup_zero_window,
                            zeroStart.format(SETUP_SHORT_NOTE_FORMAT),
                            zeroEnd.format(SETUP_SHORT_NOTE_FORMAT),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.todayTargets.forEach { (category, text) ->
                val hours = text.trim().toDoubleOrNull() ?: return@forEach
                ReviewRow(
                    label = BalancePresentation.fullLabel(context, category),
                    value = BalancePresentation.formatHours(hours),
                )
            }
            preview.nextAccrual?.let { next ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = context.getString(
                                R.string.setup_next_accrual,
                                next.date.format(SETUP_SHORT_NOTE_FORMAT),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = context.getString(
                                R.string.setup_next_accrual_body,
                                BalancePresentation.formatHours(next.annual),
                                BalancePresentation.formatHours(next.sick),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        null -> Unit
    }
    PrimaryAction(
        text = stringResource(R.string.setup_create),
        onClick = viewModel::onConfirm,
        modifier = Modifier.padding(vertical = 4.dp).testTag("setup_create"),
    )
    SecondaryAction(
        text = if (state.previewKind == SetupKind.TODAY) {
            stringResource(R.string.setup_back_balances)
        } else {
            stringResource(R.string.setup_back_form)
        },
        onClick = viewModel::onBackToForm,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value h",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
