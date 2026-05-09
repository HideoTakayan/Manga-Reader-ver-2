package com.example.manga_readerver2.features.library.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.preference.LibraryPreferences
import com.example.manga_readerver2.features.library.LibraryDisplayMode
import com.example.manga_readerver2.core.preference.TriState
import com.example.manga_readerver2.ui.components.*
import com.example.manga_readerver2.ui.theme.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    preferences: LibraryPreferences = Injekt.get()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Lọc", "Sắp xếp", "Hiển thị")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = PrimaryOrange,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) },
                            unselectedContentColor = TextSecondary
                        )
                    }
                }
                
                HorizontalDivider(color = GlassBorder)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    when (selectedTabIndex) {
                        0 -> FilterPage()
                        1 -> SortPage()
                        2 -> DisplayPage(preferences)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Đóng", color = PrimaryOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPage(preferences: LibraryPreferences = Injekt.get()) {
    Column {
        HeadingItem("Trạng thái tải")
        val downloadedState by preferences.filterDownloaded.asFlow().collectAsState(preferences.filterDownloaded.get())
        TriStateItem(
            label = "Đã tải xuống",
            state = TriState.values()[downloadedState],
            onClick = { preferences.filterDownloaded.set(it.ordinal) }
        )

        HeadingItem("Tiến độ đọc")
        val unreadState by preferences.filterUnread.asFlow().collectAsState(preferences.filterUnread.get())
        TriStateItem(
            label = "Chưa đọc",
            state = TriState.values()[unreadState],
            onClick = { preferences.filterUnread.set(it.ordinal) }
        )
        
        val startedState by preferences.filterStarted.asFlow().collectAsState(preferences.filterStarted.get())
        TriStateItem(
            label = "Đang đọc",
            state = TriState.values()[startedState],
            onClick = { preferences.filterStarted.set(it.ordinal) }
        )

        HeadingItem("Khác")
        val bookmarkedState by preferences.filterBookmarked.asFlow().collectAsState(preferences.filterBookmarked.get())
        TriStateItem(
            label = "Đã đánh dấu",
            state = TriState.values()[bookmarkedState],
            onClick = { preferences.filterBookmarked.set(it.ordinal) }
        )
    }
}

@Composable
private fun SortPage() {
    Column {
        var sortDescending by remember { mutableStateOf<Boolean?>(true) }
        HeadingItem("Sắp xếp theo")
        SortItem(label = "Tên", sortDescending = if (false) sortDescending else null, onClick = {})
        SortItem(label = "Ngày thêm", sortDescending = if (true) sortDescending else null, onClick = { sortDescending = sortDescending?.not() ?: true })
        SortItem(label = "Lần đọc cuối", sortDescending = null, onClick = {})
        SortItem(label = "Số chương chưa đọc", sortDescending = null, onClick = {})
    }
}

@Composable
private fun DisplayPage(preferences: LibraryPreferences) {
    Column {
        HeadingItem("Chế độ hiển thị")
        val displayMode by preferences.displayMode.asFlow().collectAsState(preferences.displayMode.get())
        val modes = listOf(
            LibraryDisplayMode.CompactGrid to "Lưới thu gọn",
            LibraryDisplayMode.ComfortGrid to "Lưới thoải mái",
            LibraryDisplayMode.CoverOnlyGrid to "Chỉ ảnh bìa",
            LibraryDisplayMode.List to "Danh sách"
        )
        
        modes.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { preferences.displayMode.set(mode.ordinal) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = displayMode == mode.ordinal,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange, unselectedColor = TextSecondary)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, color = TextPrimary, fontSize = 16.sp)
            }
        }

        HeadingItem("Nhãn hiển thị")
        val showDownloadBadge by preferences.showDownloadBadges.asFlow().collectAsState(preferences.showDownloadBadges.get())
        CheckboxItem(label = "Nhãn đã tải xuống", checked = showDownloadBadge, onClick = { preferences.showDownloadBadges.set(!showDownloadBadge) })
        
        val showUnreadBadge by preferences.showUnreadBadges.asFlow().collectAsState(preferences.showUnreadBadges.get())
        CheckboxItem(label = "Nhãn chưa đọc", checked = showUnreadBadge, onClick = { preferences.showUnreadBadges.set(!showUnreadBadge) })
    }
}
