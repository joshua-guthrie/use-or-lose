package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westmouthbay.fedleave.R
import com.westmouthbay.fedleave.core.PayPeriodDetailResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BALANCES_TODAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
private val BALANCES_DATED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
private val BALANCES_PROJECTED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
private val BALANCES_REQUESTED_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
private val PERIOD_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val PERIOD_DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d", Locale.getDefault())
private val PERIOD_NOTE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())

/**
 * Balances & date: the full scrollable list for the committed cutoff, with
 * the period activity card for a pay-period end, the projected and
 * unavailable states, and the entry point to the balance date picker.
 */
@Composable
fun BalanceScreen(
    viewModel: CalendarViewModel,
    onChooseDate: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val balance = state.balance
    val rows = balance?.let {
        BalancePresentation.visibleBalances(it.balances, it.parental)
    } ?: emptyList()
    var periodExpanded by rememberSaveable(state.balanceDate) { mutableStateOf(false) }

    val balanceDate = state.balanceDate
    val subtitle = when {
        balanceDate == null -> ""
        balance == null && !state.balanceLoading ->
            context.getString(
                R.string.balances_requested_date,
                balanceDate.format(BALANCES_REQUESTED_FORMAT),
            )
        state.isProjected ->
            context.getString(
                R.string.balances_projected,
                balanceDate.format(BALANCES_PROJECTED_FORMAT),
            )
        state.followsToday ->
            context.getString(
                R.string.balances_as_of_today,
                balanceDate.format(BALANCES_TODAY_FORMAT),
            )
        else -> context.getString(
            R.string.balances_as_of,
            balanceDate.format(BALANCES_DATED_FORMAT),
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = context.getString(R.string.back_to_calendar),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.balances_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            when {
                state.balanceLoading &&
                    (state.balanceFor != state.balanceDate || balance == null) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                balance == null -> {
                    UnavailableCard(
                        onRetry = { viewModel.onRetryBalance() },
                        onChooseDate = onChooseDate,
                    )
                }
                else -> {
                    val isPeriodEnd = state.periodFor == state.balanceDate
                    if (state.followsToday) {
                        PrimaryAction(
                            text = stringResource(R.string.calendar_choose_date),
                            onClick = onChooseDate,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    if (state.followsToday) {
                        Text(
                            text = context.resources.getQuantityString(
                                R.plurals.balances_count_plural,
                                rows.size,
                                rows.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    rows.forEach { row ->
                        BalanceRowPill(
                            label = BalancePresentation.fullLabel(context, row.category),
                            value = BalancePresentation.valueText(context, row.value),
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                    if (isPeriodEnd && state.periodError == null) {
                        PeriodCard(
                            period = state.period,
                            expanded = periodExpanded,
                            onToggle = { periodExpanded = !periodExpanded },
                        )
                    }
                    if (state.isProjected && !isPeriodEnd) {
                        val projectedDate = balanceDate
                        Text(
                            text = context.getString(
                                R.string.balances_projected_note,
                                projectedDate?.format(PERIOD_NOTE_FORMAT) ?: "",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        state.followsToday -> SecondaryAction(
                            text = stringResource(R.string.back_to_calendar),
                            onClick = onBack,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        isPeriodEnd -> PrimaryAction(
                            text = stringResource(R.string.calendar_choose_date),
                            onClick = onChooseDate,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        else -> SecondaryAction(
                            text = stringResource(R.string.calendar_choose_date),
                            onClick = onChooseDate,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceRowPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
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
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnavailableCard(
    onRetry: () -> Unit,
    onChooseDate: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.balances_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.balances_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
    PrimaryAction(
        text = stringResource(R.string.balances_retry),
        onClick = onRetry,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    SecondaryAction(
        text = stringResource(R.string.calendar_choose_date),
        onClick = onChooseDate,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun PeriodCard(
    period: PayPeriodDetailResult?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val info = period?.period
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onToggle)
            .testTag("balance_period_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(
                    R.string.period_card,
                    info?.number ?: 0,
                    context.getString(
                        if (expanded) R.string.period_details_open else R.string.period_details_closed,
                    ),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                val start = info?.startDate
                val end = info?.endDate
                val pay = info?.payDate
                val endText = when {
                    end == null -> ""
                    start != null &&
                        start.year == end.year && start.monthValue == end.monthValue ->
                        end.format(PERIOD_DAY_FORMAT)
                    else -> end.format(PERIOD_DATE_FORMAT)
                }
                Text(
                    text = context.getString(
                        R.string.period_range,
                        start?.format(PERIOD_DATE_FORMAT) ?: "",
                        endText,
                        pay?.format(PERIOD_DATE_FORMAT) ?: "",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (period != null) {
                    period.activity.forEach { (category, totals) ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = BalancePresentation.fullLabel(context, category),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = context.getString(
                                    R.string.period_totals,
                                    BalancePresentation.formatHours(totals.earned),
                                    BalancePresentation.formatHours(totals.used),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(20.dp)
                                .height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
internal fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
