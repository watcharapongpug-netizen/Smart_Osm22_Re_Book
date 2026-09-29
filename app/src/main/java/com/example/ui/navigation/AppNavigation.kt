package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DiagnosticScreen
import com.example.ui.DiagnosticViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.viewmodel.PersonViewModel

import com.example.data.vhv.VhvRepository
import com.example.viewmodel.VhvDirectoryViewModel

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : BottomNavItem("dashboard", "หน้าแรก", Icons.Filled.Dashboard)
    object Households : BottomNavItem("households", "ครัวเรือน", Icons.Filled.Home)
    object Map : BottomNavItem("map", "แผนที่", Icons.Filled.LocationOn)
    object Persons : BottomNavItem("persons", "ประชากร", Icons.Filled.People)
    object Info : BottomNavItem("developer_info", "ข้อมูล อสม.", Icons.Filled.Info)
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: PersonViewModel,
    repository: com.example.data.PersonRepository,
    firestore: com.google.firebase.firestore.FirebaseFirestore?,
    authViewModel: com.example.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier
) {
    val userProfile by authViewModel.userProfile.collectAsStateWithLifecycle()
    
    androidx.compose.runtime.LaunchedEffect(userProfile) {
        viewModel.setVillageFilter(userProfile?.villageNo)
        if (userProfile == null) {
            // If we are not on splash or login, and user logs out, go to login
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != "splash" && currentRoute != "login" && currentRoute != "vhv_registration") {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val items = listOf(
        BottomNavItem.Dashboard,
        BottomNavItem.Households,
        BottomNavItem.Persons,
        BottomNavItem.Map,
        BottomNavItem.Info
    )

    Scaffold(
        modifier = modifier,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Show bottom bar only on main tabs
            if (currentRoute in items.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.shadow(12.dp, spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { 
                                Text(
                                    item.title, 
                                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                ) 
                            },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(
                    onStartApp = {
                        val destination = if (authViewModel.currentUser.value != null) "pin_lock" else "login"
                        navController.navigate(destination) {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = {
                        val prefs = navController.context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        val isSetupDone = prefs.getBoolean("surveyor_setup_completed", false)
                        if (isSetupDone) {
                            navController.navigate("pin_lock") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            navController.navigate("vhv_registration") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    },
                    onContinueOffline = {
                        val prefs = navController.context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                        val isSetupDone = prefs.getBoolean("surveyor_setup_completed", false)
                        if (isSetupDone) {
                            navController.navigate("pin_lock") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            navController.navigate("vhv_registration") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    },
                    onNavigateBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate("pin_lock")
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate("user_profile")
                    }
                )
            }
            
            composable("vhv_registration") {
                VhvRegistrationScreen(
                    authViewModel = authViewModel,
                    onRegistrationSuccess = {
                        navController.navigate("pin_lock") {
                            popUpTo("vhv_registration") { inclusive = true }
                        }
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("pin_lock") {
                PinLockScreen(
                    onUnlock = {
                        navController.navigate(BottomNavItem.Dashboard.route) {
                            popUpTo("pin_lock") { inclusive = true }
                        }
                    }
                )
            }
            composable(BottomNavItem.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onNavigateToHouseholds = {
                        navController.navigate(BottomNavItem.Households.route)
                    },
                    onNavigateToNewHousehold = {
                        navController.navigate("household_form/-1")
                    },
                    onNavigateToMap = {
                        navController.navigate(BottomNavItem.Map.route)
                    },
                    onNavigateToPlanOfWork = {
                        navController.navigate("plan_of_work")
                    },
                    onNavigateToInfo = {
                        navController.navigate(BottomNavItem.Info.route)
                    },
                    onNavigateToHouseDetail = { householdId ->
                        navController.navigate("house_detail/$householdId")
                    },
                    onNavigateToQrScan = {
                        navController.navigate("qr_scanner")
                    },
                    onNavigateToScreening = { personId ->
                        navController.navigate("health_screening/$personId")
                    },
                    onNavigateToMonthlyReport = {
                        navController.navigate("vhv_monthly_report")
                    },
                    onNavigateToVideoTutorials = { videoId ->
                        if (!videoId.isNullOrBlank()) {
                            navController.navigate("video_tutorials?videoId=$videoId")
                        } else {
                            navController.navigate("video_tutorials")
                        }
                    }
                )
            }
            composable(BottomNavItem.Households.route) {
                HouseholdListScreen(
                    viewModel = viewModel,
                    onHouseClick = { householdId -> navController.navigate("house_detail/$householdId") },
                    onAddHouseClick = { navController.navigate("household_form/-1") },
                    onScanQrClick = { navController.navigate("qr_scanner") }
                )
            }

            composable(BottomNavItem.Persons.route) {
                PersonListScreen(
                    viewModel = viewModel,
                    onPersonClick = { personId, householdId -> 
                        navController.navigate("person_form/$personId?householdId=$householdId") 
                    },
                    onScreeningClick = { personId ->
                        navController.navigate("health_screening/$personId")
                    }
                )
            }
            composable(
                route = "map?targetHouseholdId={targetHouseholdId}",
                arguments = listOf(
                    navArgument("targetHouseholdId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val targetHouseholdId = backStackEntry.arguments?.getLong("targetHouseholdId") ?: -1L
                MapScreen(
                    viewModel = viewModel,
                    targetHouseholdId = targetHouseholdId,
                    onHouseClick = { householdId -> navController.navigate("house_detail/$householdId") },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(BottomNavItem.Info.route) {
                DeveloperInfoScreen(
                    authViewModel = authViewModel,
                    onNavigateToCloudSync = { navController.navigate("cloud_sync") },
                    onNavigateToHealthKnowledge = { navController.navigate("health_knowledge") },
                    onNavigateToPlanOfWork = { navController.navigate("plan_of_work") },
                    onNavigateToDiagnostic = { navController.navigate("diagnostic") },
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToUserProfile = { navController.navigate("user_profile") },
                    onNavigateToVhvRegistration = { navController.navigate("vhv_registration") },
                    onNavigateToMonthlyReport = { navController.navigate("vhv_monthly_report") },
                    onNavigateToVideoTutorials = { navController.navigate("video_tutorials") }
                )
            }
            composable("user_profile") {
                UserProfileScreen(
                    authViewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToVhvRegistration = { navController.navigate("vhv_registration") }
                )
            }
            composable("diagnostic") {
                DiagnosticScreen(
                    viewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            return DiagnosticViewModel(repository, firestore) as T
                        }
                    }),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("cloud_sync") {
                CloudSyncScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("health_knowledge") {
                HealthKnowledgeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToVideoTutorials = { navController.navigate("video_tutorials") }
                )
            }
            composable("plan_of_work") {
                PlanOfWorkScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToHouseDetail = { householdId ->
                        navController.navigate("house_detail/$householdId")
                    },
                    onNavigateToMonthlyReport = {
                        navController.navigate("vhv_monthly_report")
                    }
                )
            }
            composable("vhv_monthly_report") {
                VhvMonthlyReportScreen(
                    viewModel = viewModel,
                    authViewModel = authViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToHouseDetail = { householdId ->
                        navController.navigate("house_detail/$householdId")
                    },
                    onNavigateToScreening = { personId ->
                        navController.navigate("health_screening/$personId")
                    }
                )
            }
            composable("qr_scanner") {
                QrScannerScreen(
                    viewModel = viewModel,
                    onPersonFound = { personId ->
                        val person = viewModel.allPersons.value.find { it.id == personId }
                        if (person != null) {
                            navController.navigate("house_detail/${person.householdId}") {
                                popUpTo(BottomNavItem.Dashboard.route)
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            
            composable(
                route = "house_detail/{householdId}",
                arguments = listOf(navArgument("householdId") { type = NavType.LongType })
            ) { backStackEntry ->
                val householdId = backStackEntry.arguments?.getLong("householdId") ?: -1L
                HouseDetailScreen(
                    viewModel = viewModel,
                    householdId = householdId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { houseId -> navController.navigate("map?targetHouseholdId=$houseId") },
                    onAddMemberClick = { navController.navigate("person_form/-1?householdId=$householdId") },
                    onEditMemberClick = { personId -> navController.navigate("person_form/$personId?householdId=$householdId") },
                    onHistoryClick = { personId -> navController.navigate("person_history/$personId") },
                    onScreeningClick = { personId -> navController.navigate("health_screening/$personId") }
                )
            }

            composable(
                route = "household_form/{householdId}",
                arguments = listOf(navArgument("householdId") { type = NavType.LongType })
            ) { backStackEntry ->
                val householdId = backStackEntry.arguments?.getLong("householdId") ?: -1L
                HouseholdFormScreen(
                    viewModel = viewModel,
                    householdId = householdId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { newId ->
                        navController.popBackStack()
                        navController.navigate("house_detail/$newId")
                    }
                )
            }

            composable(
                route = "person_form/{personId}?householdId={householdId}",
                arguments = listOf(
                    navArgument("personId") { type = NavType.LongType },
                    navArgument("householdId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                val householdId = backStackEntry.arguments?.getLong("householdId") ?: -1L
                PersonFormScreen(
                    viewModel = viewModel,
                    personId = personId,
                    householdId = householdId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "person_history/{personId}",
                arguments = listOf(navArgument("personId") { type = NavType.LongType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                PersonHistoryScreen(
                    viewModel = viewModel,
                    personId = personId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "health_screening/{personId}",
                arguments = listOf(navArgument("personId") { type = NavType.LongType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getLong("personId") ?: -1L
                HealthScreeningScreen(
                    viewModel = viewModel,
                    personId = personId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "video_tutorials?videoId={videoId}",
                arguments = listOf(
                    navArgument("videoId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId")
                VideoTutorialScreen(
                    initialVideoId = videoId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRoute = { route ->
                        navController.navigate(route)
                    }
                )
            }
        }
    }
}
