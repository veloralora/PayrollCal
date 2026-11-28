package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.AppDatabase
import com.example.data.ocr.MlKitOcrEngine
import com.example.data.preferences.SettingsStore
import com.example.data.repository.PayrollRepository
import com.example.domain.PayrollMonth
import com.example.ui.DateTimeSetupState
import com.example.ui.PayrollViewModel
import com.example.ui.components.FormulaEditorSheet
import com.example.ui.components.SettingsBackupSheet
import com.example.ui.navigation.CalculationHubRoute
import com.example.ui.navigation.CalculationMembersRoute
import com.example.ui.navigation.CalculationTableRoute
import com.example.ui.navigation.DateTimeSetupRoute
import com.example.ui.navigation.EmployeeCalculationRoute
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.MasterEmployeesRoute
import com.example.ui.navigation.ScanRoute
import com.example.ui.navigation.TotalRoute
import com.example.ui.screens.CalculationHubScreen
import com.example.ui.screens.CalculationMembersScreen
import com.example.ui.screens.CalculationTableScreen
import com.example.ui.screens.DateTimeSetupScreen
import com.example.ui.screens.EmployeeCalculationScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MasterEmployeesScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.TotalScreen
import com.example.ui.theme.HitungGajianProTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val settingsStore = SettingsStore(applicationContext)
        val repository = PayrollRepository(db.payrollDao(), settingsStore)
        val ocrEngine = MlKitOcrEngine(applicationContext)

        setContent {
            HitungGajianProTheme {
                val viewModel: PayrollViewModel = viewModel(
                    factory = PayrollViewModel.provideFactory(repository, ocrEngine)
                )
                HitungGajianApp(viewModel = viewModel)
            }
        }
    }
}

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(
        route = HomeRoute,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    TopLevelDestination(
        route = CalculationHubRoute,
        labelRes = R.string.nav_calculator,
        selectedIcon = Icons.Filled.Calculate,
        unselectedIcon = Icons.Outlined.Calculate,
        testTag = "nav_calculator"
    ),
    TopLevelDestination(
        route = MasterEmployeesRoute,
        labelRes = R.string.nav_employees,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        testTag = "nav_employees"
    ),
    TopLevelDestination(
        route = TotalRoute,
        labelRes = R.string.nav_summary,
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong,
        testTag = "nav_summary"
    ),
    TopLevelDestination(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_history"
    )
)

