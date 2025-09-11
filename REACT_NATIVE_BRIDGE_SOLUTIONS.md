# React Native Bridge Solutions for SDK Differences

## 1. iOS Root ViewController 설정 문제 해결

### 문제 상황
iOS SDK는 WebView를 표시하기 위해 UIViewController 참조가 필요하지만, React Native 앱에서는 직접적인 ViewController 접근이 어렵습니다.

### 해결 방안 A: 브릿지에서 자동 획득 (권장) ✅

#### iOS Native Module (Objective-C)
```objc
// AdchainBridge.m
#import <React/RCTBridgeModule.h>
#import <React/RCTUtils.h>
@import AdchainSDK;

@interface AdchainBridge : NSObject <RCTBridgeModule>
@end

@implementation AdchainBridge

RCT_EXPORT_MODULE(AdchainSDK)

RCT_EXPORT_METHOD(initialize:(NSString *)appKey
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
  dispatch_async(dispatch_get_main_queue(), ^{
    // 자동으로 Root ViewController 획득
    UIViewController *rootViewController = RCTPresentedViewController();
    
    // SDK 초기화
    AdchainSdkConfig *config = [[AdchainSdkConfig alloc] initWithAppKey:appKey];
    [[AdchainSdk shared] initializeWithConfig:config];
    
    // Root ViewController 설정
    [AdchainSdk setRootViewController:rootViewController];
    
    resolve(@{@"success": @YES});
  });
}

@end
```

#### iOS Native Module (Swift)
```swift
// AdchainBridge.swift
import Foundation
import React
import AdchainSDK

@objc(AdchainBridge)
class AdchainBridge: NSObject, RCTBridgeModule {
  
  static func moduleName() -> String! {
    return "AdchainSDK"
  }
  
  @objc
  func initialize(_ appKey: String,
                  resolver: @escaping RCTPromiseResolveBlock,
                  rejecter: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      // React Native의 Helper 함수로 Root ViewController 자동 획득
      guard let rootViewController = RCTPresentedViewController() else {
        rejecter("NO_VIEW_CONTROLLER", "Could not find root view controller", nil)
        return
      }
      
      // SDK 초기화
      let config = AdchainSdkConfig(appKey: appKey)
      AdchainSdk.shared.initialize(config: config)
      
      // Root ViewController 설정
      AdchainSdk.setRootViewController(rootViewController)
      
      resolver(["success": true])
    }
  }
  
  static func requiresMainQueueSetup() -> Bool {
    return true
  }
}
```

#### JavaScript 사용
```javascript
// index.js
import { NativeModules, Platform } from 'react-native';

const { AdchainSDK } = NativeModules;

class AdchainSDKWrapper {
  static async init(appKey) {
    try {
      // iOS는 네이티브에서 자동으로 ViewController 설정
      // Android는 그대로 진행
      await AdchainSDK.initialize(appKey);
      console.log('SDK initialized successfully');
    } catch (error) {
      console.error('SDK initialization failed:', error);
    }
  }
}

export default AdchainSDKWrapper;
```

**장점:**
- ✅ JavaScript 코드가 단순함
- ✅ 플랫폼별 차이를 의식할 필요 없음
- ✅ React Native 표준 방식 (RCTPresentedViewController 사용)

**단점:**
- ❌ 네이티브 코드 수정 필요
- ❌ 특정 상황에서 ViewController를 못 찾을 수 있음

---

### 해결 방안 B: JavaScript에서 명시적 전달

#### iOS Native Module
```swift
@objc(AdchainBridge)
class AdchainBridge: NSObject, RCTBridgeModule {
  
  @objc
  func initialize(_ appKey: String,
                  resolver: @escaping RCTPromiseResolveBlock,
                  rejecter: @escaping RCTPromiseRejectBlock) {
    // ViewController 설정 없이 초기화만
    let config = AdchainSdkConfig(appKey: appKey)
    AdchainSdk.shared.initialize(config: config)
    resolver(["success": true])
  }
  
  @objc
  func setRootViewController(_ resolver: @escaping RCTPromiseResolveBlock,
                              rejecter: @escaping RCTPromiseRejectBlock) {
    DispatchQueue.main.async {
      guard let rootViewController = RCTPresentedViewController() else {
        rejecter("NO_VIEW_CONTROLLER", "Could not find root view controller", nil)
        return
      }
      
      AdchainSdk.setRootViewController(rootViewController)
      resolver(["success": true])
    }
  }
}
```

#### JavaScript 사용
```javascript
import { NativeModules, Platform } from 'react-native';

const { AdchainSDK } = NativeModules;

class AdchainSDKWrapper {
  static async init(appKey) {
    await AdchainSDK.initialize(appKey);
    
    // iOS만 추가 설정
    if (Platform.OS === 'ios') {
      await AdchainSDK.setRootViewController();
    }
  }
}
```

**장점:**
- ✅ 더 명시적이고 제어 가능
- ✅ 필요시 다른 ViewController 설정 가능

