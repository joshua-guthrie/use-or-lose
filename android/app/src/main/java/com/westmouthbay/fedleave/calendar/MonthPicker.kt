package com.westmouthbay.fedleave.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.westmouthbay.fedleave.R
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Choose month: a calendar-year chip row and the twelve month chip rows,
 * matching `calendar-compact-month.png`. Selecting a month updates the grid
 * and preserves the balance cutoff; Cancel (or system Back) closes without
 * changing anything.
 */
@Composable
fun MonthPicker(
    viewModel: CalendarViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val displayMonth = state.displayMonth
    var yearExpanded by remember { mutableStateOf(false) }
    var selectedYear by remember(displayMonth) {
        mutableStateOf(displayMonth?.year ?: state.today?.year ?: YearMonth.now().year)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = stringResource(R.string.calendar_choose_month),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            YearChipRow(
                selectedYear = selectedYear,
                years = yearChoices(viewModel.availableYears(), selectedYear),
                expanded = yearExpanded,
                onExpand = { yearExpanded = true },
                onDismiss = { yearExpanded = false },
                onYearSelected = {
                    yearExpanded = false
                    selectedYear = it
                },
            )
            Spacer(Modifier.height(4.dp))
            for (month in Month.entries) {
                val isCurrent = selectedYear == displayMonth?.year &&
                    month.value == displayMonth?.monthValue
                MonthChipRow(
                    month = month,
                    isCurrent = isCurrent,
                    onClick = {
                        viewModel.onSelectMonth(YearMonth.of(selectedYear, month.value))
                        onClose()
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable(onClick = onClose),
        ) {
            Text(
                text = stringResource(R.string.cancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun YearChipRow(
    selectedYear: Int,
    years: List<Int>,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onYearSelected: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
    ) {
        Box {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.calendar_year_row),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = selectedYear.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
            ) {
                years.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = { onYearSelected(year) },
                    )
                }
            }
        }
    }
}

/**
 * Calendar months are browsable one year beyond each stored leave year
 * (e.g. January 2027 on the 2026 ledger), plus the currently selected year.
 */
private fun yearChoices(storedYears: List<Int>, selectedYear: Int): List<Int> =
    (
        storedYears +
            storedYears.flatMap { listOf(it - 1, it + 1) } +
            selectedYear
        ).distinct().sorted()

@Composable
private fun MonthChipRow(
    month: Month,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isCurrent) {
                    stringResource(R.string.calendar_current)
                } else {
                    "\u203A"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
