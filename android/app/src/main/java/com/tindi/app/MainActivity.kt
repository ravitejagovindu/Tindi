package com.tindi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tindi.app.nav.TindiRoot

class MainActivity : ComponentActivity() {
    private val container get() = (application as TindiApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A rotation or process restore re-delivers the same intent. Only a fresh launch counts.
        if (savedInstanceState == null) takeShare(intent)
        setContent { TindiRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeShare(intent)
    }

    private fun takeShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            container.shared.offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}
