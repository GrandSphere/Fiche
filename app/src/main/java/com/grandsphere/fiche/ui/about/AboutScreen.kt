package com.grandsphere.fiche.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandsphere.fiche.BuildConfig
import com.grandsphere.fiche.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Fiche",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("Fiche", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Grand Sphere Studios",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Version: ${BuildConfig.VERSION_NAME}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text("License:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                AboutLink(
                    title = "GNU General Public License 3",
                    subtitle = "(GPLv3)",
                    url = "https://github.com/GrandSphere/Fiche/blob/master/LICENSE"
                )
                Spacer(Modifier.height(16.dp))
                Text("Github:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                AboutLink(
                    title = "Github Repository",
                    url = "https://github.com/GrandSphere/Fiche"
                )
                AboutLink(
                    title = "Latest Release",
                    url = "https://github.com/GrandSphere/Fiche/releases/latest"
                )
                Spacer(Modifier.height(16.dp))
                Text("Catalog data:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                AboutLink(title = "TvMaze", url = "https://www.tvmaze.com")
                AboutLink(title = "TMDB", url = "https://www.themoviedb.org")
                AboutLink(title = "Jikan", url = "https://jikan.moe")
                AboutLink(title = "MyAnimeList", url = "https://myanimelist.net")
                AboutLink(title = "Open Library", url = "https://openlibrary.org")
                AboutLink(title = "RAWG", url = "https://rawg.io")
                AboutLink(title = "TasteDive", url = "https://tastedive.com")
                Spacer(Modifier.height(16.dp))
                Text("Donate:", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                AboutLink(title = "Ko-fi", url = "https://ko-fi.com/grandspherestudios")
                AboutLink(title = "Liberapay", url = "https://liberapay.com/GrandSphere")
            }
        }
    }
}

@Composable
private fun AboutLink(title: String, url: String, subtitle: String? = null) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.primary)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open link",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}
