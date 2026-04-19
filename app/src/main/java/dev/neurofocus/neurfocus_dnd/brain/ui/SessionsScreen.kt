package dev.neurofocus.neurfocus_dnd.brain.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.ui.components.GlassCard
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSurfaceWhite
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun SessionsScreen(modifier: Modifier = Modifier) {
    val days = remember { buildWeekAroundToday() }
    var selectedIndex by remember {
        mutableIntStateOf(days.indexOfFirst { it.isToday }.coerceAtLeast(0))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Activity timeline",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Tap a day to review focus and mindfulness blocks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(days) { index, day ->
                val selected = index == selectedIndex
                val interaction = remember(index) { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) NeuroSkyBlue.copy(alpha = 0.55f) else NeuroSurfaceWhite.copy(alpha = 0.75f),
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { selectedIndex = index },
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeuroNavy.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${day.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NeuroNavy,
                    )
                }
            }
        }

        GlassCard {
            TimelineChart()
        }
    }
}

private data class DayModel(
    val label: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
)

private fun buildWeekAroundToday(): List<DayModel> {
    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    val labelFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val start = cal.clone() as Calendar
    start.add(Calendar.DAY_OF_MONTH, -3)
    return (0..6).map { i ->
        val c = start.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, i)
        val isToday =
            c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        DayModel(
            label = labelFmt.format(c.time),
            dayOfMonth = c.get(Calendar.DAY_OF_MONTH),
            isToday = isToday,
        )
    }
}

@Composable
private fun TimelineChart() {
    val navy = NeuroNavy
    val blue = NeuroSkyBlue.copy(alpha = 0.9f)
    val cream = NeuroSkyBlue.copy(alpha = 0.35f)
    val markers = listOf(
        Triple(0.12f, 0.55f, Icons.Outlined.SelfImprovement to navy),
        Triple(0.45f, 0.72f, Icons.Outlined.Psychology to blue),
        Triple(0.72f, 0.48f, Icons.Outlined.AutoAwesome to cream),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = NeuroNavy,
        )
        val hours = listOf("6:00 AM", "8:00 AM", "10:00 AM", "12:00 PM")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            hours.forEach { h ->
                Text(
                    text = h,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeuroNavy.copy(alpha = 0.45f),
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val chartW = maxWidth - 28.dp
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val padL = 28.dp.toPx()
                val chartPxW = w - padL
                val chartH = h
                val grid = NeuroNavy.copy(alpha = 0.08f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                for (i in 1..4) {
                    val y = chartH * i / 5f
                    drawLine(
                        color = grid,
                        start = Offset(padL, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }
                markers.forEach { (nx, frac, pair) ->
                    val (_, color) = pair
                    val cx = padL + nx * chartPxW
                    val barTop = chartH * (1f - frac)
                    val barH = chartH - barTop
                    drawRoundRect(
                        color = color.copy(alpha = 0.4f),
                        topLeft = Offset(cx - 14.dp.toPx(), barTop),
                        size = Size(28.dp.toPx(), barH),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    )
                }
            }
            markers.forEach { (nx, frac, pair) ->
                val (icon, color) = pair
                val cx = 28.dp + chartW * nx
                val top = maxHeight * (1f - frac)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .offset(
                            x = cx - 12.dp,
                            y = top - 26.dp,
                        )
                        .width(24.dp)
                        .height(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SessionsScreenPreview() {
    NeurfocusdndTheme {
        SessionsScreen()
    }
}