@Composable
private fun HitungGajianApp(viewModel: PayrollViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val masterEmployees by viewModel.masterEmployees.collectAsStateWithLifecycle()
    val globalFormula by viewModel.globalFormula.collectAsStateWithLifecycle()
    val roundingConfig by viewModel.roundingConfig.collectAsStateWithLifecycle()
    val savedCalculations by viewModel.savedCalculations.collectAsStateWithLifecycle()
    val selectedTotalMonth by viewModel.selectedTotalMonth.collectAsStateWithLifecycle()
    val availableTotalMonths by viewModel.availableTotalMonths.collectAsStateWithLifecycle()
    val monthCalculationCounts by viewModel.monthCalculationCounts.collectAsStateWithLifecycle()
    val monthlySavedCalculations by viewModel.monthlySavedCalculations.collectAsStateWithLifecycle()
    val monthlyTotalSummary by viewModel.monthlyTotalSummary.collectAsStateWithLifecycle()
    val currentMonthCalculations by viewModel.currentMonthCalculations.collectAsStateWithLifecycle()
    val currentMonthTotalPayroll by viewModel.currentMonthTotalPayroll.collectAsStateWithLifecycle()
    val autoSaveStatus by viewModel.autoSaveStatus.collectAsStateWithLifecycle()
    val needsMasterGuide by viewModel.needsMasterEmployeeGuide.collectAsStateWithLifecycle()
    val dateTimeSetup by viewModel.dateTimeSetup.collectAsStateWithLifecycle()
    val activeDraft by viewModel.activeDraft.collectAsStateWithLifecycle()
    val computedActiveDraft by viewModel.computedActiveDraft.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGlobalFormulaSheet by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToUri(context, uri)
            showSettingsSheet = false
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showSettingsSheet = false
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isFullScreenSubFlow = currentDestination?.let { dest ->
        dest.hasRoute(DateTimeSetupRoute::class) ||
            dest.hasRoute(CalculationMembersRoute::class) ||
            dest.hasRoute(EmployeeCalculationRoute::class) ||
            dest.hasRoute(CalculationTableRoute::class) ||
            dest.hasRoute(ScanRoute::class)
    } ?: false

    val navigateTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val startCreateCalculationFlow: () -> Unit = {
        viewModel.beginCreateCalculation(
            onEmptyMasterRedirect = { navigateTopLevel(MasterEmployeesRoute) },
            onProceedToDateTime = { navController.navigate(DateTimeSetupRoute) }
        )
    }

    val openSavedCalculationFlow: (Long) -> Unit = { calcId ->
        viewModel.openSavedCalculation(calcId) {
            navController.navigate(CalculationMembersRoute)
        }
    }

    val saveCalculationAndGoHistory: () -> Unit = {
        viewModel.saveActiveCalculation {
            navController.navigate(HistoryRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isFullScreenSubFlow) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isFullScreenSubFlow) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TOP_LEVEL_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            currentMonth = PayrollMonth.now(),
                            currentMonthTotalPayroll = currentMonthTotalPayroll,
                            currentMonthCalculationCount = currentMonthCalculations.size,
                            savedCalculationCount = savedCalculations.size,
                            masterEmployeeCount = masterEmployees.size,
                            globalFormula = globalFormula,
                            onCreateCalculation = startCreateCalculationFlow,
                            onNavigateToMasterEmployees = { navigateTopLevel(MasterEmployeesRoute) },
                            onNavigateToTotal = { navigateTopLevel(TotalRoute) },
                            onNavigateToHistory = { navigateTopLevel(HistoryRoute) },
                            onNavigateToScan = { navController.navigate(ScanRoute) },
                            onOpenSettings = { showSettingsSheet = true },
                            onOpenGlobalFormulaEditor = { showGlobalFormulaSheet = true }
                        )
                    }

                    composable<CalculationHubRoute> {
                        CalculationHubScreen(
                            masterEmployeeCount = masterEmployees.size,
                            globalFormula = globalFormula,
                            activeDraft = activeDraft,
                            computedDraft = computedActiveDraft,
                            recentCalculations = savedCalculations,
                            onCreateCalculation = startCreateCalculationFlow,
                            onResumeActiveDraft = { navController.navigate(CalculationMembersRoute) },
                            onOpenSavedCalculation = openSavedCalculationFlow,
                            onNavigateToMasterEmployees = { navigateTopLevel(MasterEmployeesRoute) }
                        )
                    }

                    composable<DateTimeSetupRoute> {
                        DateTimeSetupScreen(
                            state = dateTimeSetup,
                            masterEmployeeCount = masterEmployees.size,
                            onBack = { navController.popBackStack() },
                            onUpdateDateTime = viewModel::updateDateTimeSetup,
                            onResetToNow = {
                                viewModel.setDateTimeFromMillis(DateTimeSetupState.now().toMillis())
                            },
                            onConfirmAndContinue = {
                                viewModel.confirmDateTimeAndLoadMembers {
                                    navController.navigate(CalculationMembersRoute) {
                                        popUpTo(DateTimeSetupRoute) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable<CalculationMembersRoute> {
                        CalculationMembersScreen(
                            draft = activeDraft,
                            computed = computedActiveDraft,
                            globalFormula = globalFormula,
                            autoSaveStatus = autoSaveStatus,
                            onBack = {
                                viewModel.flushAutoSaveNow()
                                navController.popBackStack()
                            },
                            onSelectEmployee = { memberId ->
                                navController.navigate(EmployeeCalculationRoute(memberId))
                            },
                            onAddOneOffMember = viewModel::addOneOffMemberToDraft,
                            onRenameMember = viewModel::renameDraftMember,
                            onRemoveMember = viewModel::removeDraftMember,
                            onToggleFormula = viewModel::toggleActiveDraftFormula,
                            onApplyFormulaPerList = viewModel::applyFormulaPerList,
                            onApplyFormulaGlobal = { viewModel.applyFormulaGlobal(it, attachActiveDraftToGlobal = true) },
                            onUseGlobalFormula = viewModel::revertDraftToGlobalFormula,
                            onOpenCalculationTable = { navController.navigate(CalculationTableRoute) },
                            onSaveTotal = saveCalculationAndGoHistory
                        )
                    }

                    composable<EmployeeCalculationRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<EmployeeCalculationRoute>()
                        EmployeeCalculationScreen(
                            memberId = route.memberId,
                            draft = activeDraft,
                            computed = computedActiveDraft,
                            globalFormula = globalFormula,
                            roundingConfig = roundingConfig,
                            autoSaveStatus = autoSaveStatus,
                            onBackToMembers = { navController.popBackStack() },
                            onFlushOnLeave = { viewModel.flushAutoSaveNow() },
                            onSaveFallback = { viewModel.flushAutoSaveNow(showConfirmationToast = true) },
                            onSwitchMember = { targetId ->
                                viewModel.flushAutoSaveNow()
                                navController.navigate(EmployeeCalculationRoute(targetId)) {
                                    popUpTo(EmployeeCalculationRoute(route.memberId)) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onAddRow = { viewModel.addRowToMember(route.memberId) },
                            onUpdateRow = { rowId, n1, n2, note ->
                                viewModel.updateMemberRow(route.memberId, rowId, n1, n2, note)
                            },
                            onToggleRowFormula = { rowId ->
                                viewModel.toggleMemberRowFormula(route.memberId, rowId)
                            },
                            onRemoveRow = { rowId ->
                                viewModel.removeRowFromMember(route.memberId, rowId)
                            },
                            onToggleFormula = viewModel::toggleActiveDraftFormula,
                            onApplyFormulaPerList = viewModel::applyFormulaPerList,
                            onApplyFormulaGlobal = { viewModel.applyFormulaGlobal(it, attachActiveDraftToGlobal = true) },
                            onUseGlobalFormula = viewModel::revertDraftToGlobalFormula,
                            onUpdateRounding = viewModel::updateRoundingConfig,
                            onAddAdjustmentColumn = { name, op ->
                                viewModel.addAdjustmentColumnForMember(route.memberId, name, op)
                            },
                            onUpdateAdjustmentColumn = { colId, name, op ->
                                viewModel.updateAdjustmentColumnForMember(route.memberId, colId, name, op)
                            },
                            onDeleteAdjustmentColumn = { colId ->
                                viewModel.deleteAdjustmentColumnForMember(route.memberId, colId)
                            },
                            onUpdateAdjustmentValue = { colId, value ->
                                viewModel.updateMemberAdjustmentValue(route.memberId, colId, value)
                            },
                            onOpenScanForMember = {
                                viewModel.flushAutoSaveNow()
                                viewModel.preselectScanMember(route.memberId)
                                navController.navigate(ScanRoute)
                            }
                        )
                    }

                    composable<CalculationTableRoute> {
                        CalculationTableScreen(
                            draft = activeDraft,
                            computed = computedActiveDraft,
                            globalFormula = globalFormula,
                            roundingConfig = roundingConfig,
                            onBackToMembers = { navController.popBackStack() },
                            onOpenEmployeeDetail = { memberId ->
                                navController.navigate(EmployeeCalculationRoute(memberId))
                            },
                            onToggleFormula = viewModel::toggleActiveDraftFormula,
                            onApplyFormulaPerList = viewModel::applyFormulaPerList,
                            onApplyFormulaGlobal = { viewModel.applyFormulaGlobal(it, attachActiveDraftToGlobal = true) },
                            onUseGlobalFormula = viewModel::revertDraftToGlobalFormula,
                            onUpdateRounding = viewModel::updateRoundingConfig,
                            onSaveTotal = saveCalculationAndGoHistory,
                            onOpenScan = { navController.navigate(ScanRoute) },
                            onCopiedFeedback = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<MasterEmployeesRoute> {
                        MasterEmployeesScreen(
                            employees = masterEmployees,
                            showCreationGuide = needsMasterGuide,
                            onAddEmployee = viewModel::addMasterEmployee,
                            onRenameEmployee = viewModel::renameMasterEmployee,
                            onDeleteEmployee = viewModel::deleteMasterEmployee,
                            onProceedCreateCalculation = {
                                viewModel.clearMasterEmployeeGuide()
                                startCreateCalculationFlow()
                            },
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }

                    composable<TotalRoute> {
                        TotalScreen(
                            selectedMonth = selectedTotalMonth,
                            availableMonths = availableTotalMonths,
                            monthCalculationCounts = monthCalculationCounts,
                            monthlyCalculations = monthlySavedCalculations,
                            monthlyTotalSummary = monthlyTotalSummary,
                            onSelectMonth = viewModel::selectTotalMonth,
                            onStepMonth = viewModel::stepTotalMonth,
                            onOpenSavedCalculation = openSavedCalculationFlow,
                            onDeleteCalculation = viewModel::deleteSavedCalculation,
                            onCreateCalculation = startCreateCalculationFlow,
                            onCopiedFeedback = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<HistoryRoute> {
                        HistoryScreen(
                            calculations = savedCalculations,
                            onOpenCalculation = openSavedCalculationFlow,
                            onDeleteCalculation = viewModel::deleteSavedCalculation,
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }

                    composable<ScanRoute> {
                        ScanScreen(
                            scanState = scanState,
                            activeMembers = activeDraft.members,
                            onBack = { navController.popBackStack() },
                            onImageSelected = viewModel::processScannedImage,
                            onUpdateDetectedName = viewModel::updateScanDetectedName,
                            onSelectTargetMember = viewModel::updateScanTargetMember,
                            onAddRow = viewModel::addScanRow,
                            onUpdateRow = viewModel::updateScanRow,
                            onSwapRow = viewModel::swapScanRow,
                            onRemoveRow = viewModel::removeScanRow,
                            onConfirmApply = {
                                viewModel.confirmScanIntoActiveCalculation { memberId ->
                                    navController.popBackStack()
                                    if (navController.currentDestination?.hasRoute(EmployeeCalculationRoute::class) != true) {
                                        navController.navigate(EmployeeCalculationRoute(memberId))
                                    }
                                }
                            },
                            onClearScan = viewModel::clearScanState
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsBackupSheet(
            globalFormula = globalFormula,
            roundingConfig = roundingConfig,
            onDismiss = { showSettingsSheet = false },
            onOpenGlobalFormulaEditor = { showGlobalFormulaSheet = true },
            onUpdateRounding = viewModel::updateRoundingConfig,
            onExportBackup = {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                exportBackupLauncher.launch("backup_hitung_gajian_$stamp.json")
            },
            onImportBackup = {
                importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        )
    }

    if (showGlobalFormulaSheet) {
        FormulaEditorSheet(
            currentFormula = globalFormula,
            usesGlobalFormula = true,
            globalFormula = globalFormula,
            showPerListOption = false,
            onDismiss = { showGlobalFormulaSheet = false },
            onApplyPerList = {},
            onApplyGlobal = { viewModel.applyFormulaGlobal(it, attachActiveDraftToGlobal = false) },
            onUseGlobalFormula = {}
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Pulihkan Data Backup?") },
            text = {
                Text("Data Karyawan dan seluruh Riwayat saat ini akan diganti dengan data dari file backup yang Anda pilih. Lanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackupFromUri(context, uri)
                        pendingRestoreUri = null
                    },
                    modifier = Modifier.testTag("confirm_restore_button")
                ) {
                    Text("Pulihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
