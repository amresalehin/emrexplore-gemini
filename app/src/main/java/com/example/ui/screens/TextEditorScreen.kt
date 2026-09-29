package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileItem
import com.example.ui.components.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    fileItem: FileItem,
    content: String,
    isEditing: Boolean,
    onContentChange: (String) -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val linesCount = content.lines().size
    val wordsCount = if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileItem.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${formatFileSize(fileItem.size)} • $linesCount lines • $wordsCount words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleEdit(!isEditing) }) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditing) "View Mode" else "Edit Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isEditing) {
                        IconButton(onClick = onSave) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Changes",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    placeholder = { Text("Type file content here...") }
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium
                ) {
                    SelectionContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        ) {
                            // Line numbers gutter
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                for (i in 1..linesCount) {
                                    Text(
                                        text = "$i",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            // Text content
                            Text(
                                text = content.ifEmpty { "(Empty file)" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
