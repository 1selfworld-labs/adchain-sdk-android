# Android SDK 35 WebView Inset 처리 가이드

## 개요
Android SDK 35 (Android 15)부터 Edge-to-Edge 디스플레이가 기본으로 적용되어, WebView 콘텐츠가 시스템 네비게이션 바에 가려지는 문제가 발생할 수 있습니다.

AdChain SDK는 이 문제를 자체적으로 해결하도록 업데이트되었으나, 부모앱의 설정에 따라 추가 조치가 필요할 수 있습니다.

## SDK 변경사항 요약

### 1. InsetHelper 유틸리티 추가
- 3단계 폴백 전략으로 네비게이션 바 높이 감지 및 적용
- 부모앱 설정과 독립적으로 동작

### 2. 독립 테마 적용
- SDK Activity가 자체 테마 사용 (`AdchainSDKTheme`)
- 투명 시스템 바 설정으로 Edge-to-Edge 지원

### 3. 자동 하단 패딩 적용
- WebView 콘텐츠가 네비게이션 바에 가려지지 않도록 자동 조정

## 부모앱에서 확인 사항

### 1. 권장 설정 (변경 불필요)
대부분의 경우 SDK가 자체적으로 처리하므로 부모앱 변경이 필요 없습니다.

### 2. 문제가 지속되는 경우

#### 옵션 A: SafeArea 활성화 (React Native)
```typescript
// SafeAreaProvider.tsx 수정
export const SafeAreaProvider = ({children}: {children: React.ReactNode}) => {
  // Android에서도 SafeArea 활성화
  const RealSafeArea = require('react-native-safe-area-context').SafeAreaProvider;
  return <RealSafeArea>{children}</RealSafeArea>;
};
```

#### 옵션 B: 임시 Edge-to-Edge 비활성화 (권장하지 않음)
```xml
<!-- res/values/styles.xml -->
<style name="AppTheme" parent="Theme.AppCompat.Light.NoActionBar">
    <!-- Android 15 Edge-to-Edge 임시 비활성화 -->
    <!-- 주의: 향후 Android 버전에서 작동 중단 예정 -->
    <item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>
</style>
```

**⚠️ 경고**: `windowOptOutEdgeToEdgeEnforcement`는 임시 해결책이며, 향후 Android 버전에서 제거될 예정입니다.

#### 옵션 C: MainActivity에서 WindowInsets 처리
```kotlin
// MainActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Edge-to-Edge 명시적 활성화
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Root view에 insets 리스너 추가
    val rootView = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        // 인셋을 소비하지 않고 전달
        insets
    }
}
```

## 테스트 체크리스트

### 필수 테스트
- [ ] Android 15 (API 35) 디바이스에서 테스트
- [ ] 3버튼 네비게이션 모드
- [ ] 제스처 네비게이션 모드
- [ ] WebView 내 입력 필드 + 키보드 표시
- [ ] 가로/세로 모드 전환 (SDK는 세로 고정)

### 확인 사항
1. WebView 하단 콘텐츠(버튼, 링크)가 클릭 가능한가?
2. 네비게이션 바에 콘텐츠가 가려지지 않는가?
3. 키보드 표시 시 입력 필드가 보이는가?

## 로그 확인

SDK는 InsetHelper를 통해 디버그 로그를 출력합니다:

```bash
adb logcat | grep InsetHelper
```

출력 예시:
```
D/InsetHelper: Stage 1 - Insets listener: bottom=144
D/InsetHelper: Stage 2 - Root insets: bottom=144
D/InsetHelper: Stage 3 - Resource fallback: navBarHeight=144
```

- Stage 1이 성공하면: 정상 (부모앱이 인셋을 제대로 전달)
- Stage 2가 사용되면: 부모앱이 인셋을 소비했지만 SDK가 복구
- Stage 3이 사용되면: 폴백 모드 (제스처 네비게이션에서는 0일 수 있음)

## FAQ

### Q: SDK 업데이트 후에도 문제가 지속됩니다
A: 부모앱의 캐시를 클리어하고 clean build를 수행하세요:
```bash
cd android
./gradlew clean
./gradlew assembleDebug
```

### Q: React Native 0.73+ 버전에서 문제가 있습니다
A: React Native 0.73부터 자체 Edge-to-Edge 처리가 변경되었습니다. 옵션 A (SafeArea 활성화)를 적용하세요.

### Q: 제스처 네비게이션에서만 문제가 있습니다
A: 제스처 네비게이션은 네비게이션 바 높이가 0이거나 매우 작을 수 있습니다. SDK는 이를 감지하여 최소 패딩을 적용합니다.

## 지원

문제가 지속되는 경우:
1. 디바이스 정보 (모델, Android 버전)
2. 부모앱 설정 (targetSdkVersion, 테마)
3. 로그캣 출력 (InsetHelper 관련)

위 정보와 함께 이슈를 보고해주세요.