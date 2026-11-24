package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.westmouthbay.fedleave.R
import com.westmouthbay.fedleave.core.MonthDay
import com.westmouthbay.fedleave.core.MonthViewResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val CARD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val MONTH_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenDay: (LocalDate) -> Unit,
    onOpenMonthPicker: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
    ) {
        // Top bar: Menu (dropdown with Today in I05), the FedLeave wordmark,
        // no Share action until I15.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("calendar_menu_button"),
                ) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.menu_open),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(248.dp),
                    shape = RoundedCornerShape(8.dp),
                    offset = DpOffset(x = 0.dp, y = 4.dp),
                ) {
                    // I05 delivers Today only; Transactions and Force Balance
                    // land in I09 and I10.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_today)) },
                        onClick = {
                            menuExpanded = false
                            viewModel.onMenuToday()
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.calendar_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        BalanceCard(state)
        MonthRow(
            month = state.displayMonth,
            onOpenMonthPicker = onOpenMonthPicker,
        )
        val month = state.month
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.monthLoading && month == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                month == null -> MonthUnavailable()
                else -> CalendarGridOrList(
                    month = month,
                    displayMonth = state.displayMonth,
                    selectedDay = state.selectedDay,
                    today = state.today,
                    settings = viewModel.displaySettings,
                    periodNumberFor = { date -> month.payPeriods.firstOrNull { it.end == date }?.number },
                    yearEndFor = { date -> viewModel.leaveYearEndFor(date) },
                    yearNumberFor = { date -> viewModel.leaveYearNumberFor(date) },
                    context = context,
                    onOpenDay = onOpenDay,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(state: CalendarUiState) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("calendar_balance_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val header = when {
                state.balanceDate == null -> ""
                state.followsToday ->
                    stringResource(R.string.calendar_as_of_today, state.balanceDate.format(CARD_DATE_FORMAT))
                else -> state.balanceDate.format(CARD_DATE_FORMAT)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                // The card opens Balances & date in I05-03; the chevron ships now.
                Text(
                    text = "\u203A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                state.balanceLoading && state.balance == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .width(24.dp)
                                .height(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                state.balance == null -> {
                    Text(
                        text = stringResource(R.string.unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                else -> {
                    val rows = BalancePresentation.visibleBalances(state.balance!!.balances)
                    rows.chunked(4).forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chunk.forEach { row ->
                                BalanceValueCell(context, row, Modifier.weight(1f))
                            }
                            repeat(4 - chunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceValueCell(
    context: android.content.Context,
    row: BalancePresentation.BalanceRow,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = BalancePresentation.shortLabel(context, row.category),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = stringResource(R.string.hours_value, BalancePresentation.formatHours(row.hours)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun MonthRow(
    month: YearMonth?,
    onOpenMonthPicker: () -> Unit,
) {
    val label = month?.format(MONTH_HEADER_FORMAT) ?: ""
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenMonthPicker)
            .testTag("calendar_month_selector"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.calendar_choose_month),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MonthUnavailable() {
    Text(
        text = stringResource(R.string.unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

// ---- grid and list -------------------------------------------------------

@Composable
private fun CalendarGridOrList(
    month: MonthViewResult,
    displayMonth: YearMonth?,
    selectedDay: LocalDate?,
    today: LocalDate?,
    settings: CalendarDisplaySettings,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The grid needs seven readable 48 dp targets AND a font scale that
        // keeps the cell annotations legible; otherwise the full-width day
        // list keeps every date and amount intact.
        val fontScale = LocalDensity.current.fontScale
        if (maxWidth < 7.dp * 48 || fontScale >= 1.3f) {
            DayList(
                month = month,
                settings = settings,
                periodNumberFor = periodNumberFor,
                yearEndFor = yearEndFor,
                yearNumberFor = yearNumberFor,
                context = context,
                onOpenDay = onOpenDay,
            )
        } else {
            MonthGrid(
                month = month,
                displayMonth = displayMonth,
                selectedDay = selectedDay,
                today = today,
                settings = settings,
                periodNumberFor = periodNumberFor,
                yearEndFor = yearEndFor,
                yearNumberFor = yearNumberFor,
                context = context,
                onOpenDay = onOpenDay,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: MonthViewResult,
    displayMonth: YearMonth?,
    selectedDay: LocalDate?,
    today: LocalDate?,
    settings: CalendarDisplaySettings,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        WeekdayHeader(settings.weekStart)
        // The mockups render only the days of the displayed month, with
        // blank cells for the leading and trailing adjacent dates.
        val inMonth = month.days.filter { it.inDisplayMonth }
        val leading = inMonth.firstOrNull()?.date?.dayOfWeek?.let { firstDay ->
            (firstDay.value - settings.weekStart.value + 7) % 7
        } ?: 0
        val slots: List<MonthDay?> =
            List(leading) { null } + inMonth
        val paddedSlots = slots + List((7 - slots.size % 7) % 7) { null }
        paddedSlots.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val yearEndNumber = if (yearEndFor(day.date) == day.date) {
                            yearNumberFor(day.date)
                        } else {
                            null
                        }
                        DayCell(
                            day = day,
                            isSelected = day.date == selectedDay,
                            isToday = day.date == today,
                            markers = markersFor(
                                day,
                                settings,
                                periodNumberFor(day.date),
                                yearEndNumber,
                                context,
                            ),
                            context = context,
                            onClick = { onOpenDay(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_weekday_header"),
    ) {
        repeat(7) { index ->
            val day = weekStart.plus(index.toLong())
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun markersFor(
    day: MonthDay,
    settings: CalendarDisplaySettings,
    periodNumber: Int?,
    yearEndNumber: Int?,
    context: android.content.Context,
): List<DayMarker> = buildList {
    if (settings.showHolidays && day.holidayName != null) {
        add(
            DayMarker(
                MarkerKind.HOLIDAY,
                "${context.getString(R.string.day_event_holiday)}, ${day.holidayName}",
            ),
        )
    }
    if (settings.showPaydays && day.isPayday) {
        add(DayMarker(MarkerKind.PAYDAY, context.getString(R.string.day_event_payday)))
    }
    if (settings.showPayPeriodEnds && day.isPayPeriodEnd) {
        val spoken = context.getString(R.string.day_event_period_end) +
            (periodNumber?.let { ", " + context.getString(R.string.day_event_period_number, it) } ?: "")
        add(DayMarker(MarkerKind.PERIOD_END, spoken))
    }
    if (yearEndNumber != null) {
        val spoken = context.getString(R.string.day_event_year_end) + ", " +
            context.getString(R.string.day_event_year_label, yearEndNumber)
        add(DayMarker(MarkerKind.YEAR_END, spoken))
    }
}

@Composable
private fun DayCell(
    day: MonthDay,
    isSelected: Boolean,
    isToday: Boolean,
    markers: List<DayMarker>,
    context: android.content.Context,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlighted = isSelected || isToday
    val borderColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fill = if (highlighted) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val glyphColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val lines = day.displayLines.map { BalancePresentation.formatDisplayLine(it) }
    val spoken = buildList {
        add(day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())))
        markers.forEach { add(it.spoken) }
        day.entries.forEach { entry ->
            val direction = entry.direction ?: ""
            add(
                "${BalancePresentation.formatHours(entry.hours)} hours " +
                    "${BalancePresentation.fullLabel(context, entry.category).lowercase()} $direction",
            )
        }
    }.joinToString(". ")

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(fill, RoundedCornerShape(8.dp))
            .border(if (highlighted) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("calendar_day_${day.date}"),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            markers.take(2).forEach { marker ->
                Spacer(Modifier.width(2.dp))
                MarkerGlyph(marker.kind, glyphColor)
            }
        }
        if (markers.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                markers.drop(2).forEachIndexed { index, marker ->
                    if (index > 0) {
                        Spacer(Modifier.width(2.dp))
                    }
                    MarkerGlyph(marker.kind, glyphColor)
                }
            }
        }
        lines.take(2).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (lines.size > 2) {
            Text(
                text = "+${lines.size - 2}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MarkerGlyph(kind: MarkerKind, color: Color) {
    when (kind) {
        MarkerKind.HOLIDAY -> HolidayStarGlyph(color = color)
        MarkerKind.PAYDAY -> PaydayCoinGlyph(color = color)
        MarkerKind.PERIOD_END -> PayPeriodEndFlagGlyph(color = color)
        MarkerKind.YEAR_END -> LeaveYearEndGlyph(color = color)
    }
}

@Composable
private fun DayList(
    month: MonthViewResult,
    settings: CalendarDisplaySettings,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    val days = month.days.filter { it.inDisplayMonth }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_day_list"),
    ) {
        items(days, key = { it.date.toString() }) { day ->
            val markers = markersFor(
                day,
                settings,
                periodNumberFor(day.date),
                if (yearEndFor(day.date) == day.date) yearNumberFor(day.date) else null,
                context,
            )
            val spoken = buildList {
                add(day.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())))
                markers.forEach { add(it.spoken) }
                day.entries.forEach { entry ->
                    add(
                        "${BalancePresentation.entryValueText(context, entry)} " +
                            BalancePresentation.fullLabel(context, entry.category),
                    )
                }
                if (day.entries.isEmpty()) {
                    add(context.getString(R.string.day_no_entries))
                }
            }.joinToString(". ")

            val body = when {
                day.entries.isEmpty() ->
                    context.getString(R.string.day_no_entries) + " \u203A"
                else -> day.entries.joinToString(" \u00b7 ") { entry ->
                    BalancePresentation.fullLabel(context, entry.category) + " \u00b7 " +
                        BalancePresentation.entryValueText(context, entry)
                } + " \u203A"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(day.date) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .testTag("calendar_day_${day.date}")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date.format(
                        DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
