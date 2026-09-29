package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.metadata.ExifDirectoryNode
import com.example.data.metadata.IptcDatasetItem
import com.example.data.metadata.IptcGroupNode
import com.example.data.metadata.MetadataExtractor
import com.example.data.metadata.MetadataReport
import com.example.data.metadata.MetadataSummary
import com.example.data.metadata.MetadataTagItem
import com.example.data.metadata.XmpPropertyItem
import com.example.data.metadata.XmpSchemaNode
import com.example.ui.components.formatFileSize

enum class MetadataTab(val label: String, val icon: ImageVector) {
    SUMMARY("Summary", Icons.Default.Info),
    EXIF("EXIF Tree", Icons.Default.CameraAlt),
    IPTC("IPTC-IIM", Icons.Default.Tune),
    XMP("XMP Schemas", Icons.Default.FolderOpen),
    RAW_XML("Raw XML", Icons.Default.Code)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataInspectorSheet(
    report: MetadataReport,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        MetadataInspectorContent(
            report = report,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MetadataInspectorContent(
    report: MetadataReport,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MetadataTab.SUMMARY) }
    var searchQuery by remember { mutableStateOf("") }

    // Map of expanded directory state for EXIF tree
    val exifExpandedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            report.exifDirectories.forEach { put(it.name, true) }
        }
    }

    // Map of expanded state for IPTC groups
    val iptcExpandedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            report.iptcReport.groups.forEach { put(it.categoryName, true) }
        }
    }

    // Map of expanded state for XMP schemas
    val xmpExpandedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            report.xmpReport.schemas.forEach { put(it.displayName, true) }
        }
    }

    // Filtered data based on search query
    val q = searchQuery.trim().lowercase()
    val isSearching = q.isNotEmpty()

    val filteredExifDirectories = remember(report, q) {
        if (q.isEmpty()) report.exifDirectories
        else {
            report.exifDirectories.mapNotNull { dir ->
                val matchedTags = dir.tags.filter { tag ->
                    tag.name.lowercase().contains(q) ||
                            tag.formattedValue.lowercase().contains(q) ||
                            tag.tagIdHex.lowercase().contains(q) ||
                            tag.dataType.lowercase().contains(q)
                }
                if (matchedTags.isNotEmpty()) dir.copy(tags = matchedTags) else null
            }
        }
    }

    val filteredIptcGroups = remember(report, q) {
        if (q.isEmpty()) report.iptcReport.groups
        else {
            report.iptcReport.groups.mapNotNull { grp ->
                val matched = grp.datasets.filter { ds ->
                    ds.name.lowercase().contains(q) ||
                            ds.value.lowercase().contains(q) ||
                            ds.tagCode.lowercase().contains(q)
                }
                if (matched.isNotEmpty()) grp.copy(datasets = matched) else null
            }
        }
    }

    val filteredXmpSchemas = remember(report, q) {
        if (q.isEmpty()) report.xmpReport.schemas
        else {
            report.xmpReport.schemas.mapNotNull { schema ->
                val matched = schema.properties.filter { prop ->
                    prop.qualifiedName.lowercase().contains(q) ||
                            prop.propertyName.lowercase().contains(q) ||
                            prop.value.lowercase().contains(q)
                }
                if (matched.isNotEmpty()) schema.copy(properties = matched) else null
            }
        }
    }

    val matchCount by remember(isSearching, filteredExifDirectories, filteredIptcGroups, filteredXmpSchemas) {
        derivedStateOf {
            if (!isSearching) 0
            else filteredExifDirectories.sumOf { it.tags.size } +
                    filteredIptcGroups.sumOf { it.datasets.size } +
                    filteredXmpSchemas.sumOf { it.properties.size }
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
    }

    fun shareFullReport() {
        val extractor = MetadataExtractor(context)
        val textReport = extractor.generateFormattedReport(report)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Metadata Report - ${report.fileName}")
            putExtra(Intent.EXTRA_TEXT, textReport)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Metadata Report"))
    }

    fun copyFullReport() {
        val extractor = MetadataExtractor(context)
        val textReport = extractor.generateFormattedReport(report)
        copyToClipboard("Full Metadata Report", textReport)
    }

    fun toggleExpandAll(expand: Boolean) {
        report.exifDirectories.forEach { exifExpandedState[it.name] = expand }
        report.iptcReport.groups.forEach { iptcExpandedState[it.categoryName] = expand }
        report.xmpReport.schemas.forEach { xmpExpandedState[it.displayName] = expand }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "Metadata Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${report.fileName} · ${formatFileSize(report.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Expand / Collapse all
                    IconButton(onClick = { toggleExpandAll(true) }) {
                        Icon(Icons.Default.UnfoldMore, contentDescription = "Expand All")
                    }

                    // Copy full report
                    IconButton(onClick = { copyFullReport() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report")
                    }

                    // Share report
                    IconButton(onClick = { shareFullReport() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report")
                    }
                }

                // Standard Indicator Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetadataBadge(
                        text = "EXIF (${report.exifDirectories.sumOf { it.tags.size }})",
                        active = report.hasExif,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MetadataBadge(
                        text = "IPTC-IIM (${report.iptcReport.allDatasets.size})",
                        active = report.hasIptc,
                        color = Color(0xFF10B981) // emerald
                    )
                    MetadataBadge(
                        text = "XMP (${report.xmpReport.allProperties.size})",
                        active = report.hasXmp,
                        color = Color(0xFF6366F1) // indigo
                    )
                    if (report.hasGps) {
                        MetadataBadge(
                            text = "GPS",
                            active = true,
                            color = Color(0xFFF59E0B) // amber
                        )
                    }
                }

                // Live Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    placeholder = { Text("Search tags, hex ID (0x0110), value...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (isSearching) {
                    Text(
                        text = "$matchCount matching tag(s) found",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                // Tab Selector
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetadataTab.entries.forEach { tab ->
                        val count = when (tab) {
                            MetadataTab.SUMMARY -> null
                            MetadataTab.EXIF -> if (isSearching) filteredExifDirectories.sumOf { it.tags.size } else report.exifDirectories.sumOf { it.tags.size }
                            MetadataTab.IPTC -> if (isSearching) filteredIptcGroups.sumOf { it.datasets.size } else report.iptcReport.allDatasets.size
                            MetadataTab.XMP -> if (isSearching) filteredXmpSchemas.sumOf { it.properties.size } else report.xmpReport.allProperties.size
                            MetadataTab.RAW_XML -> null
                        }

                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (count != null) "${tab.label} ($count)" else tab.label)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                MetadataTab.SUMMARY -> SummaryTabContent(
                    report = report,
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                MetadataTab.EXIF -> ExifTreeTabContent(
                    directories = filteredExifDirectories,
                    expandedMap = exifExpandedState,
                    onToggleDir = { name -> exifExpandedState[name] = !(exifExpandedState[name] ?: true) },
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                MetadataTab.IPTC -> IptcTabContent(
                    groups = filteredIptcGroups,
                    expandedMap = iptcExpandedState,
                    onToggleGroup = { name -> iptcExpandedState[name] = !(iptcExpandedState[name] ?: true) },
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                MetadataTab.XMP -> XmpTabContent(
                    schemas = filteredXmpSchemas,
                    expandedMap = xmpExpandedState,
                    onToggleSchema = { name -> xmpExpandedState[name] = !(xmpExpandedState[name] ?: true) },
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
                MetadataTab.RAW_XML -> RawXmlTabContent(
                    rawXml = report.xmpReport.rawXml,
                    onCopy = { label, text -> copyToClipboard(label, text) }
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, active: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (active) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// -------------------------------------------------------------
// 1. SUMMARY TAB
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryTabContent(
    report: MetadataReport,
    onCopy: (String, String) -> Unit
) {
    val context = LocalContext.current
    val s = report.summary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Camera & Optics Card
        if (!s.make.isNullOrBlank() || !s.model.isNullOrBlank() || !s.lensModel.isNullOrBlank()) {
            SummarySectionCard(
                title = "Camera & Optics",
                icon = Icons.Default.CameraAlt,
                accentColor = MaterialTheme.colorScheme.primary
            ) {
                if (!s.make.isNullOrBlank() || !s.model.isNullOrBlank()) {
                    SummaryItemRow("Camera", "${s.make ?: ""} ${s.model ?: ""}".trim(), onCopy)
                }
                if (!s.lensModel.isNullOrBlank()) {
                    SummaryItemRow("Lens", s.lensModel, onCopy)
                }
                if (!s.software.isNullOrBlank()) {
                    SummaryItemRow("Software / Firmware", s.software, onCopy)
                }
            }
        }

        // Photographic Exposure Card
        if (!s.exposureTime.isNullOrBlank() || !s.fNumber.isNullOrBlank() || !s.iso.isNullOrBlank() || !s.focalLength.isNullOrBlank()) {
            SummarySectionCard(
                title = "Exposure & Shooting Settings",
                icon = Icons.Default.Tune,
                accentColor = Color(0xFF0284C7)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!s.exposureTime.isNullOrBlank()) {
                        ExposureStatPill("Shutter", s.exposureTime, Modifier.weight(1f))
                    }
                    if (!s.fNumber.isNullOrBlank()) {
                        ExposureStatPill("Aperture", s.fNumber, Modifier.weight(1f))
                    }
                    if (!s.iso.isNullOrBlank()) {
                        ExposureStatPill("ISO", s.iso, Modifier.weight(1f))
                    }
                    if (!s.focalLength.isNullOrBlank()) {
                        ExposureStatPill("Focal Length", s.focalLength, Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!s.focalLength35mm.isNullOrBlank()) {
                    SummaryItemRow("35mm Equivalent", s.focalLength35mm, onCopy)
                }
                if (!s.meteringMode.isNullOrBlank()) {
                    SummaryItemRow("Metering Mode", s.meteringMode, onCopy)
                }
                if (!s.exposureProgram.isNullOrBlank()) {
                    SummaryItemRow("Exposure Program", s.exposureProgram, onCopy)
                }
                if (!s.whiteBalance.isNullOrBlank()) {
                    SummaryItemRow("White Balance", s.whiteBalance, onCopy)
                }
                if (!s.flash.isNullOrBlank()) {
                    SummaryItemRow("Flash", s.flash, onCopy)
                }
            }
        }

        // GPS Location Card
        if (s.latitude != null && s.longitude != null) {
            SummarySectionCard(
                title = "GPS Geolocation",
                icon = Icons.Default.LocationOn,
                accentColor = Color(0xFFEA580C)
            ) {
                val coords = String.format("%.6f, %.6f", s.latitude, s.longitude)
                SummaryItemRow("Coordinates", coords, onCopy)
                if (s.altitude != null) {
                    SummaryItemRow("Altitude", "${String.format("%.1f", s.altitude)} meters", onCopy)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        try {
                            val geoUri = Uri.parse("geo:${s.latitude},${s.longitude}?q=${s.latitude},${s.longitude}(Photo Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${s.latitude},${s.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in Maps Application")
                }
            }
        }

        // IPTC & XMP Editorial Content Card
        if (!s.title.isNullOrBlank() || !s.headline.isNullOrBlank() || !s.description.isNullOrBlank() || s.keywords.isNotEmpty() || !s.city.isNullOrBlank()) {
            SummarySectionCard(
                title = "Editorial, IPTC & Keywords",
                icon = Icons.Default.Image,
                accentColor = Color(0xFF10B981)
            ) {
                if (!s.title.isNullOrBlank()) SummaryItemRow("Title", s.title, onCopy)
                if (!s.headline.isNullOrBlank()) SummaryItemRow("Headline", s.headline, onCopy)
                if (!s.description.isNullOrBlank()) SummaryItemRow("Caption / Description", s.description, onCopy)

                if (!s.city.isNullOrBlank() || !s.state.isNullOrBlank() || !s.country.isNullOrBlank()) {
                    val loc = listOfNotNull(s.city, s.state, s.country).joinToString(", ")
                    SummaryItemRow("Location Name", loc, onCopy)
                }

                if (s.keywords.isNotEmpty()) {
                    Text(
                        text = "Keywords (${s.keywords.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        s.keywords.forEach { kw ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                modifier = Modifier.clickable { onCopy("Keyword", kw) }
                            ) {
                                Text(
                                    text = kw,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Rights & Author Card
        if (!s.artist.isNullOrBlank() || !s.copyright.isNullOrBlank()) {
            SummarySectionCard(
                title = "Author & Rights",
                icon = Icons.Default.Info,
                accentColor = Color(0xFF8B5CF6)
            ) {
                if (!s.artist.isNullOrBlank()) SummaryItemRow("Creator / Artist", s.artist, onCopy)
                if (!s.copyright.isNullOrBlank()) SummaryItemRow("Copyright Notice", s.copyright, onCopy)
            }
        }

        // File & Binary Segment Card
        SummarySectionCard(
            title = "File & Binary Segments",
            icon = Icons.Default.FolderOpen,
            accentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            SummaryItemRow("File Name", report.fileName, onCopy)
            SummaryItemRow("File Size", "${formatFileSize(report.fileSize)} (${report.fileSize} bytes)", onCopy)
            SummaryItemRow("MIME Type", report.mimeType, onCopy)
            if (s.imageWidth > 0 && s.imageHeight > 0) {
                SummaryItemRow("Dimensions", "${s.imageWidth} x ${s.imageHeight} px (${String.format("%.1f", (s.imageWidth * s.imageHeight) / 1_000_000f)} MP)", onCopy)
            }
            if (!s.dateTimeOriginal.isNullOrBlank()) {
                SummaryItemRow("Original Timestamp", s.dateTimeOriginal, onCopy)
            }

            if (report.segments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Detected JPEG Segments (${report.segments.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                report.segments.forEach { seg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = seg.markerHex,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = seg.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${seg.length} B",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExposureStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummarySectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun SummaryItemRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(label, value) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f)
        )
    }
}

// -------------------------------------------------------------
// 2. EXIF TREE TAB
// -------------------------------------------------------------
@Composable
private fun ExifTreeTabContent(
    directories: List<ExifDirectoryNode>,
    expandedMap: Map<String, Boolean>,
    onToggleDir: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    if (directories.isEmpty()) {
        EmptyTabNotice("No EXIF tags found or matched search")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(directories, key = { it.name }) { dir ->
            val isExpanded = expandedMap[dir.name] ?: true

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column {
                    // Directory Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDir(dir.name) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dir.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dir.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${dir.tags.size} tags",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Tags list
                    if (isExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            dir.tags.forEachIndexed { index, tag ->
                                ExifTagRow(tag = tag, onCopy = onCopy)
                                if (index < dir.tags.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifTagRow(tag: MetadataTagItem, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(tag.name, tag.formattedValue) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Tag Hex ID badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
        ) {
            Text(
                text = tag.tagIdHex,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = tag.dataType,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Text(
                text = tag.formattedValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (tag.rawValue != tag.formattedValue && tag.rawValue.isNotBlank()) {
                Text(
                    text = "Raw: ${tag.rawValue}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        IconButton(
            onClick = { onCopy(tag.name, tag.formattedValue) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 3. IPTC-IIM TAB
// -------------------------------------------------------------
@Composable
private fun IptcTabContent(
    groups: List<IptcGroupNode>,
    expandedMap: Map<String, Boolean>,
    onToggleGroup: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyTabNotice("No IPTC-IIM datasets found or matched search")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.categoryName }) { grp ->
            val isExpanded = expandedMap[grp.categoryName] ?: true

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleGroup(grp.categoryName) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = grp.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${grp.datasets.size} datasets",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            grp.datasets.forEachIndexed { index, ds ->
                                IptcDatasetRow(ds = ds, onCopy = onCopy)
                                if (index < grp.datasets.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IptcDatasetRow(ds: IptcDatasetItem, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(ds.name, ds.value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
        ) {
            Text(
                text = ds.tagCode,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF047857),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ds.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = ds.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        IconButton(
            onClick = { onCopy(ds.name, ds.value) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy dataset",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 4. XMP SCHEMAS TAB
// -------------------------------------------------------------
@Composable
private fun XmpTabContent(
    schemas: List<XmpSchemaNode>,
    expandedMap: Map<String, Boolean>,
    onToggleSchema: (String) -> Unit,
    onCopy: (String, String) -> Unit
) {
    if (schemas.isEmpty()) {
        EmptyTabNotice("No XMP schemas found or matched search")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(schemas, key = { it.displayName }) { schema ->
            val isExpanded = expandedMap[schema.displayName] ?: true

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSchema(schema.displayName) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = schema.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (schema.namespaceUri.isNotBlank()) {
                                Text(
                                    text = schema.namespaceUri,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${schema.properties.size} props",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            schema.properties.forEachIndexed { index, prop ->
                                XmpPropertyRow(prop = prop, onCopy = onCopy)
                                if (index < schema.properties.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XmpPropertyRow(prop: XmpPropertyItem, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy(prop.qualifiedName, prop.value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF6366F1).copy(alpha = 0.12f),
            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
        ) {
            Text(
                text = prop.qualifiedName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4338CA),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            if (prop.isList && prop.listValues.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    prop.listValues.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = prop.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        IconButton(
            onClick = { onCopy(prop.qualifiedName, prop.value) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Copy property",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// 5. RAW XML TAB
// -------------------------------------------------------------
@Composable
private fun RawXmlTabContent(
    rawXml: String,
    onCopy: (String, String) -> Unit
) {
    if (rawXml.isBlank()) {
        EmptyTabNotice("No raw XMP XML packet found in image")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Raw XMP XML Packet (${rawXml.length} chars)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { onCopy("Raw XMP XML", rawXml) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy XML")
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = rawXml,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun EmptyTabNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
