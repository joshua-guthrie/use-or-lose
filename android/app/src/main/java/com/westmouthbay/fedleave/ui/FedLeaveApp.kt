package com.westmouthbay.fedleave.ui

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
import com.westmouthbay.fedleave.R
import com.westmouthbay.fedleave.calendar.CalendarViewModel
import com.westmouthbay.fedleave.calendar.DayScreen
import com.westmouthbay.fedleave.calendar.MonthPicker
import com.westmouthbay.fedleave.calendar.CalendarScreen
import com.westmouthbay.fedleave.core.AppDependencies
import java.time.LocalDate

/**
 * The app root: bottom bar with the three destinations from the mockups
 * (Calendar, Reports, Settings — Reports and Settings are placeholder
 * destinations until I13/I18), the navigation host, and the activity-scoped
 * Calendar ViewModel that owns state across rotation and route return.
 */
@Composable
fun FedLeaveApp(dependencies: AppDependencies) {
    val navController = rememberNavController()
    val activity = checkNotNull(LocalActivity.current) as ComponentActivity
    val calendarViewModel: CalendarViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = CalendarViewModel.factory(dependencies),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        calendarViewModel.onLaunch()
    }

    // Returning to Calendar closes the read-only day route state.
    LaunchedEffect(currentRoute) {
        if (currentRoute == AppRoutes.CALENDAR) {
            calendarViewModel.onCloseDay()
        }
    }

    Scaffold(
        bottomBar = { FedLeaveBottomBar(currentRoute, navController::navigateToTab) },
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
private fun FedLeaveBottomBar(
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
            BottomTab(
                label = stringResource(R.string.tab_calendar),
                selected = currentRoute == AppRoutes.CALENDAR,
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
