package com.tindi.app

import android.app.Application
import android.content.Context
import com.tindi.app.data.ApiAuthRepository
import com.tindi.app.data.ApiCategoryRepository
import com.tindi.app.data.ApiRecipeRepository
import com.tindi.app.data.AuthRepository
import com.tindi.app.data.remote.ApiFactory
import com.tindi.app.data.remote.EncryptedSessionStore
import com.tindi.app.data.remote.SessionStore
import kotlinx.coroutines.launch
import com.tindi.app.data.CategoryRepository
import com.tindi.app.data.ApiSourceFetcher
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.RecipeRepository
import com.tindi.app.data.SourceFetcher
import com.tindi.app.data.ApiTextStructurer
import com.tindi.app.data.TextStructurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The only place that knows which implementations are in use. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionStore: SessionStore = EncryptedSessionStore(context.applicationContext, scope)
    private val api = ApiFactory.create(BuildConfig.BASE_URL, sessionStore)

    val auth: AuthRepository = ApiAuthRepository(api, sessionStore, scope)
    val recipes: RecipeRepository = ApiRecipeRepository(api, sessionStore, scope)
    val categories: CategoryRepository = ApiCategoryRepository(api, sessionStore, recipes, scope)
    val sourceFetcher: SourceFetcher = ApiSourceFetcher(api)
    val textStructurer: TextStructurer = ApiTextStructurer(api)

    val themePrefs = ThemePrefs(context)
    val draft = DraftHolder()
    val shared = SharedContent()

    init {
        // A half-made recipe must not outlive its session.
        scope.launch { sessionStore.session.collect { if (it == null) draft.set(null) } }
    }
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

/** What another app shared to Tindi (Share, then Tindi), waiting for Add recipe to pick it up. */
class SharedContent {
    private val _item = MutableStateFlow<SharedItem?>(null)
    val item: StateFlow<SharedItem?> = _item

    /** Takes the text of a share intent. A link goes to the fetch, other text to the paste box. */
    fun offer(text: String?) {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return
        val link = LINK.find(t)?.value?.trimEnd('.', ',', ')', ']')
        _item.value = if (link != null) SharedItem(link = link, text = null) else SharedItem(link = null, text = t)
    }

    fun clear() { _item.value = null }

    private companion object { val LINK = Regex("https?://[^ \t\r\n]+", RegexOption.IGNORE_CASE) }
}

data class SharedItem(val link: String?, val text: String?)