**단점:**
- ❌ JavaScript에서 플랫폼 분기 필요
- ❌ 개발자가 iOS 특수성을 알아야 함

---

### 해결 방안 C: SDK 자체 수정 (가장 깔끔) ⭐

#### iOS SDK 내부 수정
```swift
// AdchainSdk.swift
public class AdchainSdk {
    private static weak var rootViewController: UIViewController?
    
    internal static func getRootViewController() -> UIViewController? {
        // 1. 명시적으로 설정된 ViewController 사용
        if let vc = rootViewController {
            return vc
        }
        
        // 2. 자동으로 찾기 시도
        if let windowScene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene,
           let rootVC = windowScene.windows.first(where: { $0.isKeyWindow })?.rootViewController {
            return rootVC
        }
        
        // 3. Legacy 방식 (iOS 13 이전)
        if let rootVC = UIApplication.shared.keyWindow?.rootViewController {
            return rootVC
        }
        
        return nil
    }
    
    // setRootViewController는 optional로 변경
    public static func setRootViewController(_ viewController: UIViewController?) {
        rootViewController = viewController
    }
}
```

**JavaScript 사용 (플랫폼 차이 없음)**
```javascript
class AdchainSDKWrapper {
  static async init(appKey) {
    // 양 플랫폼 동일!
    await AdchainSDK.initialize(appKey);
  }
}
```

**장점:**
- ✅ React Native 브릿지 코드 최소화
- ✅ JavaScript에서 플랫폼 차이 없음
- ✅ 가장 깔끔한 솔루션

**단점:**
- ❌ SDK 소스 수정 필요
- ❌ 자동 탐색이 실패할 수 있음

---

## 2. 에러 네이밍 규칙 차이 해결

### 문제 상황
- Android: `AdchainAdError.NOT_INITIALIZED`
- iOS: `AdchainAdError.notInitialized`

### 해결 방안 A: 브릿지에서 통합 (권장) ✅

#### Android Native Module
```kotlin
// AdchainBridge.kt
class AdchainBridge(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    override fun getName() = "AdchainSDK"
    
    // 에러를 통합 포맷으로 변환
    private fun convertError(error: AdchainAdError): WritableMap {
        val errorMap = Arguments.createMap()
        
        // 통합 에러 코드 (camelCase)
        val unifiedCode = when(error) {
            AdchainAdError.NOT_INITIALIZED -> "notInitialized"
            AdchainAdError.INVALID_UNIT_ID -> "invalidUnitId"
            AdchainAdError.LOAD_FAILED -> "loadFailed"
            AdchainAdError.UNKNOWN -> "unknown"
        }
        
        errorMap.putString("code", unifiedCode)
        errorMap.putString("message", error.message)
        errorMap.putString("platform", "android")
        errorMap.putString("originalCode", error.name) // 원본 보존
        
        return errorMap
    }
    
    @ReactMethod
    fun getQuizList(unitId: String, promise: Promise) {
        val quiz = AdchainQuiz(unitId)
        
        quiz.getQuizList(
            onSuccess = { quizEvents ->
                val array = Arguments.createArray()
                quizEvents.forEach { event ->
                    val map = Arguments.createMap()
                    map.putString("id", event.id)
                    map.putString("title", event.title)
                    array.pushMap(map)
                }
                promise.resolve(array)
            },
            onFailure = { error ->
                // 통합 에러 포맷으로 변환
                promise.reject("QUIZ_LOAD_ERROR", convertError(error))
            }
        )
    }
}
```

#### iOS Native Module
```swift
// AdchainBridge.swift
@objc(AdchainBridge)
class AdchainBridge: NSObject, RCTBridgeModule {
    
    // 에러를 통합 포맷으로 변환
    private func convertError(_ error: AdchainAdError) -> [String: Any] {
        // 이미 camelCase이므로 그대로 사용
        let unifiedCode = switch error {
        case .notInitialized: "notInitialized"
        case .invalidUnitId: "invalidUnitId"
        case .loadFailed: "loadFailed"
        case .unknown: "unknown"
        }
        
        return [
            "code": unifiedCode,
            "message": error.description,
            "platform": "ios",
            "originalCode": String(describing: error)
        ]
    }
    
    @objc
    func getQuizList(_ unitId: String,
                     resolver: @escaping RCTPromiseResolveBlock,
                     rejecter: @escaping RCTPromiseRejectBlock) {
        let quiz = AdchainQuiz(unitId: unitId)
        
        quiz.getQuizList(
            onSuccess: { quizEvents in
                let events = quizEvents.map { event in
                    return [
                        "id": event.id,
                        "title": event.title
                    ]
                }
                resolver(events)
            },
            onFailure: { error in
                // 통합 에러 포맷으로 변환
                let errorDict = self.convertError(error)
                rejecter("QUIZ_LOAD_ERROR", errorDict["message"] as? String, nil)
            }
        )
    }
}
```

