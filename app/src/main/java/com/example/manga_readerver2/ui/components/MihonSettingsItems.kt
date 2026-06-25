package com.example.manga_readerver2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manga_readerver2.core.preference.TriState
import com.example.manga_readerver2.ui.theme.*

object MihonSettingsPaddings {
    val Horizontal = 24.dp
    val Vertical = 12.dp
}

@Composable
fun HeadingItem(text: String) {
    Text(
        text = text.uppercase(),
        color = PrimaryOrange,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MihonSettingsPaddings.Horizontal,
                vertical = 8.dp,
            ),
    )
}

@Composable
fun SortItem(label: String, sortDescending: Boolean?, onClick: () -> Unit) {
    val arrowIcon = when (sortDescending) {
        true -> Icons.Default.ArrowDownward
        false -> Icons.Default.ArrowUpward
        null -> null
    }

    BaseSettingsItem(
        label = label,
        widget = {
            if (arrowIcon != null) {
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = null,
                    tint = PrimaryOrange,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        },
        onClick = onClick,
    )
}

@Composable
fun CheckboxItem(label: String, checked: Boolean, onClick: () -> Unit) {
    BaseSettingsItem(
        label = label,
        widget = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryOrange,
                    uncheckedColor = TextSecondary
                )
            )
        },
        onClick = onClick,
    )
}

@Composable
fun TriStateItem(
    label: String,
    state: TriState,
    enabled: Boolean = true,
    onClick: (TriState) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(
                enabled = enabled,
                onClick = {
                    onClick(state.next())
                },
            )
            .fillMaxWidth()
            .padding(
                horizontal = MihonSettingsPaddings.Horizontal,
                vertical = MihonSettingsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val stateAlpha = if (enabled) 1f else 0.4f

        Icon(
            imageVector = when (state) {
                TriState.DISABLED -> Icons.Rounded.CheckBoxOutlineBlank
                TriState.ENABLED_IS -> Icons.Rounded.CheckBox
                TriState.ENABLED_NOT -> Icons.Rounded.DisabledByDefault
            },
            contentDescription = null,
            tint = if (!enabled) {
                TextSecondary.copy(alpha = stateAlpha)
            } else {
                when (state) {
                    TriState.DISABLED -> TextSecondary
                    TriState.ENABLED_IS -> PrimaryOrange
                    TriState.ENABLED_NOT -> Color.Red
                }
            },
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = TextPrimary.copy(alpha = stateAlpha),
            fontSize = 16.sp,
        )
    }
}

@Composable
fun SliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueString: String = value.toInt().toString(),
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MihonSettingsPaddings.Horizontal, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 16.sp)
            Text(valueString, color = PrimaryOrange, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryOrange,
                activeTrackColor = PrimaryOrange,
                inactiveTrackColor = SurfaceDark
            )
        )
    }
}

@Composable
private fun BaseSettingsItem(
    label: String,
    widget: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(
                horizontal = MihonSettingsPaddings.Horizontal,
                vertical = MihonSettingsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        widget(this)
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 16.sp,
        )
    }
}

