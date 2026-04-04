package com.example.gemma4

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Debug
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemma4.ui.theme.ModelBubble
import com.example.gemma4.ui.theme.ModelText
import com.example.gemma4.ui.theme.UserBubble
import com.example.gemma4.ui.theme.UserText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel(), onOpenSettings: () -> Unit = {}) {
    val uiState = chatViewModel.uiState
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatViewModel.modelType.displayName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    TextButton(onClick = { onOpenSettings() }) {
                        Text("\u2699")
                    }
                    TextButton(onClick = { chatViewModel.resetChat() }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (chatViewModel.isModelLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${chatViewModel.modelType.displayName} 로딩 중...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "초기화에 수십 초 소요될 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        reverseLayout = true,
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(uiState.messages.reversed()) { message ->
                            MessageBubble(message)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (uiState.isGenerating) {
                        Button(
                            onClick = { chatViewModel.stopGeneration() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("생성 중단")
                        }
                    }

                    MessageInput(
                        enabled = !uiState.isGenerating,
                        tokenCount = uiState.inputTokenCount,
                        attachedImageBytes = chatViewModel.attachedImageBytes,
                        attachedAudioBytes = chatViewModel.attachedAudioBytes,
                        onTextChanged = { chatViewModel.onInputTextChanged(it) },
                        onSend = { chatViewModel.sendMessage(it) },
                        onImageAttached = { chatViewModel.attachImage(it) },
                        onAudioAttached = { chatViewModel.attachAudio(it) },
                        onClearImage = { chatViewModel.clearImage() },
                        onClearAudio = { chatViewModel.clearAudio() }
                    )
                }
            }

            MemoryOverlay(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun MemoryOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pssMb by remember { mutableLongStateOf(0L) }
    var availableRamMb by remember { mutableLongStateOf(0L) }
    var nativeHeapMb by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                availableRamMb = memInfo.availMem / (1024 * 1024)

                // PSS: 공유 메모리 포함 실제 점유량 (mmap 모델 포함)
                val debugMemInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(debugMemInfo)
                pssMb = debugMemInfo.totalPss / 1024L

                nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            }
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = "PSS  ${pssMb}MB",
            color = Color.Green,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
        Text(
            text = "NAT  ${nativeHeapMb}MB",
            color = Color.Cyan,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
        Text(
            text = "FREE ${availableRamMb}MB",
            color = Color.Yellow,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.author == MessageAuthor.USER
    val bubbleColor = if (isUser) UserBubble else ModelBubble
    val textColor = if (isUser) UserText else ModelText
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            if (message.isLoading && message.text.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(4.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    enabled: Boolean,
    tokenCount: Int,
    attachedImageBytes: ByteArray?,
    attachedAudioBytes: ByteArray?,
    onTextChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onImageAttached: (ByteArray) -> Unit,
    onAudioAttached: (ByteArray) -> Unit,
    onClearImage: () -> Unit,
    onClearAudio: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder() }

    // 카메라 캡처를 저장할 임시 URI
    var photoFileRef by remember { mutableStateOf<File?>(null) }

    // 카메라 결과 처리
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            scope.launch(Dispatchers.IO) {
                val bytes = photoFileRef?.readBytes()
                if (bytes != null) onImageAttached(bytes)
            }
        }
    }

    fun launchCamera() {
        val imagesDir = File(context.cacheDir, "images").also { it.mkdirs() }
        val file = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
        photoFileRef = file
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        cameraLauncher.launch(uri)
    }

    // 카메라 권한 요청
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
    }

    // 마이크 권한 요청
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioRecorder.start()
            isRecording = true
        }
    }

    fun onCameraClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onMicClick() {
        if (isRecording) {
            scope.launch(Dispatchers.IO) {
                val bytes = audioRecorder.stop()
                withContext(Dispatchers.Main) {
                    isRecording = false
                    onAudioAttached(bytes)
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                audioRecorder.start()
                isRecording = true
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun send() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
            text = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 첨부 미리보기
        if (attachedImageBytes != null || attachedAudioBytes != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (attachedImageBytes != null) {
                    Box {
                        val bitmap = remember(attachedImageBytes) {
                            BitmapFactory.decodeByteArray(attachedImageBytes, 0, attachedImageBytes.size)
                                ?.asImageBitmap()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "첨부 이미지",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // X 버튼
                        IconButton(
                            onClick = onClearImage,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Text("×", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                if (attachedAudioBytes != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎙 오디오 첨부됨", fontSize = 13.sp)
                            IconButton(
                                onClick = onClearAudio,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("×", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // 녹음 중 표시
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
                Text(
                    text = "녹음 중... (마이크 버튼을 눌러 중지)",
                    fontSize = 12.sp,
                    color = Color.Red
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 카메라 버튼
            IconButton(
                onClick = ::onCameraClick,
                enabled = enabled
            ) {
                Text(
                    text = "📷",
                    fontSize = 20.sp,
                    color = if (enabled) Color.Unspecified else Color.Gray
                )
            }

            // 마이크 버튼
            IconButton(
                onClick = ::onMicClick,
                enabled = enabled || isRecording
            ) {
                Text(
                    text = if (isRecording) "⏹" else "🎙",
                    fontSize = 20.sp,
                    color = if (isRecording) Color.Red
                    else if (enabled) Color.Unspecified
                    else Color.Gray
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onTextChanged(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("메시지를 입력하세요...") },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = { send() },
                enabled = enabled && text.isNotBlank()
            ) {
                Text(
                    text = "\u27A4",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enabled && text.isNotBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        Color.Gray
                )
            }
        }

        if (text.isNotEmpty()) {
            Text(
                text = "입력: ~$tokenCount 토큰",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
            )
        }
    }
}
