package com.tindi.app

import android.app.Application
import android.content.Context
import com.tindi.app.data.AuthRepository
import com.tindi.app.data.CategoryRepository
import com.tindi.app.data.LocalAuthRepository
import com.tindi.app.data.LocalCategoryRepository
import com.tindi.app.data.LocalRecipeRepository
import com.tindi.app.data.LocalStore
import com.tindi.app.data.MockSourceFetcher
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.RecipeRepository
import com.tindi.app.data.SourceFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The only place that knows which implementations are in use. Swap them here for a real backend. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = LocalStore(File(context.filesDir, "tindi_data.json"), scope)

    val auth: AuthRepository = LocalAuthRepository(store)
    val recipes: RecipeRepository = LocalRecipeRepository(store)
    val categories: CategoryRepository = LocalCategoryRepository(store)
    val sourceFetcher: SourceFetcher = MockSourceFetcher()

    val themePrefs = ThemePrefs(context)
    val draft = DraftHolder()
}

/** Hands the picked source text from Add recipe to Review. */
class DraftHolder {
    private val _draft = MutableStateFlow<RecipeDraft?>(null)
    val draft: StateFlow<RecipeDraft?> = _draft
    fun set(d: RecipeDraft?) { _draft.value = d }
}

/** Dark mode choice. null = follow the system until the user toggles. Stays on the device. */
class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("tindi_prefs", Context.MODE_PRIVATE)
    private val _dark = MutableStateFlow<Boolean?>(
        if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null,
    )
    val dark: StateFlow<Boolean?> = _dark

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
        _dark.value = value
    }

    private companion object { const val KEY = "dark" }
}

class TindiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
