package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object CalculationHubRoute

@Serializable
object DateTimeSetupRoute

@Serializable
object CalculationMembersRoute

@Serializable
data class EmployeeCalculationRoute(val memberId: String)

@Serializable
object CalculationTableRoute

@Serializable
object MasterEmployeesRoute

@Serializable
object TotalRoute

@Serializable
object HistoryRoute

@Serializable
object ScanRoute
