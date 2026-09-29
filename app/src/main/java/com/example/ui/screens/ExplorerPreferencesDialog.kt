package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Bolt
import com.example.ui.components.isAllFilesAccessGranted
import com.example.ui.components.openAllFilesAccessSettings
import com.example.ui.components.openAppSettings
import com.example.data.model.SortOption
import com.example.data.model.ViewMode
import com.example.ui.components.formatDate
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UnifiedViewModel

@Composable
fun ExplorerPreferencesDialog(
    uiState: UiState,
    viewModel: UnifiedViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showSortDropdown by remember { mutableStateOf(false) }
    val isAllFilesGranted = isAllFilesAccessGranted()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("explorer_preferences_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Explorer Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Persistent in Room Database",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: View & Layout
                Text(
                    text = "VIEW & LAYOUT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // View Mode selector
                Text(
                    text = "Default View Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ViewModeOptionChip(
                        label = "Detailed",
                        icon = Icons.Default.ViewList,
                        selected = uiState.viewMode == ViewMode.DETAILED_LIST,
                        onClick = { viewModel.setViewMode(ViewMode.DETAILED_LIST) },
                        modifier = Modifier.weight(1f)
                    )
                    ViewModeOptionChip(
                        label = "Compact",
                        icon = Icons.Default.ViewAgenda,
                        selected = uiState.viewMode == ViewMode.COMPACT_LIST,
                        onClick = { viewModel.setViewMode(ViewMode.COMPACT_LIST) },
                        modifier = Modifier.weight(1f)
                    )
                    ViewModeOptionChip(
                        label = "Grid",
                        icon = Icons.Default.GridView,
                        selected = uiState.viewMode == ViewMode.GRID,
                        onClick = { viewModel.setViewMode(ViewMode.GRID) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Sort By Selector
                Column {
                    Text(
                        text = "Default Sort Order",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSortDropdown = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = uiState.sortOption.title,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    text = "Change",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(option.title)
                                            if (option == uiState.sortOption) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Show Hidden Files Switch
                PreferenceToggleRow(
                    title = "Show Hidden Files",
                    subtitle = "Display files and folders starting with a dot",
                    icon = Icons.Default.Visibility,
                    checked = uiState.showHidden,
                    onCheckedChange = { viewModel.toggleShowHidden() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Section: Navigation & Storage Persistence
                Text(
                    text = "NAVIGATION & FOLDER RESTORATION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                PreferenceToggleRow(
                    title = "Remember Last Folder",
                    subtitle = "Reopen the last active directory when launching app",
                    icon = Icons.Default.History,
                    checked = uiState.explorerPreferences.rememberLastDirectory,
                    onCheckedChange = { viewModel.toggleRememberLastDirectory() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Section: Room Database Indexing
                Text(
                    text = "LOCAL ROOM DATABASE INDEXING",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                PreferenceToggleRow(
                    title = "Fast SQLite Indexed Search",
                    subtitle = "Query storage using indexed Room database tables for sub-millisecond search",
                    icon = Icons.Default.Search,
                    checked = uiState.isFastSearchRoomPowered,
                    onCheckedChange = { viewModel.toggleFastSearch() }
                )

                // Room Index Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Room SQLite Index",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = uiState.indexStatusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (uiState.isIndexing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Indexed items: ${uiState.indexedCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.lastIndexedTimestamp > 0) {
                                Text(
                                    text = "Updated: ${formatDate(uiState.lastIndexedTimestamp)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.reindexStorage(force = true) },
                            enabled = !uiState.isIndexing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reindex_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isIndexing) "Indexing..." else "Re-index Local Storage")
                        }
                    }
                }

                // Device Storage & File Management Permission Section
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isAllFilesGranted) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAllFilesGranted) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAllFilesGranted) Icons.Default.Security else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isAllFilesGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "All Files Access",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAllFilesGranted) "Granted - Full management active" else "Required to view and manage all files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAllFilesGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Text(
                            text = if (isAllFilesGranted) {
                                "emrexplore has full access to create, edit, rename, delete, and compress files across your internal and external storage."
                            } else {
                                "Android requires special permission for file managers to manage all files across storage. Tap below to enable."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { openAllFilesAccessSettings(context) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isAllFilesGranted) "Manage Access" else "Grant Permission")
                            }

                            OutlinedButton(
                                onClick = { openAppSettings(context) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("App Info")
                            }
                        }
                    }
                }

                // Performance & I/O Priority Diagnostics Section
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Performance & I/O Priority",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Interactive priority active • Indexer yields to user",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Folder Open Latency", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${uiState.performanceMetrics.lastFolderOpenLatencyMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Page Slice Fetch Latency", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${uiState.performanceMetrics.lastPagedLoadLatencyMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Disk Reads Avoided (Cache)", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${uiState.performanceMetrics.diskReadsAvoided} syscalls",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Background Indexer Yields", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${uiState.performanceMetrics.totalIoYields} times",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset Preferences button
                OutlinedButton(
                    onClick = { viewModel.resetPreferences() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_preferences_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Preferences to Defaults")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ViewModeOptionChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
