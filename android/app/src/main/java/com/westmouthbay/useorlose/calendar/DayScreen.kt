package com.westmouthbay.useorlose.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westmouthbay.useorlose.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())

private data class DayEventRow(val label: String, val value: String)

/**
 * The read-only day route from I05: the tapped date, its schedule events in
 * table order, and its saved leave activity. The Add entry and review
 * controls are I08's; the route opens from a Calendar cell or day row.
 */
@Composable
fun DayScreen(
    viewModel: CalendarViewModel,
    date: LocalDate,
    onBack: () -> Unit,
    onEditDay: () -> Unit,
    onAddEntry: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val month = state.month
    val day = month?.days?.firstOrNull { it.date == date }

    val events = buildList {
        day?.holidayName?.let {
            add(DayEventRow(context.getString(R.string.day_event_holiday), it))
        }
        if (day?.isPayday == true) {
            add(DayEventRow(context.getString(R.string.day_event_payday), ""))
        }
        if (day?.isPayPeriodEnd == true) {
            val number = month?.payPeriods?.firstOrNull { it.end == date }?.number
            add(
                DayEventRow(
                    context.getString(R.string.day_event_period_end),
                    number?.let { context.getString(R.string.day_event_period_number, it) } ?: "",
                ),
            )
        }
        if (viewModel.leaveYearEndFor(date) == date) {
            val year = viewModel.leaveYearNumberFor(date)
            add(
                DayEventRow(
                    context.getString(R.string.day_event_year_end),
                    year?.let { context.getString(R.string.day_event_year_label, it) } ?: "",
                ),
            )
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
            text = stringResource(R.string.day_title),
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
                text = date.format(DAY_DATE_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (events.isNotEmpty()) {
                Column(modifier = Modifier.testTag("calendar_day_events")) {
                    events.forEach { event ->
                        DayRowPill(
                            label = event.label,
                            value = event.value,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = stringResource(R.string.day_activity_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val entries = day?.entries ?: emptyList()
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.day_no_entries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                entries.forEach { entry ->
                    DayRowPill(
                        label = BalancePresentation.fullLabel(context, entry.category),
                        value = BalancePresentation.entryValueText(context, entry),
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = onEditDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("day_edit_entry"),
            ) {
                Text(stringResource(R.string.day_edit_entry_label))
            }
            androidx.compose.material3.Button(
                onClick = onAddEntry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("day_add_entry"),
            ) {
                Text(stringResource(R.string.day_add_title))
            }
        }
    }
}

@Composable
private fun DayRowPill(
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
