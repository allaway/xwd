package com.allaway.xwd.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

@Composable
fun CrosswordKeyboard(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ROWS.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (rowIndex == 1) Spacer(0.5f)
                row.forEach { ch ->
                    Key(modifier = Modifier.weight(1f), onClick = { onKey(ch) }) {
                        Text(ch.toString(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (rowIndex == 1) Spacer(0.5f)
                if (rowIndex == 2) {
                    Key(modifier = Modifier.weight(1.5f), onClick = onBackspace) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "Delete",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Spacer(weight: Float) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(weight))
}

@Composable
private fun Key(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
