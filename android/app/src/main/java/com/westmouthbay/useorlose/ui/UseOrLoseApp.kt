package com.westmouthbay.useorlose.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.westmouthbay.useorlose.R
import com.westmouthbay.useorlose.calendar.BalanceDatePicker
import com.westmouthbay.useorlose.calendar.BalanceScreen
import com.westmouthbay.useorlose.calendar.CalendarViewModel
import com.westmouthbay.useorlose.calendar.DayEditScreen
import com.westmouthbay.useorlose.calendar.DayEditViewModel
import com.westmouthbay.useorlose.calendar.DayScreen
import com.westmouthbay.useorlose.calendar.EditMode
import com.westmouthbay.useorlose.calendar.MonthPicker
import com.westmouthbay.useorlose.calendar.CalendarScreen
import com.westmouthbay.useorlose.calendar.SetupScreen
import com.westmouthbay.useorlose.calendar.SetupViewModel
import com.westmouthbay.useorlose.calendar.WelcomeScreen
import com.westmouthbay.useorlose.core.AppDependencies
import java.time.LocalDate

/**
 * The app root: bottom bar with the three destinations from the mockups
 * (Calendar, Reports, Settings — Reports and Settings are placeholder
 * destinations until I13/I18), the navigation host, and the activity-scoped
 * Calendar ViewModel that owns state across rotation and route return.
 */
