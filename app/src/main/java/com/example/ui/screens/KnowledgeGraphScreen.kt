package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.brain.AskAiChatMessage
import com.example.data.brain.AttachedAiFile
import com.example.data.brain.RagAnswer
import com.example.data.local.AiProviderConfigEntity
import java.io.File

/**
 * Brain is a consumer: retrieval + answer generation only.
 * File AI owns document enrichment/embedding and Gallery AI owns VLM enrichment/embedding.
 */
@Composable
fun BrainScreen(
    aiConfig: AiProviderConfigEntity?,
    ragAnswer: RagAnswer?,
    isRagQuerying: Boolean,
    askAiMessages: List<AskAiChatMessage> = emptyList(),
    attachedAiFile: AttachedAiFile? = null,
    onAttachFile: (File) -> Unit = {},
    onDetachFile: () -> Unit = {},
    onClearChat: () -> Unit = {},
    onQueryRag: (String) -> Unit,
    onCancelRag: () -> Unit = {},
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var queryText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { copyUriToTempFile(context, it)?.let(onAttachFile) }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { copyUriToTempFile(context, it)?.let(onAttachFile) }
    }

    LaunchedEffect(askAiMessages.size, isRagQuerying) {
        if (askAiMessages.isNotEmpty()) listState.animateScrollToItem(askAiMessages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Brain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        aiConfig?.chatModel?.ifBlank { "Ask your indexed files" } ?: "Ask your indexed files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        if (isRagQuerying) onCancelRag()
                        onClearChat()
                        onDetachFile()
                        queryText = ""
                    },
                    modifier = Modifier.testTag("brain_new_chat_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
                }
                IconButton(
                    onClick = onOpenAiSettings,
                    modifier = Modifier.testTag("brain_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "AI settings")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (askAiMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Ask your files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Brain searches saved File AI and Gallery AI data, then uses the configured LLM to answer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(askAiMessages, key = { it.id }) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (message.isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 360.dp)
                            ) {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    if (isRagQuerying) {
                        item {
                            Text(
                                "Thinking…",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        attachedAiFile?.let { file ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(file.file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDetachFile) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.testTag("brain_attach_file")
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                }
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Attach image")
                }
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your files") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isRagQuerying && queryText.isNotBlank()) {
                                onQueryRag(queryText.trim())
                                queryText = ""
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (isRagQuerying) {
                    IconButton(onClick = onCancelRag) {
                        Icon(Icons.Default.Clear, contentDescription = "Stop")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (queryText.isNotBlank()) {
                                onQueryRag(queryText.trim())
                                queryText = ""
                            }
                        },
                        enabled = queryText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

private fun copyUriToTempFile(context: Context, uri: Uri): File? {
    return try {
        var displayName = "attached_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) displayName = cursor.getString(index) ?: displayName
        }
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = File(context.cacheDir, "ai_" + System.currentTimeMillis() + "_" + safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.takeIf { it.exists() && it.length() > 0L }
    } catch (_: Exception) {
        null
    }
}
