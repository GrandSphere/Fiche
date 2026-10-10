package com.grandsphere.fiche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.grandsphere.fiche.domain.model.MediaType
import com.grandsphere.fiche.ui.about.AboutScreen
import com.grandsphere.fiche.ui.chrome.MediaTypeTabs
import com.grandsphere.fiche.ui.components.FicheSnackbarHost
import com.grandsphere.fiche.ui.components.ShareImportDialog
import com.grandsphere.fiche.ui.detail.DetailScreen
import com.grandsphere.fiche.ui.discover.DiscoverScreen
import com.grandsphere.fiche.ui.home.HomeScreen
import com.grandsphere.fiche.ui.lists.BanListScreen
import com.grandsphere.fiche.ui.lists.DislikeListScreen
import com.grandsphere.fiche.ui.lists.InterestNotesScreen
import com.grandsphere.fiche.ui.lists.WatchlistScreen
import com.grandsphere.fiche.ui.overview.OverviewScreen
import com.grandsphere.fiche.ui.search.AddSearchScreen
import com.grandsphere.fiche.ui.settings.SettingsScreen
import com.grandsphere.fiche.ui.suggestions.SuggestionsScreen
import kotlinx.coroutines.launch

private data class DrawerDest(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FicheRoot(session: SessionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val mediaType by session.mediaType.collectAsStateWithLifecycle()
    val enabledTypes by session.enabledMediaTypes.collectAsStateWithLifecycle()
    val pendingAdd by session.addPrefill.pending.collectAsStateWithLifecycle()
    val pendingShareImport by session.shareImport.pending.collectAsStateWithLifecycle()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    fun openDrawer() = scope.launch { drawerState.open() }
    fun closeDrawer() = scope.launch { drawerState.close() }

    LaunchedEffect(pendingAdd) {
        if (pendingAdd != null) {
            nav.navigate("add") { launchSingleTop = true }
        }
    }

    LaunchedEffect(Unit) {
        session.importMessage.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    pendingShareImport?.let { titles ->
        ShareImportDialog(
            titles = titles,
            onConfirm = session::confirmShareImport,
            onDismiss = session::dismissShareImport
        )
    }

    // Group 1: Your library
    val libraryItems = listOf(
        DrawerDest("library", mediaType.label, Icons.Default.Home),
        DrawerDest("overview", "Overview", Icons.Default.Assessment)
    )
    // Group 2: Discovery
    val discoveryItems = listOf(
        DrawerDest("find", "Find New", Icons.Default.Explore),
        DrawerDest("suggestions", "Suggestions", Icons.Default.Lightbulb),
        DrawerDest("watchlist", "Interests", Icons.AutoMirrored.Filled.PlaylistAdd),
        DrawerDest("dislikes", "Dislike list", Icons.Default.Block)
    )
    // Group 3: App
    val appItems = listOf(
        DrawerDest("settings", "Settings", Icons.Default.Settings),
        DrawerDest("about", "About", Icons.Default.Info)
    )

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                        Text("Fiche", style = MaterialTheme.typography.headlineSmall)
                    }
                    // Media type tab strip in the drawer header
                    MediaTypeTabs(
                        current = mediaType,
                        enabledTypes = enabledTypes,
                        onSelect = session::setMediaType,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

                    @Composable
                    fun DrawerDest.item() {
                        NavigationDrawerItem(
                            label = { Text(label) },
                            selected = current?.startsWith(route) == true,
                            icon = { Icon(icon, null) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 1.dp)
                                .heightIn(max = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = NavigationDrawerItemDefaults.colors(),
                            onClick = {
                                nav.navigate(route) {
                                    popUpTo("library") { inclusive = route == "library" }
                                    launchSingleTop = true
                                }
                                closeDrawer()
                            }
                        )
                    }

                    libraryItems.forEach { it.item() }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    discoveryItems.forEach { it.item() }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    appItems.forEach { it.item() }
                }
            }
        ) {
            NavHost(
                navController = nav,
                startDestination = "library",
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                composable("library") {
                    HomeScreen(
                        onOpenDrawer = ::openDrawer,
                        onAdd = { nav.navigate("add") },
                        onOpenTitle = { nav.navigate("detail/$it") },
                        onSearchCatalog = { query ->
                            session.addPrefill.offer(
                                com.grandsphere.fiche.data.repository.AddSearchPrefill(
                                    query = query,
                                    type = mediaType,
                                    seed = null,
                                    fromShare = false
                                )
                            )
                            nav.navigate("add") { launchSingleTop = true }
                        },
                        onGetSuggestions = { ids ->
                            nav.navigate("suggestions") {
                                launchSingleTop = true
                            }
                            // Prefill handled by SuggestionsViewModel via SavedState / session later;
                            // store ids on session for Suggestions to pick up.
                            session.offerSuggestionSeeds(ids)
                        }
                    )
                }
                composable("overview") { OverviewScreen(::openDrawer) }
                composable("watchlist") {
                    WatchlistScreen(
                        onOpenDrawer = ::openDrawer,
                        onOpenNotes = { nav.navigate("interestNotes") }
                    )
                }
                composable("interestNotes") {
                    InterestNotesScreen(onBack = { nav.popBackStack() })
                }
                composable("find") { DiscoverScreen(onOpenDrawer = ::openDrawer) }
                composable("suggestions") {
                    SuggestionsScreen(
                        onOpenDrawer = ::openDrawer,
                        suggestionSeeds = session.suggestionSeeds
                    )
                }
                composable("dislikes") {
                    DislikeListScreen(
                        onOpenDrawer = ::openDrawer,
                        onOpenBanList = { nav.navigate("banlist") }
                    )
                }
                composable("banlist") { BanListScreen(onBack = { nav.popBackStack() }) }
                composable("settings") { SettingsScreen(::openDrawer) }
                composable("about") { AboutScreen(::openDrawer) }
                composable("add") { AddSearchScreen(onBack = { nav.popBackStack() }) }
                composable(
                    "detail/{titleId}",
                    arguments = listOf(navArgument("titleId") { type = NavType.LongType })
                ) {
                    DetailScreen(
                        onBack = { nav.popBackStack() },
                        onSearchTitle = { query, type ->
                            session.addPrefill.offer(
                                com.grandsphere.fiche.data.repository.AddSearchPrefill(
                                    query = query,
                                    type = type,
                                    seed = null,
                                    fromShare = false
                                )
                            )
                            nav.navigate("add") { launchSingleTop = true }
                        },
                        onOpenRelated = { query, type, remoteId ->
                            session.addPrefill.offer(
                                com.grandsphere.fiche.data.repository.AddSearchPrefill(
                                    query = query,
                                    type = type,
                                    seed = com.grandsphere.fiche.data.remote.RemoteTitle(
                                        mediaType = type.name,
                                        remoteId = remoteId,
                                        title = query
                                    ),
                                    fromShare = false
                                )
                            )
                            nav.navigate("add") { launchSingleTop = true }
                        },
                        onOpenSequel = { sequelId ->
                            nav.navigate("detail/$sequelId") {
                                popUpTo("library") { inclusive = false }
                            }
                        }
                    )
                }
            }
        }
        FicheSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
