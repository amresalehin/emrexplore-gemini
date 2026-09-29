package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

/**
 * Returns the list of standard runtime storage permissions needed according to Android API level.
 */
fun getRequiredStoragePermissions(): List<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}

/**
 * Checks whether full external storage manager permission is granted (Android 11+ / API 30+).
 */
fun isAllFilesAccessGranted(): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    } catch (e: Throwable) {
        false
    }
}

/**
 * Creates an Intent to open the Manage All Files Access screen.
 */
fun createAllFilesAccessIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } catch (e: Exception) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}

/**
 * Opens system settings for app detail permissions.
 */
fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/**
 * Opens system settings to grant All Files Access permission (Android 11+).
 */
fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                openAppSettings(context)
            }
        }
    } else {
        openAppSettings(context)
    }
}

/**
 * Launches All Files Access permission settings using ActivityResultLauncher.
 */
fun launchAllFilesAccessSettings(context: Context, launcher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            launcher.launch(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                launcher.launch(intent)
            } catch (ex: Exception) {
                openAppSettings(context)
            }
        }
    } else {
        openAppSettings(context)
    }
}

/**
 * Dialog prompting the user to grant All Files Access permission (Android 11+).
 */
@Composable
fun AllFilesAccessDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "All Files Access Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "To browse folders, manage documents, play audio, and edit or move files across your device, emrexplore needs 'All files access' permission.",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
                Text(
                    text = "In the next screen, toggle 'Allow access to manage all files'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                modifier = Modifier.testTag("confirm_all_files_dialog_button")
            ) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        },
        modifier = modifier.testTag("all_files_access_dialog")
    )
}

/**
 * Accompanist Permissions Banner for Storage access.
 * Displays rationale and action buttons when runtime storage permissions or All Files Access are not yet granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoragePermissionBanner(
    permissionsState: MultiplePermissionsState,
    allFilesAccessGranted: Boolean = isAllFilesAccessGranted(),
    onGrantAllFilesAccess: () -> Unit = { },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAndroid11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val needsAllFilesAccess = isAndroid11Plus && !allFilesAccessGranted
    val needsRuntimePermissions = !permissionsState.allPermissionsGranted
    val isVisible = needsAllFilesAccess || needsRuntimePermissions

    val anyGranted = permissionsState.permissions.any { it.status.isGranted } || allFilesAccessGranted

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("storage_permission_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (needsAllFilesAccess) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (needsAllFilesAccess) Icons.Default.Storage else if (anyGranted) Icons.Default.FolderShared else Icons.Default.Lock,
                        contentDescription = "Storage Permission Icon",
                        tint = if (needsAllFilesAccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (needsAllFilesAccess) {
                            "All Files Access Required"
                        } else if (anyGranted) {
                            "Partial Storage Access Granted"
                        } else {
                            "Storage Permission Required"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (needsAllFilesAccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val rationaleText = if (needsAllFilesAccess) {
                    "emrexplore needs permission to access and manage all files to browse, create, edit, rename, move, delete, and compress files across your internal and external storage."
                } else if (permissionsState.shouldShowRationale) {
                    "emrexplore needs storage access to read and write files, browse folders, play audio, and organize documents on your external storage."
                } else if (anyGranted) {
                    "Some storage permissions are still missing. Grant all requested permissions for full reading and writing capabilities."
                } else {
                    "Grant storage permission to read, write, edit, and organize files on this device."
                }

                Text(
                    text = rationaleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (needsAllFilesAccess) {
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    },
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (needsAllFilesAccess) {
                        Button(
                            onClick = onGrantAllFilesAccess,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier.testTag("grant_all_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Grant All Files Access",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                permissionsState.launchMultiplePermissionRequest()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Grant Permission",
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            openAppSettings(context)
                        },
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Settings",
                            color = if (needsAllFilesAccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
