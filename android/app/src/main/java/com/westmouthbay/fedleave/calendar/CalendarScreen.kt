package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
    onOpenBalances: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    // Manual "full list" mode: the user switches between the grid and the
    // full-width day list with the small toggle above the bottom bar. The
    // grid no longer switches itself on the system font scale.
    var fullListMode by rememberSaveable { mutableStateOf(false) }
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
        BalanceCard(state, onOpenBalances)
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
                state.monthLoading && (state.monthFor != state.displayMonth || month == null) -> {
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
                    fullListMode = fullListMode,
                    periodNumberFor = { date -> month.payPeriods.firstOrNull { it.end == date }?.number },
                    yearEndFor = { date -> viewModel.leaveYearEndFor(date) },
                    yearNumberFor = { date -> viewModel.leaveYearNumberFor(date) },
                    context = context,
                    onOpenDay = onOpenDay,
                )
            }
        }
        FullListToggle(
            fullListMode = fullListMode,
            onToggle = { fullListMode = !fullListMode },
        )
    }
}

@Composable
private fun BalanceCard(
    state: CalendarUiState,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val header = when {
        state.balanceDate == null -> ""
        state.followsToday ->
            stringResource(R.string.calendar_as_of_today, state.balanceDate.format(CARD_DATE_FORMAT))
        else -> state.balanceDate.format(CARD_DATE_FORMAT)
    }
    val rows = state.balance?.let {
        BalancePresentation.visibleBalances(it.balances, it.parental)
    } ?: emptyList()
    val columns = BalancePresentation.previewColumns(
        maxWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp,
        fontScale = LocalDensity.current.fontScale,
    )
    val (previewRows, hidden) = BalancePresentation.preview(rows, columns)
    val spoken = buildString {
        append(header)
        previewRows.forEach { row ->
            append(". ")
            append(BalancePresentation.shortLabel(context, row.category))
            append(" ")
            append(BalancePresentation.valueText(context, row.value))
        }
        if (hidden > 0) {
            append(". ")
            append(
                context.getString(
                    R.string.calendar_view_all_balances,
                    rows.size,
                    hidden,
                ),
            )
        }
        append(". ")
        append(context.getString(R.string.calendar_open_balances_action))
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("calendar_balance_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                // The card opens Balances & date.
                Text(
                    text = "\u203A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                state.balanceLoading &&
                    (state.balanceFor != state.balanceDate || state.balance == null) -> {
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
                    previewRows.chunked(columns).forEach { chunk ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            chunk.forEach { row ->
                                BalanceValueCell(context, row, Modifier.weight(1f))
                            }
                            repeat(columns - chunk.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (hidden > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.calendar_view_all_balances,
                                rows.size,
                                hidden,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
            text = BalancePresentation.valueText(context, row.value),
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
    fullListMode: Boolean,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The full-width day list appears in manual mode or when seven
        // readable 48 dp targets physically cannot fit the screen width.
        if (fullListMode || maxWidth < 7.dp * 48) {
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
                maxHeight = maxHeight,
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
    maxHeight: Dp,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
) {
    // The grid restores its scroll position when the screen returns from the
    // day route or the month picker.
    var savedScroll by rememberSaveable { mutableIntStateOf(0) }
    val gridScrollState = rememberScrollState(initial = savedScroll)
    DisposableEffect(Unit) {
        onDispose { savedScroll = gridScrollState.value }
    }
    // The grid keeps its base font size regardless of the system font-scale
    // setting: the at-a-glance calendar must not clip or reflow at large
    // text. Everything else in the app keeps scaling.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        // The mockups render only the days of the displayed month, with
        // blank cells for the leading and trailing adjacent dates.
        val inMonth = month.days.filter { it.inDisplayMonth }
        val leading = inMonth.firstOrNull()?.date?.dayOfWeek?.let { firstDay ->
            (firstDay.value - settings.weekStart.value + 7) % 7
        } ?: 0
        val slots: List<MonthDay?> =
            List(leading) { null } + inMonth
        val paddedSlots = slots + List((7 - slots.size % 7) % 7) { null }
        val rows = paddedSlots.chunked(7)

        val headerHeight = 20.dp
        val fitsWithoutScroll =
            rows.isNotEmpty() && maxHeight > headerHeight + MIN_ROW_HEIGHT * rows.size
        if (fitsWithoutScroll) {
            // The grid fills the space between the month selector and the
            // bottom bar: every row — and every day cell — is exactly the
            // same height, computed per device. Content is top-anchored.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                WeekdayHeader(settings.weekStart)
                rows.forEach { week ->
                    GridWeekRow(
                        week = week,
                        settings = settings,
                        selectedDay = selectedDay,
                        today = today,
                        periodNumberFor = periodNumberFor,
                        yearEndFor = yearEndFor,
                        yearNumberFor = yearNumberFor,
                        context = context,
                        onOpenDay = onOpenDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        } else {
            // Short screens or a very tall month: the rows keep their
            // minimum height and the grid scrolls instead of clipping.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(gridScrollState)
                    .padding(horizontal = 12.dp),
            ) {
                WeekdayHeader(settings.weekStart)
                rows.forEach { week ->
                    GridWeekRow(
                        week = week,
                        settings = settings,
                        selectedDay = selectedDay,
                        today = today,
                        periodNumberFor = periodNumberFor,
                        yearEndFor = yearEndFor,
                        yearNumberFor = yearNumberFor,
                        context = context,
                        onOpenDay = onOpenDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_ROW_HEIGHT),
                    )
                }
            }
        }
    }
}

/** One grid week: seven equal-width cells, content top-anchored. */
@Composable
private fun GridWeekRow(
    week: List<MonthDay?>,
    settings: CalendarDisplaySettings,
    selectedDay: LocalDate?,
    today: LocalDate?,
    periodNumberFor: (LocalDate) -> Int?,
    yearEndFor: (LocalDate) -> LocalDate?,
    yearNumberFor: (LocalDate) -> Int?,
    context: android.content.Context,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Minimum height a grid row needs for its bounded content (day number, up to
 * two marker rows, two activity lines and the overflow count). Used as the
 * fill-vs-scroll threshold and the scroll-branch row minimum.
 */
private val MIN_ROW_HEIGHT = 56.dp

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
private fun FullListToggle(
    fullListMode: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .clickable(onClick = onToggle)
                .testTag("calendar_full_list_toggle"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (fullListMode) {
                        Icons.Filled.DateRange
                    } else {
                        Icons.AutoMirrored.Filled.List
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (fullListMode) {
                        stringResource(R.string.calendar_grid)
                    } else {
                        stringResource(R.string.calendar_full_list)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
    // The day list restores its position when the screen returns from the
    // day route.
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedIndex,
        initialFirstVisibleItemScrollOffset = savedOffset,
    )
    DisposableEffect(Unit) {
        onDispose {
            savedIndex = listState.firstVisibleItemIndex
            savedOffset = listState.firstVisibleItemScrollOffset
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_day_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
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

            val bodyParts = buildList {
                // Each row spells out its schedule events and day activity,
                // as the CALENDAR-DAY-LIST board requires.
                markers.forEach { add(it.spoken) }
                day.entries.forEach { entry ->
                    val direction = entry.direction ?: ""
                    add(
                        "${BalancePresentation.shortLabel(context, entry.category)} " +
                            "${BalancePresentation.formatHours(entry.hours)} h $direction",
                    )
                }
            }
            val body = if (bodyParts.isEmpty()) {
                context.getString(R.string.day_no_entries)
            } else {
                bodyParts.joinToString(". ")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDay(day.date) }
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .testTag("calendar_day_${day.date}")
                    .padding(vertical = 6.dp, horizontal = 12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = day.date.format(
                                    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()),
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "\u203A",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
