package com.example.manga_readerver2.features.migration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.ui.theme.PrimaryOrange

@Composable
fun MigrateDialog(
    oldManga: Manga,
    newManga: Manga,
    onDismiss: () -> Unit,
    onConfirm: (copyReadStatus: Boolean, copyCategories: Boolean, deleteOld: Boolean) -> Unit
) {
    var copyReadStatus by remember { mutableStateOf(true) }
    var copyCategories by remember { mutableStateOf(true) }
    var deleteOld by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Di chuyển nguồn",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Di chuyển từ:\n${oldManga.title}\n\nSang:\n${newManga.title}",
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = copyReadStatus,
                        onCheckedChange = { copyReadStatus = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                    )
                    Text("Sao chép trạng thái đọc", color = Color.White)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = copyCategories,
                        onCheckedChange = { copyCategories = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                    )
                    Text("Sao chép danh mục", color = Color.White)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = deleteOld,
                        onCheckedChange = { deleteOld = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                    )
                    Text("Xóa truyện cũ khỏi thư viện", color = Color.White)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(copyReadStatus, copyCategories, deleteOld) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
            ) {
                Text("Xác nhận", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
