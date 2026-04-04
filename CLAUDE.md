# Gemma 4 E2B Android 챗봇 앱

## 프로젝트 개요
Gemma 4 E2B 모델을 LiteRT-LM SDK로 온디바이스 실행하는 Android 챗봇 앱.

## 빌드 & 배포
```bash
# 빌드
./gradlew assembleDebug

# 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 실행
adb shell am start -n com.example.gemma4/.MainActivity

# 로그 확인
adb logcat | grep -E "gemma4|InferenceModel|ModelDownloader"
```

## 기술 스택
- **언어**: Kotlin 2.0.0 (`-Xskip-metadata-version-check` 필요 — litertlm-android가 Kotlin 2.3.0 메타데이터로 컴파일됨)
- **UI**: Jetpack Compose + Material3
- **추론 엔진**: LiteRT-LM SDK (`com.google.ai.edge.litertlm:litertlm-android:0.10.0`)
- **모델**: `gemma-4-E2B-it.litertlm` (2.6GB, HuggingFace)
- **빌드**: AGP 8.5.0 + Gradle 8.7

## 모델 정보
| 항목 | 값 |
|------|-----|
| 파일명 | `gemma-4-E2B-it.litertlm` |
| 크기 | 2.6GB |
| HuggingFace | `litert-community/gemma-4-E2B-it-litert-lm` |
| 저장 위치 | `context.filesDir` |
| 인증 | HuggingFace Bearer Token 필요 |

## 아키텍처

```
MainActivity
└── Gemma4ChatApp (Navigation)
    ├── SettingsScreen   → 시스템 프롬프트, 파라미터 설정
    ├── DownloadScreen   → HF 토큰 입력, 모델 다운로드
    └── ChatScreen       → 스트리밍 채팅 UI
         └── ChatViewModel
              └── InferenceModel (singleton)
                   └── Engine (LiteRT-LM)
                        └── Conversation (대화 히스토리 유지)
```

## LiteRT-LM API 핵심
```kotlin
// 엔진 초기화 (느림 — IO 스레드에서 실행)
val engine = Engine(EngineConfig(modelPath = "...", backend = Backend.GPU()))
engine.initialize()

// 대화 세션 (히스토리 유지됨)
val conversation = engine.createConversation(
    ConversationConfig(systemInstruction = Contents.of("..."))
)

// 스트리밍 생성 (Flow)
conversation.sendMessageAsync("메시지")
    .collect { chunk -> /* 토큰 단위로 수신 */ }

// 정리
conversation.close()
engine.close()
```

## 주요 설계 결정

### 스레드 처리
- 다운로드: `withContext(Dispatchers.IO)` — OkHttp 블로킹 호출
- 콜백(onProgress, onFinished): `scope.launch` — Compose 상태 업데이트는 메인 스레드 필요
- 모델 로드: `Dispatchers.IO` — `engine.initialize()` 블로킹
- 생성 중단: `generationJob.cancel()` — Flow 수집 코루틴 취소

### GPU/CPU 폴백
GPU 초기화 실패 시 자동으로 CPU로 전환:
```kotlin
engine = try {
    Engine(EngineConfig(modelPath, Backend.GPU())).also { it.initialize() }
} catch (e: Exception) {
    Engine(EngineConfig(modelPath, Backend.CPU())).also { it.initialize() }
}
```

### Conversation 히스토리
`Conversation` 객체가 멀티턴 히스토리를 자동 관리.
Reset 버튼 또는 시스템 프롬프트 변경 시 `conversation.close()` 후 새로 생성.

## 알려진 이슈 / 주의사항
- `litertlm-android`가 Kotlin 2.3.0 메타데이터로 컴파일되어 있어 `-Xskip-metadata-version-check` 필수
- 모델 초기화 최대 수십 초 소요 (기기 성능에 따라 상이)
- `engine.initialize()`는 반드시 IO/백그라운드 스레드에서 호출
- NavController 조작은 반드시 메인 스레드에서 수행
