package com.lasse.speedometer.ui.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lasse.speedometer.R

/**
 * One dependency the app ships, with the licence it is used under.
 *
 * Apache 2.0 requires the notice to travel with the binary, and OpenStreetMap's
 * ODbL requires visible attribution, so this screen is a compliance obligation
 * rather than a courtesy.
 */
private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val license: String,
    @param:RawRes val licenseText: Int?,
    val url: String,
)

private val Entries = listOf(
    LicenseEntry(
        name = "Android Jetpack — Compose, Room, DataStore, Navigation, Lifecycle, Core",
        copyright = "Copyright © The Android Open Source Project",
        license = "Apache License 2.0",
        licenseText = R.raw.license_apache_2_0,
        url = "https://developer.android.com/jetpack",
    ),
    LicenseEntry(
        name = "Health Connect client",
        copyright = "Copyright © The Android Open Source Project",
        license = "Apache License 2.0",
        licenseText = R.raw.license_apache_2_0,
        url = "https://developer.android.com/health-and-fitness/guides/health-connect",
    ),
    LicenseEntry(
        name = "Kotlin standard library and coroutines",
        copyright = "Copyright © JetBrains s.r.o. and Kotlin contributors",
        license = "Apache License 2.0",
        licenseText = R.raw.license_apache_2_0,
        url = "https://github.com/JetBrains/kotlin",
    ),
    LicenseEntry(
        name = "MapLibre Native for Android",
        copyright = "Copyright © MapLibre contributors; portions © Mapbox, Inc.",
        license = "BSD 2-Clause License",
        licenseText = R.raw.license_bsd_2_clause,
        url = "https://github.com/maplibre/maplibre-native",
    ),
    LicenseEntry(
        name = "OkHttp",
        copyright = "Copyright © Square, Inc.",
        license = "Apache License 2.0",
        licenseText = R.raw.license_apache_2_0,
        url = "https://square.github.io/okhttp/",
    ),
    LicenseEntry(
        name = "Map data — OpenStreetMap",
        copyright = "© OpenStreetMap contributors",
        license = "Open Database License (ODbL) v1.0",
        licenseText = R.raw.license_odbl_summary,
        url = "https://www.openstreetmap.org/copyright",
    ),
    LicenseEntry(
        name = "Map tiles and styles — OpenFreeMap",
        copyright = "Tiles served by OpenFreeMap, built from OpenStreetMap data",
        license = "See OpenStreetMap attribution above",
        licenseText = null,
        url = "https://openfreemap.org/",
    ),
    LicenseEntry(
        name = "Noto Sans (map label glyphs)",
        copyright = "Copyright © The Noto Project Authors",
        license = "SIL Open Font License 1.1",
        licenseText = null,
        url = "https://fonts.google.com/noto",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var showing by remember { mutableStateOf<LicenseEntry?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_licenses_title)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("intro") {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Entries.forEach { entry ->
                item(entry.name) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        onClick = { if (entry.licenseText != null) showing = entry },
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = entry.copyright,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = entry.license,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = entry.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item("app-license") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.licenses_privacy_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.licenses_privacy_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item("footer") { androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)) }
        }
    }

    showing?.let { entry ->
        LicenseTextDialog(entry = entry, onDismiss = { showing = null })
    }
}

@Composable
private fun LicenseTextDialog(entry: LicenseEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(entry.licenseText) {
        entry.licenseText?.let { resource ->
            runCatching {
                context.resources.openRawResource(resource)
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault("")
        }.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.license) },
        text = {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