@Composable
fun UseOrLoseApp(dependencies: AppDependencies) {
    val navController = rememberNavController()
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val calendarViewModel: CalendarViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = CalendarViewModel.factory(dependencies),
    )
    val setupViewModel: SetupViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = SetupViewModel.factory(dependencies),
    )
    val dayEditViewModel: DayEditViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = DayEditViewModel.factory(dependencies),
    )
    val calendarState by calendarViewModel.state.collectAsState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        calendarViewModel.onLaunch()
    }

    // A store that cannot open (first use) routes to the setup welcome.
    LaunchedEffect(currentRoute, calendarState.setupRequired) {
        if (currentRoute == AppRoutes.CALENDAR && calendarState.setupRequired) {
            navController.navigate(AppRoutes.WELCOME) { launchSingleTop = true }
        }
    }

    // The tapped day stays selected after Back so the Calendar restores its
    // month, scroll context and focus on the tapped date; Menu > Today and
    // the next day tap replace it.

    Scaffold(
        bottomBar = { UseOrLoseBottomBar(currentRoute, navController::navigateToTab) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.CALENDAR,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppRoutes.CALENDAR) {
                CalendarScreen(
                    viewModel = calendarViewModel,
                    onOpenDay = { date ->
                        calendarViewModel.onOpenDay(date)
                        navController.navigate(AppRoutes.day(date))
                    },
                    onOpenMonthPicker = { navController.navigate(AppRoutes.MONTH_PICKER) },
                    onOpenBalances = { navController.navigate(AppRoutes.BALANCES) },
                    onSetUpForMonth = { month ->
                        setupViewModel.begin(
                            calendarViewModel.setupProposalFor(month.atDay(1)),
                            resumeDate = null,
                        )
                        navController.navigate(AppRoutes.setup(month.atDay(1).toString()))
                    },
                )
            }
            composable(AppRoutes.BALANCES) {
                BalanceScreen(
                    viewModel = calendarViewModel,
                    onChooseDate = {
                        calendarViewModel.onChooseDate()
                        navController.navigate(AppRoutes.BALANCE_DATE_PICKER)
                    },
                    onBack = { navController.popBackStack() },
                    onSetUp = { date ->
                        navController.navigate(AppRoutes.setup(date.toString()))
                    },
                )
            }
            composable(AppRoutes.BALANCE_DATE_PICKER) {
                BalanceDatePicker(
                    viewModel = calendarViewModel,
                    onConfirm = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.WELCOME) {
                WelcomeScreen(
                    onOpenSetup = {
                        val today = calendarViewModel.state.value.today ?: LocalDate.now()
                        setupViewModel.begin(
                            calendarViewModel.setupProposalFor(today),
                            resumeDate = null,
                        )
                        navController.navigate(AppRoutes.setup(AppRoutes.SETUP_FOR_WELCOME))
                    },
                )
            }
            composable(
                route = AppRoutes.SETUP,
                arguments = listOf(navArgument(AppRoutes.SETUP_ARG) { type = NavType.StringType }),
            ) { entry ->
                val forDate = entry.arguments?.getString(AppRoutes.SETUP_ARG)
                val resumeDate = forDate?.takeIf { it != AppRoutes.SETUP_FOR_WELCOME }
                LaunchedEffect(forDate) {
                    val target = runCatching { LocalDate.parse(forDate ?: "") }.getOrNull()
                        ?: calendarViewModel.state.value.today
                        ?: LocalDate.now()
                    setupViewModel.begin(calendarViewModel.setupProposalFor(target), resumeDate)
                }
                SetupScreen(
                    viewModel = setupViewModel,
                    onExit = { navController.popBackStack() },
                    onCreated = {
                        calendarViewModel.refreshAfterSetup()
                        navController.popBackStack(AppRoutes.CALENDAR, inclusive = false)
                        val editorDraft = calendarViewModel.consumeEditorDraft()
                        if (editorDraft != null) {
                            calendarViewModel.stashEditorDraft(editorDraft)
                            navController.navigate(
                                if (editorDraft.mode == EditMode.ADD) {
                                    AppRoutes.dayAdd(editorDraft.date)
                                } else {
                                    AppRoutes.dayEdit(editorDraft.date)
                                },
                            )
                        }
                    },
                )
            }
            composable(
                route = AppRoutes.DAY,
                arguments = listOf(navArgument(AppRoutes.DAY_ARG) { type = NavType.StringType }),
            ) { entry ->
                val date = LocalDate.parse(
                    checkNotNull(entry.arguments?.getString(AppRoutes.DAY_ARG)),
                )
                DayScreen(
                    viewModel = calendarViewModel,
                    date = date,
                    onBack = { navController.popBackStack() },
                    onEditDay = {
                        calendarViewModel.stashEditorDraft(
                            com.westmouthbay.useorlose.calendar.EditorDraft(
                                date = date,
                                mode = EditMode.EDIT,
                                category = "",
                                direction = "",
                                hoursText = "",
                                comment = "",
                            ),
                        )
                        navController.navigate(AppRoutes.dayEdit(date))
                    },
                    onAddEntry = {
                        navController.navigate(AppRoutes.dayAdd(date))
                    },
                )
            }
            composable(
                route = AppRoutes.DAY_EDIT,
                arguments = listOf(navArgument(AppRoutes.DAY_ARG) { type = NavType.StringType }),
            ) { entry ->
                val date = LocalDate.parse(
                    checkNotNull(entry.arguments?.getString(AppRoutes.DAY_ARG)),
                )
                LaunchedEffect(date) {
                    dayEditViewModel.begin(date, calendarViewModel.consumeEditorDraft())
                }
                DayEditScreen(
                    viewModel = dayEditViewModel,
                    onBack = { navController.popBackStack() },
                    onSetUp = { target ->
                        calendarViewModel.stashEditorDraft(
                            checkNotNull(dayEditViewModel.draft()),
                        )
                        navController.navigate(AppRoutes.setup(target.toString()))
                    },
                    onCreated = {
                        calendarViewModel.refreshAfterSetup()
                        navController.popBackStack(AppRoutes.CALENDAR, inclusive = false)
                    },
                )
            }
            composable(
                route = AppRoutes.DAY_ADD,
                arguments = listOf(navArgument(AppRoutes.DAY_ARG) { type = NavType.StringType }),
            ) { entry ->
                val date = LocalDate.parse(
                    checkNotNull(entry.arguments?.getString(AppRoutes.DAY_ARG)),
                )
                LaunchedEffect(date) {
                    dayEditViewModel.begin(date, calendarViewModel.consumeEditorDraft(), EditMode.ADD)
                }
                DayEditScreen(
                    viewModel = dayEditViewModel,
                    onBack = { navController.popBackStack() },
                    onSetUp = { target ->
                        calendarViewModel.stashEditorDraft(
                            checkNotNull(dayEditViewModel.draft()),
                        )
                        navController.navigate(AppRoutes.setup(target.toString()))
                    },
                    onCreated = {
                        calendarViewModel.refreshAfterSetup()
                        navController.popBackStack(AppRoutes.CALENDAR, inclusive = false)
                    },
                )
            }
            composable(AppRoutes.MONTH_PICKER) {
                MonthPicker(
                    viewModel = calendarViewModel,
                    onClose = { navController.popBackStack() },
                )
            }
            composable(AppRoutes.REPORTS) {
                PlaceholderDestination(stringResource(R.string.tab_reports))
            }
            composable(AppRoutes.SETTINGS) {
                PlaceholderDestination(stringResource(R.string.tab_settings))
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(AppRoutes.CALENDAR) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun UseOrLoseBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            // The day, editor, setup and picker routes belong to the
            // Calendar tab: the tab stays selected while they are open.
            val onCalendar = currentRoute == AppRoutes.CALENDAR ||
                (currentRoute != null && (
                    currentRoute.startsWith(AppRoutes.DAY) ||
                        currentRoute.startsWith("day_edit") ||
                        currentRoute.startsWith("day_add") ||
                        currentRoute.startsWith("setup") ||
                        currentRoute == AppRoutes.WELCOME ||
                        currentRoute == AppRoutes.BALANCES ||
                        currentRoute == AppRoutes.BALANCE_DATE_PICKER ||
                        currentRoute == AppRoutes.MONTH_PICKER
                    ))
            BottomTab(
                label = stringResource(R.string.tab_calendar),
                selected = onCalendar,
                tag = "tab_calendar",
                onClick = { onSelect(AppRoutes.CALENDAR) },
                modifier = Modifier.weight(1f),
            )
            BottomTab(
                label = stringResource(R.string.tab_reports),
                selected = currentRoute == AppRoutes.REPORTS,
                tag = "tab_reports",
                onClick = { onSelect(AppRoutes.REPORTS) },
                modifier = Modifier.weight(1f),
            )
            BottomTab(
                label = stringResource(R.string.tab_settings),
                selected = currentRoute == AppRoutes.SETTINGS,
                tag = "tab_settings",
                onClick = { onSelect(AppRoutes.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomTab(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PlaceholderDestination(title: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title \u00b7 " +
                stringResource(R.string.placeholder_coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
