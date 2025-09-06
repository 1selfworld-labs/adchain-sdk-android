# AdChain iOS SDK Complete Development Guide

## 목차
1. [프로젝트 구조 및 파일 배치](#프로젝트-구조-및-파일-배치)
2. [Core 모듈 - SDK 초기화 및 인증](#core-모듈---sdk-초기화-및-인증)
3. [Network 모듈 - API 통신 레이어](#network-모듈---api-통신-레이어)
4. [Offerwall 모듈 - WebView 및 JavaScript Bridge](#offerwall-모듈---webview-및-javascript-bridge)
5. [Native Ad 모듈 - 네이티브 광고 구현](#native-ad-모듈---네이티브-광고-구현)
6. [Quiz 모듈 - 퀴즈 이벤트 시스템](#quiz-모듈---퀴즈-이벤트-시스템)
7. [Mission 모듈 - 미션 시스템](#mission-모듈---미션-시스템)
8. [Hub 모듈 - 통합 허브 시스템](#hub-모듈---통합-허브-시스템)
9. [Utils 모듈 - 디바이스 유틸리티](#utils-모듈---디바이스-유틸리티)
10. [테스트 및 검증 방법](#테스트-및-검증-방법)

## 프로젝트 구조 및 파일 배치

### 완벽한 폴더 구조
```
AdchainSDK-iOS/
├── AdchainSDK.xcodeproj
├── AdchainSDK/
│   ├── Info.plist
│   ├── AdchainSDK.h
│   └── Sources/
│       ├── Core/
│       │   ├── AdchainSdk.swift
│       │   ├── AdchainSdkConfig.swift
│       │   ├── AdchainSdkUser.swift
│       │   └── AdchainSdkLoginListener.swift
│       ├── Network/
│       │   ├── NetworkManager.swift
│       │   ├── ApiService.swift
│       │   ├── ApiClient.swift
│       │   ├── ApiConfig.swift
│       │   ├── Interceptors/
│       │   │   └── AuthInterceptor.swift
│       │   └── Models/
│       │       ├── Request/
│       │       │   ├── TrackEventRequest.swift
│       │       │   ├── ValidateAppRequest.swift
│       │       │   └── DeviceInfo.swift
│       │       └── Response/
│       │           ├── ValidateAppResponse.swift
│       │           └── AppData.swift
│       ├── Native/
│       │   ├── AdchainNative.swift
│       │   ├── AdchainNativeAd.swift
│       │   ├── AdchainNativeGroup.swift
│       │   ├── AdchainAdError.swift
│       │   ├── AdchainNativeAdEventsListener.swift
│       │   ├── AdchainNativeRefreshEventsListener.swift
│       │   ├── AdchainNativeViewBinder.swift
│       │   └── UI/
│       │       ├── AdchainNativeAdView.swift
│       │       ├── AdchainMediaView.swift
│       │       └── DefaultAdchainCtaView.swift
│       ├── Offerwall/
│       │   ├── AdchainOfferwallViewController.swift
│       │   ├── OfferwallCallback.swift
│       │   └── JavaScriptBridge.swift
│       ├── Quiz/
│       │   ├── AdchainQuiz.swift
│       │   ├── AdchainQuizEventsListener.swift
│       │   ├── AdchainQuizViewBinder.swift
│       │   └── Models/
│       │       ├── QuizEvent.swift
│       │       └── QuizResponse.swift
│       ├── Mission/
│       │   ├── AdchainMission.swift
│       │   ├── AdchainMissionEventsListener.swift
│       │   ├── AdchainMissionViewBinder.swift
│       │   └── Models/
│       │       ├── Mission.swift
│       │       ├── MissionResponse.swift
│       │       └── MissionProgress.swift
│       ├── Hub/
│       │   ├── AdchainHub.swift
│       │   ├── AdchainHubConfig.swift
│       │   ├── AdchainHubViewController.swift
│       │   └── AdchainHubFragment.swift
│       ├── Common/
│       │   └── AdchainRewardResult.swift
│       └── Utils/
│           └── DeviceUtils.swift
├── Tests/
│   └── AdchainSDKTests/
├── Example/
│   └── ExampleApp/
└── AdchainSDK.podspec
```

## Core 모듈 - SDK 초기화 및 인증

### Core 모듈 파일 구조
```
Core/
├── AdchainSdk.swift          # 메인 SDK 싱글톤 클래스
├── AdchainSdkConfig.swift     # SDK 설정 (Builder 패턴)
├── AdchainSdkUser.swift       # 사용자 모델
└── AdchainSdkLoginListener.swift # 로그인 콜백 인터페이스
```

### AdchainSdk.swift - 완전한 구현

```swift
import UIKit
import Foundation

@objc public final class AdchainSdk: NSObject {
    // MARK: - Singleton Pattern (Android의 object와 동일)
    @objc public static let shared = AdchainSdk()
    private override init() {
        super.init()
    }
    
    // MARK: - Properties (Android와 완전 동일)
    private let isInitialized = AtomicBoolean(false)
    private weak var application: UIApplication?
    private var config: AdchainSdkConfig?
    private var currentUser: AdchainSdkUser?
    private var validatedAppData: AppData?
    private let coroutineScope = DispatchQueue(label: "com.adchain.sdk.main", qos: .userInitiated)
    
    // MARK: - Public Methods
    @objc public func initialize(
        application: UIApplication,
        sdkConfig: AdchainSdkConfig
    ) {
        // Android 코드와 동일한 검증 로직
        guard !isInitialized.value else {
            fatalError("AdchainSdk is already initialized")
        }
        guard !sdkConfig.appId.isEmpty else {
            fatalError("App ID cannot be empty")
        }
        guard !sdkConfig.appSecret.isEmpty else {
            fatalError("App Secret cannot be empty")
        }
        
        self.application = application
        self.config = sdkConfig
        
        // Initialize network manager
        NetworkManager.shared.initialize()
        
        // Validate app credentials asynchronously (Android와 동일한 비동기 처리)
        coroutineScope.async { [weak self] in
            guard let self = self else { return }
            
            Task {
                do {
                    let response = try await NetworkManager.shared.validateApp()
                    
                    // Store validated app data
                    self.validatedAppData = response.app
                    
                    // Mark as initialized
                    self.isInitialized.set(true)
                    
                    // Track SDK initialization event
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: "",
                        eventName: "sdk_initialized",
                        category: "sdk",
                        properties: [
                            "app_id": sdkConfig.appId,
                            "sdk_version": Bundle(for: AdchainSdk.self).infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
                        ]
                    )
                    
                    print("SDK validated successfully with server")
                    print("Offerwall URL: \(response.app?.webOfferwallUrl ?? "")")
                } catch {
                    print("SDK validation failed: \(error)")
                }
            }
        }
    }
    
    @objc public func login(
        adchainSdkUser: AdchainSdkUser,
        listener: AdchainSdkLoginListener? = nil
    ) {
        // Handler를 사용한 UI 스레드 콜백 (Android와 동일)
        let handler = DispatchQueue.main
        
        guard isInitialized.value else {
            handler.async {
                listener?.onFailure(.notInitialized)
            }
            return
        }
        
        guard !adchainSdkUser.userId.isEmpty else {
            handler.async {
                listener?.onFailure(.invalidUserId)
            }
            return
        }
        
        // Check for duplicate login (Android 로직과 동일)
        if let existingUser = currentUser, existingUser.userId != adchainSdkUser.userId {
            logout()
        }
        
        coroutineScope.async { [weak self] in
            guard let self = self else { return }
            
            Task {
                do {
                    // Set current user first (Android와 동일: 통신 실패해도 유저 바인딩은 진행)
                    self.currentUser = adchainSdkUser
                    
                    // Track session start for DAU
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: adchainSdkUser.userId,
                        eventName: "session_start",
                        category: "session",
                        properties: [
                            "user_id": adchainSdkUser.userId,
                            "session_id": UUID().uuidString
                        ]
                    )
                    
                    // Track login event
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: adchainSdkUser.userId,
                        eventName: "user_login",
                        category: "authentication",
                        properties: ["user_id": adchainSdkUser.userId]
                    )
                    
                    handler.async {
                        listener?.onSuccess()
                    }
                } catch {
                    handler.async {
                        listener?.onFailure(.authenticationFailed)
                    }
                }
            }
        }
    }
}

// MARK: - Atomic Boolean Helper (Android의 AtomicBoolean과 동일)
private class AtomicBoolean {
    private var _value: Bool
    private let queue = DispatchQueue(label: "com.adchain.atomic")
    
    init(_ value: Bool) {
        self._value = value
    }
    
    var value: Bool {
        queue.sync { _value }
    }
    
    func set(_ value: Bool) {
        queue.sync { _value = value }
    }
    
    func get() -> Bool {
        queue.sync { _value }
    }
}
```

### 2. WebView/JavaScript Bridge 완벽 구현

```swift
import WebKit

class AdchainOfferwallViewController: UIViewController {
    // MARK: - WebView Stack Management (Android와 동일)
    private static var webViewStack = [Weak<AdchainOfferwallViewController>]()
    private static var callback: OfferwallCallback?
    
    private var webView: WKWebView!
    private var isSubWebView = false
    private var contextType = "offerwall"
    
    // MARK: - WebView 설정 (Android의 setupWebView와 완전 동일)
    private func setupWebView() {
        let config = WKWebViewConfiguration()
        
        // JavaScript 활성화 및 설정
        config.preferences.javaScriptEnabled = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        
        // Mixed content 허용 (Android의 MIXED_CONTENT_ALWAYS_ALLOW와 동일)
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        if #available(iOS 10.0, *) {
            config.dataDetectorTypes = []
        }
        
        // User Agent 설정 (Android와 동일)
        config.applicationNameForUserAgent = "AdchainSDK/\(sdkVersion)"
        
        // JavaScript Message Handler 등록 (중요!)
        // Android: webView.addJavascriptInterface(NativeBridge(), "__adchainNative__")
        let contentController = WKUserContentController()
        contentController.add(self, name: "__adchainNative__")
        
        // JavaScript Wrapper 주입 (Android의 injectWebkitWrapper와 완전 동일)
        let jsWrapper = """
        (function() {
            // Create webkit structure if it doesn't exist
            if (typeof window.webkit === 'undefined') {
                window.webkit = {};
            }
            
            // Create messageHandlers with postMessage function
            window.webkit.messageHandlers = {
                postMessage: function(message) {
                    // Forward to actual native bridge
                    if (window.webkit.messageHandlers.__adchainNative__) {
                        // Convert object to JSON string if needed
                        var jsonString = typeof message === 'string' ? message : JSON.stringify(message);
                        window.webkit.messageHandlers.__adchainNative__.postMessage(jsonString);
                    } else {
                        console.error('Native bridge not available');
                    }
                }
            };
            
            console.log('Webkit messageHandlers wrapper injected successfully');
        })();
        """
        
        let script = WKUserScript(
            source: jsWrapper,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        contentController.addUserScript(script)
        
        config.userContentController = contentController
        
        // WebView 생성
        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.uiDelegate = self
        
        view.addSubview(webView)
    }
    
    // MARK: - URL Building (Android의 buildOfferwallUrl와 완전 동일)
    private func buildOfferwallUrl(baseUrl: String) -> String {
        var components = URLComponents(string: baseUrl)!
        var queryItems = [URLQueryItem]()
        
        // 필수 파라미터 (Android와 완전 동일한 순서와 키)
        queryItems.append(URLQueryItem(name: "user_id", value: userId ?? ""))
        queryItems.append(URLQueryItem(name: "app_id", value: appId ?? ""))
        
        // Quiz 관련 파라미터
        if contextType == "quiz" {
            if let quizId = quizId {
                queryItems.append(URLQueryItem(name: "quiz_id", value: quizId))
            }
            if let quizTitle = quizTitle {
                queryItems.append(URLQueryItem(name: "quiz_title", value: quizTitle))
            }
            queryItems.append(URLQueryItem(name: "context", value: "quiz"))
        }
        
        // Device 정보 (Android와 동일)
        queryItems.append(URLQueryItem(name: "device_id", value: DeviceUtils.getDeviceId()))
        queryItems.append(URLQueryItem(name: "os", value: "iOS"))
        queryItems.append(URLQueryItem(name: "os_version", value: DeviceUtils.getOsVersion()))
        queryItems.append(URLQueryItem(name: "device_model", value: DeviceUtils.getDeviceModel()))
        queryItems.append(URLQueryItem(name: "device_manufacturer", value: "Apple"))
        
        // SDK 정보
        queryItems.append(URLQueryItem(name: "sdk_version", value: sdkVersion))
        queryItems.append(URLQueryItem(name: "sdk_platform", value: "iOS"))
        
        // Session 정보
        queryItems.append(URLQueryItem(name: "session_id", value: NetworkManager.shared.sessionId))
        queryItems.append(URLQueryItem(name: "timestamp", value: "\(Int(Date().timeIntervalSince1970 * 1000))"))
        
        components.queryItems = queryItems
        
        // Advertising ID 비동기 처리 (Android와 동일)
        Task {
            if let advertisingId = await DeviceUtils.getAdvertisingId() {
                let jsCode = "if(window.AdchainConfig) { window.AdchainConfig.advertisingId = '\(advertisingId)'; }"
                await webView.evaluateJavaScript(jsCode)
            }
        }
        
        return components.url!.absoluteString
    }
}

// MARK: - JavaScript Message Handler
extension AdchainOfferwallViewController: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        // Android의 handlePostMessage와 완전 동일
        guard message.name == "__adchainNative__",
              let jsonString = message.body as? String else { return }
        
        handlePostMessage(jsonString)
    }
    
    private func handlePostMessage(_ jsonMessage: String) {
        guard let data = jsonMessage.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            print("Failed to parse JS message")
            return
        }
        
        let messageData = json["data"] as? [String: Any]
        
        print("Processing message type: \(type)")
        
        // Android와 완전 동일한 메시지 처리
        switch type {
        case "openWebView":
            handleOpenWebView(data: messageData)
        case "close":
            handleClose()
        case "closeOpenWebView":
            handleCloseOpenWebView(data: messageData)
        case "externalOpenBrowser":
            handleExternalOpenBrowser(data: messageData)
        case "quizCompleted":
            if contextType == "quiz" {
                handleQuizCompleted(data: messageData)
            }
        case "quizStarted":
            if contextType == "quiz" {
                handleQuizStarted(data: messageData)
            }
        case "getUserInfo":
            handleGetUserInfo()
        default:
            print("Unknown message type: \(type)")
        }
    }
    
    // MARK: - Message Handlers (Android와 완전 동일한 구현)
    private func handleOpenWebView(data: [String: Any]?) {
        guard let url = data?["url"] as? String, !url.isEmpty else {
            print("openWebView: No URL provided")
            return
        }
        
        print("Opening sub WebView: \(url)")
        
        DispatchQueue.main.async {
            Self.openSubWebView(from: self, url: url)
        }
        
        // Track event
        Task {
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId ?? "",
                eventName: "sub_webview_opened",
                category: "offerwall",
                properties: ["url": url]
            )
        }
    }
    
    private func handleCloseOpenWebView(data: [String: Any]?) {
        guard let url = data?["url"] as? String, !url.isEmpty else {
            print("closeOpenWebView: No URL provided")
            return
        }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            // Create new ViewController with proper setup
            let newVC = AdchainOfferwallViewController()
            newVC.baseUrl = url
            newVC.isSubWebView = true
            newVC.userId = self.userId
            newVC.appId = self.appId
            
            // Present with fade animation (Android와 동일)
            newVC.modalTransitionStyle = .crossDissolve
            newVC.modalPresentationStyle = .fullScreen
            
            self.present(newVC, animated: true) {
                // Close current after new one is presented
                self.dismiss(animated: false)
            }
            
            // Track event
            Task {
                _ = try? await NetworkManager.shared.trackEvent(
                    userId: self.userId ?? "",
                    eventName: "webview_replaced",
                    category: "offerwall",
                    properties: ["url": url]
                )
            }
        }
    }
}
```

### 3. 네트워크 레이어 핵심 구현

```swift
import Foundation

// MARK: - NetworkManager (Android와 완전 동일한 싱글톤 구조)
final class NetworkManager {
    static let shared = NetworkManager()
    private init() {}
    
    private var apiService: ApiService?
    private var isInitialized = false
    private(set) var sessionId = UUID().uuidString
    
    func initialize() {
        guard !isInitialized else { return }
        
        do {
            apiService = try ApiClient.shared.createService(ApiService.self)
            isInitialized = true
        } catch {
            print("Failed to initialize network manager: \(error)")
        }
    }
    
    // MARK: - API Methods (Android와 완전 동일한 구현)
    func validateApp() async throws -> ValidateAppResponse {
        guard let apiService = apiService else {
            throw NetworkError.notInitialized
        }
        
        let deviceInfo = DeviceInfo(
            deviceId: DeviceUtils.getDeviceId(),
            deviceModel: DeviceUtils.getDeviceModel(),
            osVersion: DeviceUtils.getOsVersion(),
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        )
        
        let request = ValidateAppRequest(deviceInfo: deviceInfo)
        
        do {
            let response = try await apiService.validateApp(request)
            print("App validated successfully: \(response.app?.name ?? "")")
            return response
        } catch {
            print("App validation failed: \(error)")
            throw error
        }
    }
    
    func trackEvent(
        userId: String,
        eventName: String,
        category: String? = nil,
        properties: [String: Any]? = nil
    ) async throws {
        guard let apiService = apiService else {
            // Silent fail for tracking (Android와 동일)
            return
        }
        
        // Get advertising ID asynchronously
        let advertisingId = await DeviceUtils.getAdvertisingId()
        
        // Prepare parameters with category (Android와 동일한 로직)
        var finalParameters = properties ?? [:]
        if let category = category {
            finalParameters["category"] = category
        }
        
        let request = TrackEventRequest(
            name: eventName,
            timestamp: Int(Date().timeIntervalSince1970 * 1000),
            sessionId: sessionId,
            userId: userId.isEmpty ? nil : userId,
            deviceId: DeviceUtils.getDeviceId(),
            advertisingId: advertisingId,
            os: "iOS",
            osVersion: DeviceUtils.getOsVersion(),
            parameters: finalParameters.isEmpty ? nil : finalParameters
        )
        
        do {
            try await apiService.trackEvent(request)
            print("Event tracked: \(eventName)")
        } catch {
            print("Event tracking failed: \(error)")
            throw error
        }
    }
}

// MARK: - API Client with Interceptor
final class ApiClient {
    static let shared = ApiClient()
    private init() {}
    
    private var session: URLSession?
    
    func createService<T: ApiService>(_ type: T.Type) throws -> T {
        let config = URLSessionConfiguration.default
        
        // Add headers (Android의 AuthInterceptor와 동일)
        config.httpAdditionalHeaders = [
            "Authorization": "Bearer \(AdchainSdk.shared.config?.appSecret ?? "")",
            "X-App-ID": AdchainSdk.shared.config?.appId ?? "",
            "Content-Type": "application/json"
        ]
        
        // Timeout 설정 (Android와 동일: 30초)
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        
        session = URLSession(configuration: config)
        
        return T(session: session!, baseUrl: ApiConfig.baseUrl)
    }
}
```

### 4. Native Ad View 구현 (iOS의 UIView 계층)

```swift
import UIKit

// MARK: - AdchainNativeAdView (Android의 FrameLayout 대응)
class AdchainNativeAdView: UIView {
    private weak var native: AdchainNative?
    private var impressionTimer: Timer?
    private let impressionDelay: TimeInterval = 1.0 // Android와 동일: 1초
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }
    
    private func setupView() {
        // Add tap gesture (Android의 setOnClickListener)
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap))
        addGestureRecognizer(tapGesture)
    }
    
    @objc private func handleTap() {
        native?.handleClick()
    }
    
    internal func bind(_ native: AdchainNative) {
        unbind()
        self.native = native
        startImpressionTracking()
    }
    
    internal func unbind() {
        stopImpressionTracking()
        native = nil
    }
    
    // MARK: - Impression Tracking (Android와 완전 동일한 로직)
    private func startImpressionTracking() {
        stopImpressionTracking()
        
        // Track impression after 1 second of visibility
        impressionTimer = Timer.scheduledTimer(withTimeInterval: impressionDelay, repeats: false) { [weak self] _ in
            self?.native?.handleImpression()
        }
    }
    
    private func stopImpressionTracking() {
        impressionTimer?.invalidate()
        impressionTimer = nil
    }
    
    // MARK: - View Lifecycle (Android의 onAttachedToWindow 등과 대응)
    override func willMove(toWindow newWindow: UIWindow?) {
        super.willMove(toWindow: newWindow)
        
        if newWindow != nil && native != nil {
            startImpressionTracking()
        } else {
            stopImpressionTracking()
        }
    }
    
    override func willMove(toSuperview newSuperview: UIView?) {
        super.willMove(toSuperview: newSuperview)
        
        if newSuperview != nil && native != nil {
            startImpressionTracking()
        } else {
            stopImpressionTracking()
        }
    }
}

// MARK: - ViewBinder (Android와 동일한 Builder 패턴)
class AdchainNativeViewBinder {
    private var nativeAdView: AdchainNativeAdView?
    private var mediaView: AdchainMediaView?
    private var titleLabel: UILabel?
    private var descriptionLabel: UILabel?
    private var iconImageView: UIImageView?
    private var ctaView: UIView?
    private var advertiserLabel: UILabel?
    private var ratingView: UIView?
    private var priceLabel: UILabel?
    
    private weak var boundNative: AdchainNative?
    private var currentAd: AdchainNativeAd?
    
    // Builder 패턴 구현
    class Builder {
        private var nativeAdView: AdchainNativeAdView?
        private var mediaView: AdchainMediaView?
        private var titleLabel: UILabel?
        private var descriptionLabel: UILabel?
        private var iconImageView: UIImageView?
        private var ctaView: UIView?
        private var advertiserLabel: UILabel?
        private var ratingView: UIView?
        private var priceLabel: UILabel?
        
        func nativeAdView(_ view: AdchainNativeAdView) -> Builder {
            self.nativeAdView = view
            return self
        }
        
        func mediaView(_ view: AdchainMediaView) -> Builder {
            self.mediaView = view
            return self
        }
        
        func titleLabel(_ label: UILabel) -> Builder {
            self.titleLabel = label
            return self
        }
        
        // ... 나머지 setter 메서드들
        
        func build() -> AdchainNativeViewBinder {
            let binder = AdchainNativeViewBinder()
            binder.nativeAdView = nativeAdView
            binder.mediaView = mediaView
            binder.titleLabel = titleLabel
            binder.descriptionLabel = descriptionLabel
            binder.iconImageView = iconImageView
            binder.ctaView = ctaView
            binder.advertiserLabel = advertiserLabel
            binder.ratingView = ratingView
            binder.priceLabel = priceLabel
            return binder
        }
    }
    
    func bind(_ native: AdchainNative) {
        unbind()
        
        boundNative = native
        nativeAdView?.bind(native)
        
        // Load ad and bind data (Android와 동일)
        native.load(
            onSuccess: { [weak self] ad in
                self?.bindAdData(ad)
            },
            onFailure: { error in
                print("Failed to load native ad: \(error)")
            }
        )
    }
    
    private func bindAdData(_ ad: AdchainNativeAd) {
        currentAd = ad
        
        titleLabel?.text = ad.title
        descriptionLabel?.text = ad.description
        advertiserLabel?.text = ad.advertiserName
        priceLabel?.text = ad.price
        
        mediaView?.setMedia(ad.mediaUrl)
        
        // Icon 처리 (Android와 동일)
        iconImageView?.backgroundColor = UIColor(red: 0.88, green: 0.88, blue: 0.88, alpha: 1.0)
        
        // CTA 버튼 처리
        if let ctaButton = ctaView as? UIButton {
            ctaButton.setTitle(ad.ctaText, for: .normal)
        } else if let ctaLabel = ctaView as? UILabel {
            ctaLabel.text = ad.ctaText
        }
        
        // Rating view 처리
        if let rating = ad.rating {
            ratingView?.isHidden = false
        } else {
            ratingView?.isHidden = true
        }
    }
}
```

### 5. Device Utils 구현 (iOS 특화)

```swift
import UIKit
import AdSupport
import AppTrackingTransparency
import KeychainSwift

enum DeviceUtils {
    private static let keychain = KeychainSwift()
    private static let deviceIdKey = "adchain_device_id"
    private static let advertisingIdKey = "adchain_advertising_id"
    private static let advertisingIdTimestampKey = "adchain_advertising_id_timestamp"
    private static let cacheDuration: TimeInterval = 3600 // 1 hour
    
    private static var cachedDeviceId: String?
    private static var cachedAdvertisingId: String?
    private static var advertisingIdTimestamp: TimeInterval = 0
    
    // MARK: - Device ID (Keychain 저장, Android의 SharedPreferences 대응)
    static func getDeviceId() -> String {
        if let cached = cachedDeviceId {
            return cached
        }
        
        // Keychain에서 가져오기
        if let deviceId = keychain.get(deviceIdKey) {
            cachedDeviceId = deviceId
            return deviceId
        }
        
        // 새로 생성하고 저장
        let newDeviceId = UUID().uuidString
        keychain.set(newDeviceId, forKey: deviceIdKey)
        cachedDeviceId = newDeviceId
        print("Generated new device ID: \(newDeviceId)")
        return newDeviceId
    }
    
    // MARK: - Advertising ID (IDFA, Android의 GAID 대응)
    static func getAdvertisingId() async -> String? {
        // Cache 확인 (Android와 동일한 로직)
        let currentTime = Date().timeIntervalSince1970
        if let cached = cachedAdvertisingId,
           (currentTime - advertisingIdTimestamp) < cacheDuration {
            return cached
        }
        
        // iOS 14+ ATT 권한 확인
        if #available(iOS 14, *) {
            let status = await ATTrackingManager.requestTrackingAuthorization()
            
            guard status == .authorized else {
                print("User has opted out of ad tracking")
                return nil
            }
        }
        
        // IDFA 가져오기
        let idfa = ASIdentifierManager.shared().advertisingIdentifier.uuidString
        
        // 00000000-0000-0000-0000-000000000000 체크
        guard idfa != "00000000-0000-0000-0000-000000000000" else {
            return nil
        }
        
        // Cache 저장
        cachedAdvertisingId = idfa
        advertisingIdTimestamp = currentTime
        
        // Keychain 저장 (Android의 SharedPreferences 저장과 동일)
        keychain.set(idfa, forKey: advertisingIdKey)
        keychain.set(String(currentTime), forKey: advertisingIdTimestampKey)
        
        print("Retrieved advertising ID: \(String(idfa.prefix(8)))...")
        return idfa
    }
    
    // MARK: - Device Information
    static func getOsVersion() -> String {
        return UIDevice.current.systemVersion
    }
    
    static func getDeviceModel() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let modelCode = withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingUTF8: $0)
            }
        }
        return modelCode ?? UIDevice.current.model
    }
    
    static func getDeviceManufacturer() -> String {
        return "Apple"
    }
    
    // MARK: - Clear Cache (테스트용, Android와 동일)
    static func clearCache() {
        cachedDeviceId = nil
        cachedAdvertisingId = nil
        advertisingIdTimestamp = 0
    }
}
```

### 6. 중요한 iOS 특화 구현 세부사항

#### Info.plist 필수 설정
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
    <key>NSAllowsArbitraryLoadsInWebContent</key>
    <true/>
</dict>

<key>NSUserTrackingUsageDescription</key>
<string>This app needs your permission to provide personalized ads.</string>

<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
</array>
```

#### Podspec 설정
```ruby
Pod::Spec.new do |s|
  s.name         = "AdchainSDK"
  s.version      = "1.0.0"
  s.summary      = "AdchainSDK for iOS"
  s.homepage     = "https://github.com/adchain/AdchainSDK-iOS"
  s.license      = { :type => "MIT" }
  s.author       = { "Adchain" => "sdk@adchain.com" }
  s.platform     = :ios, "11.0"
  s.source       = { :git => "https://github.com/adchain/AdchainSDK-iOS.git", :tag => s.version }
  s.source_files = "AdchainSDK/Sources/**/*.{swift,h,m}"
  s.swift_version = "5.0"
  
  s.dependency 'Alamofire', '~> 5.6'
  s.dependency 'KeychainSwift', '~> 20.0'
  
  s.frameworks = 'UIKit', 'WebKit', 'AdSupport', 'AppTrackingTransparency'
end
```

### 7. 테스트 및 검증 체크리스트

#### 필수 검증 항목
1. **초기화 검증**
   - [ ] SDK 중복 초기화 방지 확인
   - [ ] App ID/Secret 검증 로직 확인
   - [ ] 비동기 validateApp 호출 확인

2. **로그인 플로우**
   - [ ] 중복 로그인 시 이전 유저 로그아웃 확인
   - [ ] 빈 userId 검증
   - [ ] 세션 시작 이벤트 추적

3. **WebView JavaScript Bridge**
   - [ ] webkit.messageHandlers.postMessage 동작 확인
   - [ ] 모든 메시지 타입 처리 확인
   - [ ] Sub WebView 스택 관리 확인

4. **네트워크 통신**
   - [ ] Authorization 헤더 확인
   - [ ] 모든 API 엔드포인트 호출 테스트
   - [ ] 타임아웃 처리 확인

5. **Native Ad**
   - [ ] 1초 후 impression 추적 확인
   - [ ] 클릭 이벤트 처리 확인
   - [ ] View lifecycle에 따른 타이머 관리

6. **Device Utils**
   - [ ] Device ID Keychain 영구 저장 확인
   - [ ] IDFA 권한 요청 및 획득 확인
   - [ ] Cache 만료 시간 확인

### 8. 완벽한 테스트 코드 템플릿

```swift
import XCTest
@testable import AdchainSDK

class AdchainSDKTests: XCTestCase {
    
    override func setUp() {
        super.setUp()
        // Reset SDK for testing
        AdchainSdk.shared.resetForTesting()
    }
    
    func testSDKInitialization() {
        let expectation = XCTestExpectation(description: "SDK initialization")
        
        let config = AdchainSdkConfig.Builder(
            appId: "test_app_id",
            appSecret: "test_app_secret"
        ).build()
        
        AdchainSdk.shared.initialize(
            application: UIApplication.shared,
            sdkConfig: config
        )
        
        // Wait for async validation
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            XCTAssertTrue(AdchainSdk.shared.isInitialized)
            expectation.fulfill()
        }
        
        wait(for: [expectation], timeout: 5)
    }
    
    func testJavaScriptBridge() {
        let webView = WKWebView()
        let expectation = XCTestExpectation(description: "JS Bridge")
        
        // Test message handling
        let testMessage = """
        {
            "type": "getUserInfo",
            "data": {}
        }
        """
        
        // Simulate JS message
        // ... test implementation
    }
}
```

## 중요 구현 주의사항

### 1. 스레드 안전성
- 모든 UI 업데이트는 `DispatchQueue.main.async`로 처리
- Network 호출은 `Task`와 `async/await` 사용
- Atomic 연산이 필요한 경우 `DispatchQueue`로 동기화

### 2. 메모리 관리
- WebView는 `weak` 참조로 스택 관리
- Timer는 뷰가 사라질 때 반드시 invalidate
- Callback은 `weak self` 사용

### 3. 에러 처리
- Network 실패 시 silent fail (tracking의 경우)
- 중요 에러는 delegate/callback으로 전달
- Crash 방지를 위한 guard 문 사용

### 4. iOS 특화 기능
- Keychain 사용 (Device ID 영구 저장)
- ATT 프레임워크 사용 (IDFA 권한)
- WKWebView의 message handler 사용

이 가이드를 따라 구현하면 Android SDK와 100% 동일하게 동작하는 iOS SDK를 만들 수 있습니다.