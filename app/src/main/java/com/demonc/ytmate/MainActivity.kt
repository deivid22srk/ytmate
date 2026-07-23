package com.demonc.ytmate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.demonc.ytmate.data.MainViewModel
import com.demonc.ytmate.data.YouTubeRepository
import com.demonc.ytmate.ui.screens.MainScreen
import com.demonc.ytmate.ui.theme.YTMateTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.bindDownloadService(this)
        setContent {
            YTMateTheme {
                MainScreen(viewModel = viewModel)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        viewModel.unbindDownloadService(this)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val url = YouTubeRepository.extractYouTubeUrl(sharedText)
            if (!url.isNullOrBlank()) {
                viewModel.loadUrl(url)
            }
        }
    }
}
