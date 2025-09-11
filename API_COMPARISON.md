# Android vs iOS SDK API Comparison for React Native

## 1. SDK Initialization

### Android
```kotlin
// Initialize
AdchainSdk.init(application, AdchainSdkConfig("YOUR_APP_KEY"))

// Login
AdchainSdk.login(
    user = AdchainSdkUser(
        userId = "user123",
        gender = AdchainSdkUser.Gender.MALE,
        birthYear = 1990
    ),
    listener = object : AdchainSdkLoginListener {
        override fun onSuccess() { }
        override fun onFailure(error: AdchainLoginError) { }
    }
)
```

### iOS
```swift
// Initialize
AdchainSdk.shared.initialize(
    config: AdchainSdkConfig(appKey: "YOUR_APP_KEY")
)

// Set root view controller (iOS specific requirement)
AdchainSdk.setRootViewController(viewController)

// Login
AdchainSdk.shared.login(
    user: AdchainSdkUser(
        userId: "user123",
        gender: .male,
        birthYear: 1990
    ),
    listener: loginListener
)
```

**Key Differences:**
- ✅ Both use same initialization pattern
- ⚠️ iOS requires `setRootViewController()` for WebView presentation
- ✅ Login API is identical

---

## 2. Quiz Module

### Android
```kotlin
val quiz = AdchainQuiz(unitId = "QUIZ_UNIT_001")

// Get quiz list
quiz.getQuizList(
    onSuccess = { quizEvents -> },
    onFailure = { error -> }
)

// Click quiz
quiz.clickQuiz(quizId = "quiz_123")
```

### iOS
```swift
let quiz = AdchainQuiz(unitId: "QUIZ_UNIT_001")

// Get quiz list
quiz.getQuizList(
    onSuccess: { quizEvents in },
    onFailure: { error in }
)

// Click quiz
quiz.clickQuiz(quizId: "quiz_123")
```

**Key Differences:**
- ✅ Identical API signatures
- ✅ Same callback pattern
- ✅ Auto-refresh after completion works same way

---

## 3. Mission Module

### Android
```kotlin
val mission = AdchainMission(unitId = "MISSION_UNIT_001")

// Get mission list
mission.getMissionList(
    onSuccess = { missions -> },
    onFailure = { error -> }
)

// Get mission status
mission.getMissionStatus(
    onSuccess = { status -> 
        // status.current, status.total, status.isCompleted
    },
    onFailure = { error -> }
)

// Click mission
mission.clickMission(missionId = "mission_123")

// Get reward
mission.clickGetReward()
```

### iOS
```swift
let mission = AdchainMission(unitId: "MISSION_UNIT_001")

// Get mission list
mission.getMissionList(
    onSuccess: { missions in },
    onFailure: { error in }
)

// Get mission status
mission.getMissionStatus(
    onSuccess: { status in 
        // status.current, status.total, status.isCompleted
    },
    onFailure: { error in }
)

// Click mission
mission.clickMission(missionId: "mission_123")

// Get reward
mission.clickGetReward()
```

**Key Differences:**
- ✅ Identical API signatures
- ✅ Same data structures (MissionStatus)
- ✅ Same callback pattern

---

## 4. Offerwall Module

### Android
```kotlin
val offerwall = AdchainOfferwall(unitId = "OFFERWALL_UNIT_001")

// Open offerwall
offerwall.open(
    callback = object : OfferwallCallback {
        override fun onOpened() { }
        override fun onClosed() { }
        override fun onError(message: String) { }
        override fun onRewardEarned(amount: Int) { }
    }
)
```

### iOS
```swift
let offerwall = AdchainOfferwall(unitId: "OFFERWALL_UNIT_001")

// Open offerwall
offerwall.open(
    callback: offerwallCallback
)

// Callback protocol
protocol OfferwallCallback {
    func onOpened()
    func onClosed()
    func onError(_ message: String)
    func onRewardEarned(_ amount: Int)
}
```

**Key Differences:**
- ✅ Same open() method
- ✅ Identical callback methods
- ✅ WebView handling is internal

---

## 5. Data Models

### QuizEvent
| Field | Android Type | iOS Type | RN Bridge |
|-------|-------------|----------|----------|
| id | String | String | ✅ |
| title | String | String | ✅ |
| description | String? | String? | ✅ |
| point | String | String | ✅ |
| image_url | String | String | ✅ |
| landing_url | String | String | ✅ |
| status | String? | String? | ✅ |
| completed | Boolean? | Bool? | ✅ |

### Mission
| Field | Android Type | iOS Type | RN Bridge |
|-------|-------------|----------|----------|
| id | String | String | ✅ |
| title | String | String | ✅ |
| description | String | String | ✅ |
| image_url | String | String | ✅ |
| landing_url | String | String | ✅ |
| point | String | String | ✅ |
| type | MissionType | MissionType | ✅ |

### MissionStatus
| Field | Android Type | iOS Type | RN Bridge |
|-------|-------------|----------|----------|
| current | Int | Int | ✅ |
| total | Int | Int | ✅ |
| isCompleted | Boolean | Bool | ✅ |
| canClaimReward | Boolean | Bool | ✅ |

---

## 6. Error Types

### Android (AdchainAdError)
- NOT_INITIALIZED
- INVALID_UNIT_ID
- LOAD_FAILED
- UNKNOWN

### iOS (AdchainAdError)
- notInitialized
- invalidUnitId
- loadFailed
- unknown

**Key Differences:**
- ✅ Same error cases
- ⚠️ Different naming convention (UPPER_CASE vs camelCase)
- ✅ Can be mapped in RN bridge

---

## 7. React Native Bridge Considerations

### Platform-Specific Requirements

**Android:**
```javascript
// No special requirements
AdchainSdk.init(appKey);
```

**iOS:**
```javascript
// Need to pass root view controller
AdchainSdk.init(appKey);
AdchainSdk.setRootViewController(); // iOS only
```

### Recommended Bridge Implementation

```javascript
// Unified API for React Native
class AdchainSDK {
  static async init(appKey) {
    if (Platform.OS === 'ios') {
      // Automatically get and set root view controller
      await NativeModules.AdchainSDK.initWithViewController(appKey);
    } else {
      await NativeModules.AdchainSDK.init(appKey);
    }
  }
  
  static async getQuizList(unitId) {
    // Returns same structure on both platforms
    return NativeModules.AdchainQuiz.getQuizList(unitId);
  }
  
  static clickQuiz(quizId) {
    // Same API on both platforms
    NativeModules.AdchainQuiz.clickQuiz(quizId);
  }
}
```

---

## Summary

### ✅ Fully Compatible APIs:
1. Quiz module - All methods identical
2. Mission module - All methods identical
3. Offerwall module - All methods identical
4. Data structures - Same fields and types
5. Callback patterns - Consistent across platforms

### ⚠️ Minor Differences to Handle:
1. **iOS Root ViewController** - Need to set for WebView presentation
2. **Error naming** - UPPER_CASE (Android) vs camelCase (iOS)
3. **Initialization** - iOS needs UIViewController reference

### 🎯 React Native Integration:
- **99% compatible** - APIs are virtually identical
- Bridge layer can easily abstract the 1% differences
- Single JavaScript API can work for both platforms
- No need for platform-specific code in RN app

### Recommended Bridge Module Structure:
```
react-native-adchain-sdk/
├── index.js                 # Unified JS API
├── android/
│   └── AdchainBridge.kt    # Android native module
└── ios/
    └── AdchainBridge.swift # iOS native module
```

The SDKs are **highly compatible** for React Native usage with minimal platform-specific handling needed.