package app.feltbook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.feltbook.core.transfer.file.HandoverImportHandler
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная Activity: edge-to-edge, predictive back (манифест), интерфейс всегда на русском.
 * `singleTask`: файл `.feltbook`, открытый из Telegram или «Файлов», приходит сюда же ([onNewIntent]), а не во второй
 * экземпляр приложения в чужой задаче.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RussianLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingFile(intent)
        setContent { FeltbookRoot(viewModel = appViewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFile(intent)
    }

    private fun handleIncomingFile(intent: Intent?) {
        HandoverImportHandler.uriFrom(intent)?.let(appViewModel::openIncomingFile)
    }
}
