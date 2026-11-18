package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.CloudSyncStatus
import com.example.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrightSyncedGreen = Color(0xFF4ADE80)
private val SyncedPillBg = Color(0xFF16A34A).copy(alpha = 0.25f)
private val SyncedPillBorder = Color(0xFF4ADE80).copy(alpha = 0.65f)
private val SyncedTextColor = Color(0xFFDCFCE7)

@Composable
fun TopBarCloudSyncPill(
    syncState: CloudSyncState,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cloud_sync_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    val bgColor by animateColorAsState(
        targetValue = when (syncState.status) {
            CloudSyncStatus.SYNCING -> Color.White.copy(alpha = 0.14f)
            CloudSyncStatus.SYNCED -> SyncedPillBg
            CloudSyncStatus.ERROR -> Color(0xFFF59E0B).copy(alpha = 0.24f)
        },
        label = "pill_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = when (syncState.status) {
            CloudSyncStatus.SYNCING -> Color(0xFF93C5FD).copy(alpha = 0.5f)
            CloudSyncStatus.SYNCED -> SyncedPillBorder
            CloudSyncStatus.ERROR -> Color(0xFFFBBF24).copy(alpha = 0.65f)
        },
        label = "pill_border",
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag("top_cloud_sync_indicator"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            when (syncState.status) {
                CloudSyncStatus.SYNCING -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = stringResource(R.string.cloud_sync_syncing),
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier
                            .size(15.dp)
                            .alpha(pulseAlpha),
                    )
                    Text(
                        text = stringResource(R.string.cloud_sync_syncing),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                CloudSyncStatus.SYNCED -> {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = stringResource(R.string.cloud_sync_synced),
                        tint = BrightSyncedGreen,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(R.string.cloud_sync_synced),
                        color = SyncedTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                CloudSyncStatus.ERROR -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = stringResource(R.string.cloud_sync_error),
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = stringResource(R.string.cloud_sync_error),
                        color = Color(0xFFFEF3C7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun CloudAccountStatusDialog(
    userEmail: String?,
    userDisplayName: String?,
    syncState: CloudSyncState,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val timeText = syncState.lastSyncedAtMillis?.let { millis ->
        SimpleDateFormat("HH:mm:ss", Locale("es", "ES")).format(Date(millis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            when (syncState.status) {
                CloudSyncStatus.SYNCING -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
                CloudSyncStatus.SYNCED -> Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(32.dp),
                )
                CloudSyncStatus.ERROR -> Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = appColors.amber,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        title = {
            Text("Cuenta Google y Nube")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = appColors.cardSubtleBg,
                    border = BorderStroke(1.dp, appColors.borderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!userDisplayName.isNullOrBlank()) {
                            Text(
                                text = userDisplayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = userEmail ?: "Cuenta Google conectada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val statusLabel = when (syncState.status) {
                    CloudSyncStatus.SYNCING -> "Sincronizando cambios con la nube..."
                    CloudSyncStatus.SYNCED -> if (timeText != null) {
                        "Sincronizado correctamente ($timeText)"
                    } else {
                        "Sincronizado correctamente"
                    }
                    CloudSyncStatus.ERROR -> syncState.errorMessage
                        ?: "Sin conexión. Se sincronizará automáticamente al volver internet."
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (syncState.status) {
                        CloudSyncStatus.SYNCED -> appColors.green
                        CloudSyncStatus.ERROR -> appColors.amber
                        CloudSyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                    },
                )

                Button(
                    onClick = onSyncNow,
                    enabled = syncState.status != CloudSyncStatus.SYNCING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_sync_now_button"),
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sincronizar ahora")
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.red),
                    border = BorderStroke(1.dp, appColors.red.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_sign_out_button"),
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.google_sign_out_button))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}
