package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.ui.theme.BackgroundDark

class GuideScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scrollState = rememberScrollState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Hướng dẫn sử dụng", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundDark,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = BackgroundDark
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GuideSection(
                    title = "1. Cài đặt tiện ích mở rộng (Extensions)",
                    content = "Để đọc truyện, bạn cần cài đặt các tiện ích mở rộng. Truy cập vào mục 'Khám phá' > chuyển sang tab 'Tiện ích' và nhấn 'Cài đặt' cho các nguồn truyện bạn muốn (ví dụ: TruyenQQ, Hako, v.v.)."
                )

                GuideSection(
                    title = "2. Thêm truyện vào thư viện",
                    content = "Sau khi cài đặt tiện ích, tìm truyện bạn thích và nhấn nút 'Thêm vào' (biểu tượng trái tim) để lưu truyện vào Thư viện cá nhân. Bạn có thể phân loại truyện theo các Danh mục khác nhau."
                )

                GuideSection(
                    title = "3. Tải truyện đọc ngoại tuyến (Offline)",
                    content = "Trong màn hình chi tiết truyện, bạn có thể tải từng chương hoặc nhiều chương cùng lúc. Vào màn hình đọc và nhấn nút Tải xuống. Các chương đã tải sẽ nằm trong 'Hàng đợi tải xuống'."
                )

                GuideSection(
                    title = "4. Tính năng theo dõi (Tracking) - Sắp ra mắt",
                    content = "Tính năng đồng bộ lịch sử đọc với các nền tảng như AniList, MyAnimeList đang được phát triển và sẽ sớm ra mắt ở phiên bản tiếp theo."
                )
                
                GuideSection(
                    title = "5. Đọc Truyện Chữ & Lấy Mật Khẩu (Trình duyệt nội bộ)",
                    content = "Đối với một số nguồn truyện yêu cầu vượt Captcha hoặc nhập mật khẩu, hãy nhấn biểu tượng 'WebView' (Hình quả địa cầu) trong phần Chi tiết truyện để mở trang web gốc."
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chúc bạn có những giây phút đọc truyện vui vẻ!",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun GuideSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = com.example.manga_readerver2.ui.theme.PrimaryOrange,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = content,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}