#### JavaScript에서 통합 에러 처리
```javascript
// AdchainError.js
export class AdchainError extends Error {
  constructor(nativeError) {
    super(nativeError.message);
    this.code = nativeError.code; // 통합된 camelCase
    this.platform = nativeError.platform;
    this.originalCode = nativeError.originalCode;
  }
  
  // 통합 에러 타입
  static ErrorCodes = {
    NOT_INITIALIZED: 'notInitialized',
    INVALID_UNIT_ID: 'invalidUnitId',
    LOAD_FAILED: 'loadFailed',
    UNKNOWN: 'unknown'
  };
  
  is(errorCode) {
    return this.code === errorCode;
  }
}

// 사용 예시
try {
  const quizList = await AdchainSDK.getQuizList('UNIT_001');
} catch (error) {
  const adError = new AdchainError(error);
  
  if (adError.is(AdchainError.ErrorCodes.NOT_INITIALIZED)) {
    console.log('SDK not initialized');
  } else if (adError.is(AdchainError.ErrorCodes.LOAD_FAILED)) {
    console.log('Failed to load quiz');
  }
}
```

**장점:**
- ✅ JavaScript에서 일관된 에러 처리
- ✅ 플랫폼 독립적인 에러 코드
- ✅ 원본 에러 정보도 보존

**단점:**
- ❌ 브릿지 코드 복잡도 증가
- ❌ 에러 변환 오버헤드

---

### 해결 방안 B: TypeScript 타입 정의로 추상화

```typescript
// types/adchain.d.ts
declare module 'react-native-adchain-sdk' {
  export enum AdchainErrorCode {
    NOT_INITIALIZED = 'notInitialized',
    INVALID_UNIT_ID = 'invalidUnitId', 
    LOAD_FAILED = 'loadFailed',
    UNKNOWN = 'unknown'
  }
  
  export interface AdchainError {
    code: AdchainErrorCode;
    message: string;
    platform: 'ios' | 'android';
  }
  
  export class AdchainSDK {
    static init(appKey: string): Promise<void>;
    static getQuizList(unitId: string): Promise<QuizEvent[]>;
  }
}
```

**장점:**
- ✅ 타입 안정성
- ✅ IDE 자동완성 지원

**단점:**
- ❌ 런타임 변환은 여전히 필요
- ❌ TypeScript 프로젝트에만 적용 가능

---

### 해결 방안 C: SDK 레벨에서 통일

#### Android SDK 수정
```kotlin
// AdchainAdError.kt
enum class AdchainAdError {
    notInitialized,  // camelCase로 변경
    invalidUnitId,
    loadFailed,
    unknown;
    
    // 하위 호환성을 위한 별칭
    companion object {
        @JvmField
        @Deprecated("Use notInitialized")
        val NOT_INITIALIZED = notInitialized
        
        @JvmField
        @Deprecated("Use invalidUnitId")
        val INVALID_UNIT_ID = invalidUnitId
    }
}
```

**장점:**
- ✅ 근본적 해결
- ✅ 브릿지 코드 단순

**단점:**
- ❌ 기존 Android 앱 호환성 문제
- ❌ SDK 수정 필요

---

## 최종 권장 아키텍처

### 1. 폴더 구조
```
react-native-adchain-sdk/
├── src/
│   ├── index.ts                 # Main API
│   ├── types.ts                 # TypeScript definitions
│   ├── errors.ts                # Error handling
│   └── platform/
│       ├── ios.ts               # iOS specific
│       └── android.ts           # Android specific
├── android/
│   └── src/main/java/
│       └── AdchainBridge.kt     # Android native module
└── ios/
    └── AdchainBridge.swift      # iOS native module
```

### 2. 통합 API 예시
```typescript
// src/index.ts
import { NativeModules, Platform } from 'react-native';
import { AdchainError } from './errors';

const { AdchainSDK: NativeSDK } = NativeModules;

export class AdchainSDK {
  private static initialized = false;
  
  static async init(appKey: string): Promise<void> {
    try {
      // 플랫폼 차이를 내부에서 처리
      if (Platform.OS === 'ios') {
        await NativeSDK.initializeWithViewController(appKey);
      } else {
        await NativeSDK.initialize(appKey);
      }
      this.initialized = true;
    } catch (error) {
      throw new AdchainError(error);
    }
  }
  
  static async getQuizList(unitId: string): Promise<QuizEvent[]> {
    if (!this.initialized) {
      throw new AdchainError({
        code: 'notInitialized',
        message: 'SDK not initialized'
      });
    }
    
    try {
      return await NativeSDK.getQuizList(unitId);
    } catch (error) {
      // 네이티브 에러를 통합 에러로 변환
      throw new AdchainError(error);
    }
  }
}
```

## 결론

### 최적 솔루션 조합:
1. **iOS ViewController**: 해결 방안 A (브릿지에서 자동 획득) + C (SDK 자체 개선)
2. **에러 네이밍**: 해결 방안 A (브릿지에서 통합)

이 조합이 가장 실용적이며:
- React Native 개발자에게 투명함
- 플랫폼 차이를 의식할 필요 없음
- 타입 안정성 제공
- 기존 네이티브 SDK와 호환 유지