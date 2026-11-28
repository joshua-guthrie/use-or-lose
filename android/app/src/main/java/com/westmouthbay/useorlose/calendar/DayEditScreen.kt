package com.westmouthbay.useorlose.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westmouthbay.useorlose.R
import com.westmouthbay.useorlose.core.EditPreview
import com.westmouthbay.useorlose.core.CoreError
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_EDIT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())

/**
 * The day editor from I08: the group selector, the hours/comment form, the
 * save review with the exact replaced and created IDs, and the add-entry
 * form. When the tapped date is uncovered, the form offers setup instead.
 */
@Composable
fun DayEditScreen(
    viewModel: DayEditViewModel,
    onBack: () -> Unit,
    onSetUp: (LocalDate) -> Unit,
    onCreated: () -> Unit,
    onAddEntry: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val date = state.date

    androidx.compose.runtime.LaunchedEffect(state.published) {
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
            if (state.mode == EditMode.EDIT && state.step == DayEditStep.FORM) {
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.TextButton(
                    onClick = { onAddEntry?.invoke() },
                    modifier = Modifier.testTag("day_edit_top_add"),
                ) {
                    Text(stringResource(R.string.day_add_title))
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (state.step == DayEditStep.FORM) {
                Text(
                    text = stringResource(
                        if (state.mode == EditMode.ADD) R.string.day_add_title else R.string.day_edit_title,
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                date?.let {
                    Text(
                        text = it.format(DAY_EDIT_DATE_FORMAT),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            if (state.yearMissing) {
                Text(
                    text = stringResource(R.string.day_edit_uncovered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                PrimaryAction(
                    text = stringResource(R.string.setup_action),
                    onClick = { date?.let(onSetUp) },
                    modifier = Modifier.padding(vertical = 4.dp).testTag("day_edit_setup"),
                )
                return@Column
            }

            when (state.step) {
                DayEditStep.FORM -> if (state.mode == EditMode.ADD) {
                    AddEntryForm(viewModel, state, context)
                } else {
                    EditDayForm(viewModel, state, context)
                }
                DayEditStep.REVIEW -> ReviewStep(viewModel, state, context)
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
                }
            }
        }
    }
}

private fun errorText(context: android.content.Context, error: CoreError): String =
    error.message.orEmpty().ifBlank { context.getString(R.string.unavailable) }

@Composable
private fun GroupSelector(viewModel: DayEditViewModel, state: DayEditUiState) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val selected = state.selectedCategory?.let { category ->
        state.selectedDirection?.let { direction ->
            category to direction
        }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("day_edit_group"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.day_edit_group_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selected?.let { (category, direction) ->
                        context.getString(
                            R.string.day_edit_group_value,
                            BalancePresentation.fullLabel(context, category),
                            BalancePresentation.directionLabel(context, direction),
                        )
                    } ?: stringResource(R.string.day_edit_group_choose),
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.groups.forEach { group ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                context.getString(
                                    R.string.day_edit_group_value,
                                    BalancePresentation.fullLabel(context, group.category),
                                    BalancePresentation.directionLabel(context, group.direction),
                                ),
                            )
                        },
                        onClick = {
                            expanded = false
                            viewModel.onSelectGroup(group.category, group.direction)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditDayForm(
    viewModel: DayEditViewModel,
    state: DayEditUiState,
    context: android.content.Context,
) {
    GroupSelector(viewModel, state)
    OutlinedTextField(
        value = state.hoursText,
        onValueChange = viewModel::onHoursChanged,
        label = {
            Text(
                context.getString(
                    R.string.day_edit_hours_label,
                    BalancePresentation.formatHours(state.incrementHours),
                ),
            )
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("day_edit_hours"),
    )
    OutlinedTextField(
        value = state.comment,
        onValueChange = viewModel::onCommentChanged,
        label = { Text(stringResource(R.string.day_edit_comment_label)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("day_edit_comment"),
    )
    val selected = state.selectedCategory?.let { c -> state.selectedDirection?.let { d -> c to d } }
    val groupCount = state.groups.firstOrNull { it.category == state.selectedCategory && it.direction == state.selectedDirection }?.entryCount
    if (selected != null && groupCount != null && groupCount > 0) {
        SecondaryAction(
            text = context.getString(R.string.day_edit_view_entries, groupCount),
            onClick = { viewModel.onViewEntries() },
            modifier = Modifier.padding(vertical = 4.dp).testTag("day_edit_view_entries"),
        )
    }
    state.entryDetails.forEach { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
    state.untouchedRows.forEach { row ->
        val (category, direction, hoursText) = row.split("|", limit = 3)
        Text(
            text = context.getString(
                R.string.day_edit_untouched,
                BalancePresentation.fullLabel(context, category),
                BalancePresentation.formatHours(hoursText.toDoubleOrNull() ?: 0.0) + "",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
    if (selected != null) {
        PrimaryAction(
            text = stringResource(R.string.day_edit_review_action),
            onClick = viewModel::onPreview,
            modifier = Modifier.padding(vertical = 4.dp).testTag("day_edit_review"),
        )
    }
    SecondaryAction(
        text = stringResource(R.string.cancel),
        onClick = viewModel::onBackToDraft,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun AddTypeDirectionRows(viewModel: DayEditViewModel, state: DayEditUiState) {
    val context = LocalContext.current
    var typeExpanded by remember { mutableStateOf(false) }
    var directionExpanded by remember { mutableStateOf(false) }
    val categories = state.groups.map { it.category }.distinct()
    val directions = state.selectedCategory?.let { category ->
        state.groups.filter { it.category == category }.map { it.direction }.distinct()
    } ?: emptyList()
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.day_add_group_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(
                onClick = { typeExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.selectedCategory?.let { BalancePresentation.fullLabel(context, it) }
                        ?: stringResource(R.string.day_edit_group_choose),
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(BalancePresentation.fullLabel(context, category)) },
                        onClick = {
                            typeExpanded = false
                            val first = state.groups.firstOrNull { it.category == category }
                            if (first != null) {
                                viewModel.onSelectGroup(first.category, first.direction)
                            }
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.day_add_direction_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.TextButton(
                onClick = { directionExpanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.selectedDirection?.let { BalancePresentation.directionLabel(context, it) }
                        ?: stringResource(R.string.day_edit_group_choose),
                    modifier = Modifier.weight(1f),
                )
            }
            DropdownMenu(expanded = directionExpanded, onDismissRequest = { directionExpanded = false }) {
                directions.forEach { direction ->
                    DropdownMenuItem(
                        text = { Text(BalancePresentation.directionLabel(context, direction)) },
                        onClick = {
                            directionExpanded = false
                            state.selectedCategory?.let { category ->
                                viewModel.onSelectGroup(category, direction)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEntryForm(
    viewModel: DayEditViewModel,
    state: DayEditUiState,
    context: android.content.Context,
) {
    // The add form picks the leave type and direction directly; the
    // stored default status applies to the new entry.
    AddTypeDirectionRows(viewModel, state)
    val selected = state.selectedCategory?.let { c -> state.selectedDirection?.let { d -> c to d } }
    OutlinedTextField(
        value = state.hoursText,
        onValueChange = viewModel::onHoursChanged,
        label = { Text(context.getString(R.string.day_add_hours_label)) },
        suffix = { Text(context.getString(R.string.hours_suffix)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("day_add_hours"),
    )
    OutlinedTextField(
        value = state.comment,
        onValueChange = viewModel::onCommentChanged,
        label = { Text(stringResource(R.string.day_edit_comment_label)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("day_add_comment"),
    )
    if (selected != null) {
        PrimaryAction(
            text = stringResource(R.string.day_add_submit),
            onClick = viewModel::onPreview,
            modifier = Modifier.padding(vertical = 4.dp).testTag("day_add_submit"),
        )
    }
    SecondaryAction(
        text = stringResource(R.string.cancel),
        onClick = viewModel::onBackToDraft,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ReviewStep(
    viewModel: DayEditViewModel,
    state: DayEditUiState,
    context: android.content.Context,
) {
    Text(
        text = stringResource(R.string.day_review_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
    state.date?.let { date ->
        Text(
            text = date.format(DAY_EDIT_DATE_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    when (val preview = state.preview) {
        is EditPreview.Day -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("day_edit_review"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = context.getString(
                            R.string.day_review_card,
                            BalancePresentation.fullLabel(context, preview.value.category),
                            BalancePresentation.directionLabel(context, preview.value.direction),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(
                            R.string.day_review_body,
                            preview.value.removedTransactionIds.joinToString(" and "),
                            context.getString(
                                R.string.day_review_use_label,
                                BalancePresentation.fullLabel(context, preview.value.category),
                                BalancePresentation.directionLabel(context, preview.value.direction),
                            ),
                            BalancePresentation.formatHours(preview.value.beforeHours),
                            BalancePresentation.formatHours(preview.value.afterHours),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview.value.comment.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.day_edit_comment_label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = preview.value.comment,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.untouchedRows.forEach { row ->
                val parts = row.split("|", limit = 3)
                if (parts.size == 3) {
                    Text(
                        text = context.getString(
                            R.string.day_edit_untouched,
                            BalancePresentation.fullLabel(context, parts[0]),
                            BalancePresentation.formatHours(parts[2].toDoubleOrNull() ?: 0.0),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            PrimaryAction(
                text = stringResource(R.string.day_save),
                onClick = viewModel::onConfirm,
                modifier = Modifier.padding(vertical = 4.dp).testTag("day_edit_save"),
            )
            SecondaryAction(
                text = stringResource(R.string.day_back_draft),
                onClick = viewModel::onBackToDraft,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        is EditPreview.Add -> {
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
                            R.string.day_review_add_card,
                            BalancePresentation.fullLabel(context, preview.value.category),
                            BalancePresentation.directionLabel(context, preview.value.direction),
                            BalancePresentation.formatHours(preview.value.hours),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (preview.value.comment.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = context.getString(R.string.day_review_comment, preview.value.comment),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PrimaryAction(
                text = stringResource(R.string.day_save),
                onClick = viewModel::onConfirm,
                modifier = Modifier.padding(vertical = 4.dp).testTag("day_edit_save"),
            )
            SecondaryAction(
                text = stringResource(R.string.day_back_draft),
                onClick = viewModel::onBackToDraft,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        is EditPreview.Mixed -> {
            Text(
                text = stringResource(R.string.day_edit_mixed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            preview.value.rows.forEach { row ->
                Text(
                    text = "${row.id} · ${BalancePresentation.fullLabel(context, row.category)} · " +
                        "${BalancePresentation.formatHours(row.hours)} · ${row.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            SecondaryAction(
                text = stringResource(R.string.day_back_draft),
                onClick = viewModel::onBackToDraft,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        null -> Unit
    }
}

