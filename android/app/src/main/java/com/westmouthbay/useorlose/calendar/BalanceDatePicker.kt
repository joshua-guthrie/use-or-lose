package com.westmouthbay.useorlose.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westmouthbay.useorlose.R
import com.westmouthbay.useorlose.core.MonthDay
import com.westmouthbay.useorlose.core.MonthViewResult
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PICKER_MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val PICKER_SUBTITLE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val SHORTCUT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val SHOW_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())
private val PICKER_SPOKEN_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
private val PICKER_ROW_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

/**
 * The balance date picker: a month grid of selectable days with the schedule
 * markers (pay-period ends always shown), the nearby pay-period-end
 * shortcuts, and the confirm/cancel actions. Selecting a day or shortcut
 * only changes the pending draft; confirm commits it. On narrow screens the
 * grid falls back to the full-width day list, as on the Calendar.
 */
@Composable
fun BalanceDatePicker(
    viewModel: CalendarViewModel,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val draft = state.pickerDraft
    val pickerMonth = state.pickerMonth
    val monthResult = state.pickerMonthResult
    val anchor = state.pickerAnchor ?: state.balanceDate
    val shortcuts = anchor?.let { viewModel.nearbyPayPeriodEnds(it) } ?: emptyList()
    // The picker always identifies pay-period ends, even when the Calendar
    // hides them; holiday and payday visibility follow the settings.
    val pickerSettings = viewModel.displaySettings.copy(showPayPeriodEnds = true)

    // System Back cancels the pending choice before leaving, so no transient
    // draft survives the route.
    BackHandler {
        viewModel.onCancelPicker()
        onCancel()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = context.getString(R.string.cancel),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.balance_date_title),
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
                text = context.getString(
                    R.string.balance_date_subtitle,
                    (state.balanceDate ?: LocalDate.now()).format(PICKER_SUBTITLE_FORMAT),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            MonthHeader(
                month = pickerMonth,
                onPrevious = { pickerMonth?.let { viewModel.onPickerMonthChanged(it.minusMonths(1)) } },
                onNext = { pickerMonth?.let { viewModel.onPickerMonthChanged(it.plusMonths(1)) } },
            )
            Spacer(Modifier.height(8.dp))
            val staleMonth = state.pickerMonthFor != state.pickerMonth
            if (monthResult == null && (state.pickerMonthLoading || staleMonth)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (monthResult == null) {
                Text(
                    text = stringResource(R.string.unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 7.dp * 48) {
                        PickerDayList(
                            monthResult = monthResult,
                            draft = draft,
                            settings = pickerSettings,
                            periodNumberFor = { date -> monthResult.payPeriods.firstOrNull { it.end == date }?.number },
                            yearEndFor = { date -> viewModel.leaveYearEndFor(date) },
                            yearNumberFor = { date -> viewModel.leaveYearNumberFor(date) },
                            onSelect = { viewModel.onPickerDaySelected(it) },
                        )
                    } else {
                        PickerGrid(
                            monthResult = monthResult,
                            draft = draft,
                            settings = pickerSettings,
                            periodNumberFor = { date -> monthResult.payPeriods.firstOrNull { it.end == date }?.number },
                            yearEndFor = { date -> viewModel.leaveYearEndFor(date) },
                            yearNumberFor = { date -> viewModel.leaveYearNumberFor(date) },
                            onSelect = { viewModel.onPickerDaySelected(it) },
                        )
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.balance_picker_nearby_ends),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                shortcuts.forEach { period ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                viewModel.onPickerShortcutSelected(period.end)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = period.number?.let {
                                    context.getString(R.string.day_event_period_number, it)
                                } ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = period.end.format(SHORTCUT_DATE_FORMAT),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                text = context.getString(R.string.balance_picker_show, draft?.format(SHOW_DATE_FORMAT) ?: ""),
                onClick = {
                    viewModel.onConfirmPicker()
                    onConfirm()
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
            SecondaryAction(
                text = stringResource(R.string.cancel),
                onClick = {
                    viewModel.onCancelPicker()
                    onCancel()
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_picker_month_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        Text(
            text = month?.format(PICKER_MONTH_FORMAT) ?: "",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
    }
}

private fun pickerSlots(
    monthResult: MonthViewResult,
    settings: CalendarDisplaySettings,
): List<List<MonthDay?>> {
    val inMonth = monthResult.days.filter { it.inDisplayMonth }
    val leading = inMonth.firstOrNull()?.date?.dayOfWeek?.let { firstDay ->
        (firstDay.value - settings.weekStart.value + 7) % 7
    } ?: 0
    val slots: List<MonthDay?> = List(leading) { null } + inMonth
    return (slots + List((7 - slots.size % 7) % 7) { null }).chunked(7)
}

@Composable
private fun PickerGrid(
    monthResult: MonthViewResult,
    draft: LocalDate?,
    settings: CalendarDisplaySettings,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    onSelect: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = settings.weekStart.plus(index.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        pickerSlots(monthResult, settings).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val yearEndNumber = if (yearEndFor(day.date) == day.date) {
                            yearNumberFor(day.date)
                        } else {
                            null
                        }
                        val markers = markersFor(
                            day,
                            settings,
                            periodNumberFor(day.date),
                            yearEndNumber,
                            context,
                        )
                        val selected = day.date == draft
                        val spoken = buildList {
                            add(day.date.format(PICKER_SPOKEN_DATE_FORMAT))
                            markers.forEach { add(it.spoken) }
                            if (selected) {
                                add(context.getString(R.string.balances_title))
                            }
                        }.joinToString(". ")
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelect(day.date) }
                                .semantics(mergeDescendants = true) { contentDescription = spoken }
                                .testTag("balance_picker_day_${day.date}"),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = day.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                markers.take(2).forEach { marker ->
                                    Spacer(Modifier.width(2.dp))
                                    PickerMarkerGlyph(marker.kind, selected)
                                }
                            }
                            if (markers.size > 2) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    markers.drop(2).forEachIndexed { index, marker ->
                                        if (index > 0) {
                                            Spacer(Modifier.width(2.dp))
                                        }
                                        PickerMarkerGlyph(marker.kind, selected)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerDayList(
    monthResult: MonthViewResult,
    draft: LocalDate?,
    settings: CalendarDisplaySettings,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    onSelect: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val days = monthResult.days.filter { it.inDisplayMonth }
    Column(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            val yearEndNumber = if (yearEndFor(day.date) == day.date) {
                yearNumberFor(day.date)
            } else {
                null
            }
            val markers = markersFor(
                day,
                settings,
                periodNumberFor(day.date),
                yearEndNumber,
                context,
            )
            val selected = day.date == draft
            val spoken = buildList {
                add(day.date.format(PICKER_SPOKEN_DATE_FORMAT))
                markers.forEach { add(it.spoken) }
                if (selected) {
                    add(context.getString(R.string.balances_title))
                }
            }.joinToString(". ")
            Surface(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { onSelect(day.date) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .testTag("balance_picker_day_${day.date}"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.date.format(PICKER_ROW_DATE_FORMAT),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (markers.isEmpty()) {
                            ""
                        } else {
                            markers.joinToString(". ") { it.spoken }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerMarkerGlyph(kind: MarkerKind, selected: Boolean) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    when (kind) {
        MarkerKind.HOLIDAY -> HolidayStarGlyph(color = color)
        MarkerKind.PAYDAY -> PaydayCoinGlyph(color = color)
        MarkerKind.PERIOD_END -> PayPeriodEndFlagGlyph(color = color)
        MarkerKind.YEAR_END -> LeaveYearEndGlyph(color = color)
    }
}
