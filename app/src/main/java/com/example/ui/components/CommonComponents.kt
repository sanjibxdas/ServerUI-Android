package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.Locale

// ─── Format Helpers ─────────────────────────────────────────────────────────
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var b = bytes.toDouble()
    var idx = 0
    while (b >= 1024.0 && idx < units.size - 1) {
        b /= 1024.0
        idx++
    }
    return String.format(Locale.US, "%.1f %s", b, units[idx])
}

fun formatRate(bps: Long): String {
    return "${formatBytes(bps)}/s"
}

fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

fun formatPercent(value: Double): String {
    return String.format(Locale.US, "%.1f%%", value)
}

// ─── xAI Design System Primitives ───────────────────────────────────────────

/**
 * 1px hairline divider matching `{components.divider-hairline}`.
 */
@Composable
fun XHairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = ColorHairline
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = color
    )
}

/**
 * Eyebrow mono caption matching `{components.eyebrow-mono}`:
 * Uppercase, positive letter tracking, GeistMono monospace font.
 */
@Composable
fun XEyebrowMono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ColorBodyMid,
    style: TextStyle = TypographyCaptionMonoSM
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = style,
        color = color,
        modifier = modifier
    )
}

/**
 * Primary button matching `{components.button-primary}`:
 * White-filled pill `{rounded.pill}`, on-primary dark text, weight 400.
 */
@Composable
fun XPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val horizontalPad = if (compact) 12.dp else 16.dp
    val verticalPad = if (compact) 6.dp else 8.dp
    val minHeight = if (compact) 36.dp else 40.dp

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorPrimary,
            contentColor = ColorOnPrimary,
            disabledContainerColor = ColorCanvasMid,
            disabledContentColor = ColorBodyMid
        ),
        contentPadding = PaddingValues(horizontal = horizontalPad, vertical = verticalPad),
        modifier = modifier.heightIn(min = minHeight)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorOnPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = TypographyButtonMD,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Outline pill button matching `{components.button-outline-on-dark}`:
 * Translucent-white 1px border `rgba(255, 255, 255, 0.25)`, near-black canvas, white text.
 */
@Composable
fun XPillOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val horizontalPad = if (compact) 12.dp else 16.dp
    val verticalPad = if (compact) 6.dp else 8.dp
    val minHeight = if (compact) 36.dp else 40.dp

    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = modifier
            .heightIn(min = minHeight)
            .border(width = 1.dp, color = ColorPillOutline, shape = CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = ColorInk),
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = verticalPad)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) ColorInk else ColorBodyMid,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = TypographyButtonMD,
                color = if (enabled) ColorInk else ColorBodyMid,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Standard content card matching `{components.card-content}`:
 * Background `{colors.canvas-card}` (#191919), 1px solid `{colors.hairline}` (#212327) border,
 * shape `{rounded.sm}` (8px), ZERO DROP SHADOWS.
 */
@Composable
fun XCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorCanvasCard,
    borderColor: Color = ColorHairline,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = backgroundColor,
        shape = shape,
        modifier = modifier.border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ─── Status Badge (Clean Outline Pill) ──────────────────────────────────────
@Composable
fun ServerStatusBadge(
    status: String,
    connected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when {
        connected || status == "online" -> Pair(ColorStatusOnline, "ONLINE")
        status == "connecting" -> Pair(ColorStatusConnecting, "CONNECTING")
        status == "authentication_failed" -> Pair(ColorStatusError, "AUTH FAILED")
        status == "offline" -> Pair(ColorBodyMid, "OFFLINE")
        else -> Pair(ColorBodyMid, status.uppercase(Locale.ROOT))
    }

    Surface(
        color = ColorCanvasSoft,
        shape = CircleShape,
        modifier = modifier.border(width = 1.dp, color = ColorHairline, shape = CircleShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = TypographyCaptionMonoSM,
                color = ColorInk
            )
        }
    }
}

// ─── Floating Navigation Layout Constant ────────────────────────────────────
/**
 * Shared vertical height reserved for the bottom navigation bar.
 * Measured rendered height: 58.dp (bar height) + 2.dp (top padding) + 4.dp (bottom padding) = 64.dp.
 */
val NavBarReserved: Dp = 64.dp

// ─── Metric Gauge Card (xAI Precision Card) ─────────────────────────────────
@Composable
fun MetricGaugeCard(
    title: String,
    value: String,
    subValue: String,
    percent: Double,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    XCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ColorCanvasSoft,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .size(32.dp)
                        .border(1.dp, ColorHairline, RoundedCornerShape(6.dp)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ColorInk,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                XEyebrowMono(text = title)
            }
            Text(
                text = formatPercent(percent),
                style = TypographyCaptionMono,
                color = ColorInk
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Linear Progress Indicator with hairline track
        LinearProgressIndicator(
            progress = { (percent / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = ColorPrimary,
            trackColor = ColorCanvasMid,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = TypographyBodySM,
                color = ColorInk
            )
            Text(
                text = subValue,
                style = TypographyCaptionMonoSM,
                color = ColorBodyMid
            )
        }
    }
}

// ─── Empty State View (xAI Clean Aesthetic) ─────────────────────────────────
@Composable
fun EmptyStateView(
    title: String,
    description: String,
    icon: ImageVector,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ColorCanvasCard,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = ColorHairline, shape = RoundedCornerShape(8.dp))
            .padding(vertical = 40.dp, horizontal = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = ColorCanvasSoft,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .border(width = 1.dp, color = ColorPillOutline, shape = CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ColorInk,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            XEyebrowMono(text = "System Notice")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = TypographyDisplayXS,
                color = ColorInk
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = TypographyBodySM,
                color = ColorBodyMid,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(20.dp))
                XPillButton(
                    text = actionText,
                    onClick = onAction,
                    modifier = Modifier.testTag("empty_state_action_button")
                )
            }
        }
    }
}
