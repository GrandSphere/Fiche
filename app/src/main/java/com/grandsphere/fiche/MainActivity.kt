package com.grandsphere.fiche

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.grandsphere.fiche.data.backup.BackupRepository
import com.grandsphere.fiche.data.prefs.AppearancePrefs
import com.grandsphere.fiche.data.prefs.UserPreferencesRepository
import com.grandsphere.fiche.data.repository.CatalogShareHandler
import com.grandsphere.fiche.data.repository.ShareImportStore
import com.grandsphere.fiche.data.repository.ShareOutcome
import com.grandsphere.fiche.ui.FicheRoot
import com.grandsphere.fiche.ui.SessionViewModel
import com.grandsphere.fiche.ui.theme.FicheTheme
import com.grandsphere.fiche.util.CatalogLinkParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: UserPreferencesRepository

    @Inject
    lateinit var backup: BackupRepository

    @Inject
    lateinit var catalogShare: CatalogShareHandler

    @Inject
    lateinit var shareImport: ShareImportStore

    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncoming(intent)
        }
        setContent {
            val appearance by preferences.appearance.collectAsStateWithLifecycle(AppearancePrefs())
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale * appearance.fontScale)
            ) {
                FicheTheme(
                    themeMode = appearance.themeMode,
                    backgroundArgb = appearance.backgroundArgb,
                    groupArgb = appearance.groupArgb,
                    actionArgb = appearance.actionArgb,
                    alternateArgb = appearance.alternateArgb
                ) {
                    FicheRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val stream = incomingStream(intent)
        val viewUri = if (intent.action == Intent.ACTION_VIEW) intent.data else null
        val catalogUri = viewUri?.takeIf { it.scheme == "http" || it.scheme == "https" }
        val catalogText = text ?: catalogUri?.toString()
        val isCatalog = catalogText != null && CatalogLinkParser.parse(catalogText, catalogUri) != null
        when {
            isCatalog -> {
                catalogShare.beginLoading(catalogText, catalogUri)
                lifecycleScope.launch {
                    when (val outcome = catalogShare.handle(catalogText, catalogUri)) {
                        is ShareOutcome.AlreadyTracked -> toast("${outcome.title} is already in your library")
                        is ShareOutcome.OpenSearch -> { }
                        is ShareOutcome.Message -> toast(outcome.text)
                    }
                }
            }
            else -> lifecycleScope.launch {
                val shareTitles = when {
                    !text.isNullOrBlank() -> backup.parseSharePayload(text)
                    stream != null -> backup.readSharePayload(stream)
                    viewUri != null && (viewUri.scheme == "content" || viewUri.scheme == "file") ->
                        backup.readSharePayload(viewUri)
                    else -> null
                }
                if (shareTitles != null) {
                    shareImport.offer(shareTitles)
                    return@launch
                }
                when {
                    stream != null -> {
                        val message = runCatching { backup.importFrom(stream) }
                            .getOrElse { it.message ?: "Import failed" }
                        toast(message)
                    }
                    viewUri != null && (viewUri.scheme == "content" || viewUri.scheme == "file") -> {
                        val message = runCatching { backup.importFrom(viewUri) }
                            .getOrElse { it.message ?: "Import failed" }
                        toast(message)
                    }
                }
            }
        }
    }

    private fun toast(message: String) {
        sessionViewModel.showMessage(message)
    }

    private fun incomingStream(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}
