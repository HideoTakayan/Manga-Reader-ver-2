package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.preference.ReaderPreferences
import com.example.manga_readerver2.features.reader.ReadingMode
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val preferences = remember { Injekt.get<ReaderPreferences>() }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("CĂ i Ä‘áº·t trĂ¬nh Ä‘á»c", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                PreferenceHeader("Manga")
                
                PreferenceSwitchItem(
                    title = "Double tap zoom",
                    subtitle = "PhĂ³ng to khi nháº¥n Ä‘Ăºp",
                    checked = preferences.doubleTapZoom.get(),
                    onCheckedChange = { preferences.doubleTapZoom.set(it) }
                )

                PreferenceSwitchItem(
                    title = "Hiá»ƒn thá»‹ sá»‘ trang",
                    checked = preferences.showPageNumber.get(),
                    onCheckedChange = { preferences.showPageNumber.set(it) }
                )

                PreferenceSwitchItem(
                    title = "Äiá»u hÆ°á»›ng phĂ­m Ă¢m lÆ°á»£ng",
                    checked = preferences.volumeKeysNavigation.get(),
                    onCheckedChange = { preferences.volumeKeysNavigation.set(it) }
                )

                PreferenceHeader("Truyá»‡n chá»¯ (Novel)")
                
                PreferenceSliderItem(
                    title = "KĂ­ch thÆ°á»›c chá»¯",
                    value = preferences.fontSize.get(),
                    range = 12f..32f,
                    onValueChange = { preferences.fontSize.set(it) }
                )

                PreferenceSliderItem(
                    title = "Khoáº£ng cĂ¡ch dĂ²ng",
                    value = preferences.lineSpacing.get(),
                    range = 1.0f..2.5f,
                    onValueChange = { preferences.lineSpacing.set(it) }
                )

                PreferenceHeader("Cháº¿ Ä‘á»™ Ä‘á»c máº·c Ä‘á»‹nh")
                
                ReadingModeSelector(
                    selectedMode = preferences.readingMode.get(),
                    onModeSelected = { preferences.readingMode.set(it) }
                )

                PreferenceHeader("Báº£o máº­t & Quyá»n riĂªng tÆ°")
                
                var incognitoMode by remember { mutableStateOf(preferences.incognitoMode.get()) }
                PreferenceSwitchItem(
                    title = "Cháº¿ Ä‘á»™ áº©n danh",
                    subtitle = "KhĂ´ng lÆ°u lá»‹ch sá»­ vĂ  trang Ä‘ang Ä‘á»c",
                    checked = incognitoMode,
                    onCheckedChange = { 
                        incognitoMode = it
                        preferences.incognitoMode.set(it) 
                    }
                )
            }
        }
    }
}

@Composable
fun PreferenceHeader(title: String) {
    Text(
        text = title,
        color = PrimaryOrange,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@Composable
fun PreferenceSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(checked) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { 
            isChecked = !isChecked
            onCheckedChange(isChecked)
        },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, color = Color.Gray, fontSize = 13.sp)
                }
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { 
                    isChecked = it
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryOrange, checkedTrackColor = PrimaryOrange.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun PreferenceSliderItem(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember { mutableStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = PrimaryOrange, activeTrackColor = PrimaryOrange)
        )
    }
}

@Composable
fun ReadingModeSelector(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit
) {
    val modes = listOf("Dá»c", "Ngang", "Webtoon")
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        modes.forEachIndexed { index, name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(index) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == index,
                    onClick = { onModeSelected(index) },
                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(name, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}


