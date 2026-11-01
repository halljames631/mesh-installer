package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.FirmwareEcosystem
import com.example.model.FirmwareRelease
import com.example.ui.MeshInstallerUiState
import com.example.ui.components.HardwareChipBadge
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.MeshCoreCyan
import com.example.ui.theme.MeshtasticGreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FirmwareReleasesScreen(
    uiState: MeshInstallerUiState,
    onSelectEcosystem: (FirmwareEcosystem) -> Unit,
    onSelectRelease: (FirmwareRelease) -> Unit,
    onRefreshReleases: () -> Unit,
    onImportCustomFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ecoColor = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        MeshtasticGreen
    } else {
        MeshCoreCyan
    }

    val releases = if (uiState.selectedEcosystem == FirmwareEcosystem.MESHTASTIC) {
        uiState.meshtasticReleases
    } else {
        uiState.meshCoreReleases
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GITHUB FIRMWARE REPOSITORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = ecoColor
                )
                Text(
                    text = "Official ${uiState.selectedEcosystem.repoOwner}/${uiState.selectedEcosystem.repoName} releases",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportCustomFile,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("releases_import_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Import Custom File",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Local File", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onRefreshReleases,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("refresh_releases_button")
                ) {
                    if (uiState.isFetchingReleases) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ecoColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sync GitHub Releases",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FirmwareEcosystem.entries.forEach { eco ->
                val active = uiState.selectedEcosystem == eco
                val accent = if (eco == FirmwareEcosystem.MESHTASTIC) MeshtasticGreen else MeshCoreCyan
                FilterChip(
                    selected = active,
                    onClick = { onSelectEcosystem(eco) },
                    label = { Text("${eco.displayName} Releases") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    ),
                    modifier = Modifier.testTag("releases_eco_filter_${eco.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("firmware_releases_list"),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(releases, key = { it.id }) { release ->
                val isSelected = uiState.selectedRelease?.id == release.id
                ReleaseItemCard(
                    release = release,
                    isSelected = isSelected,
                    ecoColor = ecoColor,
                    onSelect = { onSelectRelease(release) }
                )
            }
        }
    }
}

@Composable
private fun ReleaseItemCard(
    release: FirmwareRelease,
    isSelected: Boolean,
    ecoColor: Color,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SlateCard)
            .border(
                width = if (isSelected) 1.8.dp else 1.dp,
                color = if (isSelected) ecoColor else SlateBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() }
            .padding(14.dp)
            .testTag("release_card_${release.tagName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected Release",
                        tint = ecoColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HardwareChipBadge(
                text = if (release.isPrerelease) "ALPHA / RC" else "STABLE",
                color = if (release.isPrerelease) AmberSignal else ecoColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tag: ${release.tagName} • Published: ${release.publishedDate}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianBg)
                .padding(10.dp)
        ) {
            Text(
                text = release.changelog,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (release.assets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "BUNDLED ARCHIVES & BINARIES (${release.assets.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            release.assets.take(4).forEach { asset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = asset.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${"%.2f".format(asset.sizeBytes / (1024f * 1024f))} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = ecoColor
                    )
                }
            }
        }

        if (!isSelected) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSelect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ecoColor,
                    contentColor = Color(0xFF002211)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select ${release.tagName} for Flashing", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
