package com.tindi.app.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tindi.app.AppContainer
import com.tindi.app.ui.components.BottomNav
import com.tindi.app.ui.components.NavItem
import com.tindi.app.ui.screens.AuthScreen
import com.tindi.app.ui.screens.AddRecipeScreen
import com.tindi.app.ui.screens.CategoriesScreen
import com.tindi.app.ui.screens.CookingScreen
import com.tindi.app.ui.screens.ReviewScreen
import com.tindi.app.ui.screens.LibraryScreen
import com.tindi.app.ui.screens.ProfileScreen
import com.tindi.app.ui.screens.RecipeScreen
import com.tindi.app.ui.theme.Tindi
import com.tindi.app.ui.theme.ThemeControl
import com.tindi.app.ui.theme.LocalThemeControl
import com.tindi.app.ui.theme.TindiTheme

object Routes {
    const val LIBRARY = "library"
    const val CATEGORIES = "categories"
    const val PROFILE = "profile"
    const val ADD = "add"
    const val REVIEW = "review"
    const val RECIPE = "recipe/{id}"
    const val COOK = "cook/{id}"
    const val EDIT = "edit/{id}"
    fun recipe(id: String) = "recipe/$id"
    fun cook(id: String) = "cook/$id"
    fun edit(id: String) = "edit/$id"
}

@Composable
fun TindiRoot(container: AppContainer) {
    val override by container.themePrefs.dark.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = override ?: systemDark
    val control = remember(isDark) { ThemeControl(isDark) { container.themePrefs.setDark(!isDark) } }

    TindiTheme(darkOverride = override) {
        CompositionLocalProvider(LocalThemeControl provides control) {
            val view = LocalView.current
            SideEffect {
                val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                val ctl = WindowCompat.getInsetsController(w, view)
                ctl.isAppearanceLightStatusBars = !isDark
                ctl.isAppearanceLightNavigationBars = !isDark
            }
            val ready by container.auth.ready.collectAsStateWithLifecycle(false)
            val session by container.auth.session.collectAsStateWithLifecycle(null)
            Box(Modifier.fillMaxSize().background(Tindi.colors.bg)) {
                when {
                    !ready -> Unit
                    session == null -> Box(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
                        AuthScreen(container)
                    }
                    else -> MainNav(container)
                }
            }
        }
    }
}

@Composable
private fun MainNav(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showNav = route == Routes.LIBRARY || route == Routes.CATEGORIES || route == Routes.PROFILE
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Box(
            Modifier
                .weight(1f)
                .then(if (showNav && !imeOpen) Modifier else Modifier.navigationBarsPadding()),
        ) {
            AppNavHost(nav, container)
        }
        if (showNav && !imeOpen) {
            BottomNav(currentRoute = route) { item -> nav.switchTab(item) }
        }
    }
}

private fun NavHostController.switchTab(item: NavItem) {
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, container: AppContainer) {
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(container, onOpenRecipe = { nav.navigate(Routes.recipe(it)) }, onAdd = { nav.navigate(Routes.ADD) })
        }
        composable(Routes.RECIPE) { e ->
            val id = e.arguments?.getString("id").orEmpty()
            RecipeScreen(
                container, id,
                onBack = { nav.popBackStack() },
                onStartCooking = { nav.navigate(Routes.cook(id)) },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(Routes.COOK) { e ->
            val id = e.arguments?.getString("id").orEmpty()
            CookingScreen(
                container, id,
                onClose = { nav.popBackStack() },
                onFinish = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(container) }
        composable(Routes.ADD) {
            AddRecipeScreen(container, onBack = { nav.popBackStack() }, onUseText = { nav.navigate(Routes.REVIEW) })
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                container,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
        composable(Routes.EDIT) { e ->
            val id = e.arguments?.getString("id").orEmpty()
            ReviewScreen(container, editId = id, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable(Routes.PROFILE) { ProfileScreen(container) }
    }
}

