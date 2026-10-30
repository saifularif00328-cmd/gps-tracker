package com.saifularif.gpstracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saifularif.gpstracker.data.Friend
import com.saifularif.gpstracker.data.FriendState
import com.saifularif.gpstracker.data.statusText
import com.saifularif.gpstracker.ui.theme.Gray
import com.saifularif.gpstracker.ui.theme.Green
import com.saifularif.gpstracker.ui.theme.Orange

fun FriendState.color(): Color = when (this) {
    FriendState.MOVING -> Green
    FriendState.STILL -> Orange
    FriendState.OFFLINE -> Gray
}

@Composable
fun Avatar(name: String, state: FriendState?, size: Dp = 44.dp) {
    val ring = state?.color() ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(size).clip(CircleShape).background(ring),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size - 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun StatusDot(state: FriendState) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(state.color()))
}

@Composable
fun FriendRowHeader(f: Friend, now: Long = System.currentTimeMillis()) {
    val s = f.state(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(f.name, s)
        Box(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(f.name, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(s)
                Box(Modifier.width(6.dp))
                Text(
                    f.statusText(now),
                    style = MaterialTheme.typography.bodySmall,
                    color = com.saifularif.gpstracker.ui.theme.Muted,
                )
            }
        }
    }
}
