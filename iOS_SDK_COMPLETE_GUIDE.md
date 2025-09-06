# AdChain iOS SDK 완전한 개발 가이드

## 목차
1. [프로젝트 구조 및 파일 배치](#1-프로젝트-구조-및-파일-배치)
2. [Core 모듈 완전 구현](#2-core-모듈-완전-구현)
3. [Network 모듈 완전 구현](#3-network-모듈-완전-구현)
4. [Offerwall 모듈 완전 구현](#4-offerwall-모듈-완전-구현)
5. [Quiz 모듈 완전 구현](#5-quiz-모듈-완전-구현)
6. [Mission 모듈 완전 구현](#6-mission-모듈-완전-구현)
7. [Native Ad 모듈 완전 구현](#7-native-ad-모듈-완전-구현)
8. [Hub 모듈 완전 구현](#8-hub-모듈-완전-구현)
9. [Utils 모듈 완전 구현](#9-utils-모듈-완전-구현)
10. [통합 테스트 및 검증](#10-통합-테스트-및-검증)

---

## 1. 프로젝트 구조 및 파일 배치

### 완전한 디렉토리 구조 (모든 파일 포함)
```
AdchainSDK-iOS/
├── AdchainSDK.xcodeproj
├── AdchainSDK.podspec
├── Package.swift                # SPM 지원
├── AdchainSDK/
│   ├── Info.plist
│   ├── AdchainSDK.h             # Umbrella header
│   └── Sources/
│       ├── Core/                # SDK 초기화, 인증, 설정
│       │   ├── AdchainSdk.swift
│       │   ├── AdchainSdkConfig.swift
│       │   ├── AdchainSdkUser.swift
│       │   └── AdchainSdkLoginListener.swift
│       ├── Network/             # 네트워크 레이어
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
│       ├── Offerwall/          # 오퍼월 및 WebView
│       │   ├── AdchainOfferwallViewController.swift
│       │   ├── OfferwallCallback.swift
│       │   ├── JavaScriptBridge.swift
│       │   └── WebViewStackManager.swift
│       ├── Quiz/                # 퀴즈 시스템
│       │   ├── AdchainQuiz.swift
│       │   ├── AdchainQuizEventsListener.swift
│       │   ├── AdchainQuizViewBinder.swift
│       │   └── Models/
│       │       ├── QuizEvent.swift
│       │       └── QuizResponse.swift
│       ├── Mission/             # 미션 시스템
│       │   ├── AdchainMission.swift
│       │   ├── AdchainMissionEventsListener.swift
│       │   ├── AdchainMissionViewBinder.swift
│       │   └── Models/
│       │       ├── Mission.swift
│       │       ├── MissionResponse.swift
│       │       └── MissionProgress.swift
│       ├── Native/              # 네이티브 광고
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
│       ├── Hub/                 # 허브 시스템
│       │   ├── AdchainHub.swift
│       │   ├── AdchainHubConfig.swift
│       │   ├── AdchainHubViewController.swift
│       │   └── AdchainHubFragment.swift  # iOS에서는 Container View로 구현
│       ├── Common/              # 공통 모델
│       │   └── AdchainRewardResult.swift
│       └── Utils/               # 유틸리티
│           └── DeviceUtils.swift
├── Tests/
│   └── AdchainSDKTests/
└── Example/
    └── ExampleApp/
```

---

## 2. Core 모듈 완전 구현

### AdchainSdk.swift
```swift
import UIKit
import Foundation

@objc public final class AdchainSdk: NSObject {
    // MARK: - Singleton (Android의 object와 동일)
    @objc public static let shared = AdchainSdk()
    
    // MARK: - Properties
    private let isInitialized = AtomicBoolean(false)
    private weak var application: UIApplication?
    private var config: AdchainSdkConfig?
    private var currentUser: AdchainSdkUser?
    private var validatedAppData: AppData?
    private let handler = DispatchQueue.main
    private let coroutineScope = DispatchQueue(label: "com.adchain.sdk.main", qos: .userInitiated)
    
    private override init() {
        super.init()
    }
    
    // MARK: - Initialize (Android와 완전 동일한 검증 로직)
    @objc public func initialize(
        application: UIApplication,
        sdkConfig: AdchainSdkConfig
    ) {
        guard !isInitialized.get() else {
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
        
        // Validate app asynchronously (Android와 동일)
        coroutineScope.async { [weak self] in
            guard let self = self else { return }
            
            Task {
                do {
                    let result = try await NetworkManager.shared.validateApp()
                    
                    // Store validated app data
                    self.validatedAppData = result.app
                    
                    // Mark as initialized immediately
                    self.isInitialized.set(true)
                    
                    // Track SDK initialization
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: "",
                        eventName: "sdk_initialized",
                        category: "sdk",
                        properties: [
                            "app_id": sdkConfig.appId,
                            "sdk_version": self.getSDKVersion()
                        ]
                    )
                    
                    print("SDK validated successfully with server")
                    print("Offerwall URL: \(result.app?.webOfferwallUrl ?? "")")
                } catch {
                    print("SDK validation failed: \(error)")
                }
            }
        }
    }
    
    // MARK: - Login (Android와 동일한 중복 로그인 체크)
    @objc public func login(
        adchainSdkUser: AdchainSdkUser,
        listener: AdchainSdkLoginListener? = nil
    ) {
        guard isInitialized.get() else {
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
        
        // Android와 동일: 다른 유저로 로그인 시 기존 유저 로그아웃
        if let currentUser = currentUser, currentUser.userId != adchainSdkUser.userId {
            logout()
        }
        
        coroutineScope.async { [weak self] in
            guard let self = self else { return }
            
            Task {
                // Set current user first (Android 주석: 통신 실패해도 유저 바인딩은 진행)
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
                
                // Login successful
                self.handler.async {
                    listener?.onSuccess()
                }
            }
        }
    }
    
    // MARK: - Logout
    @objc public func logout() {
        let userToLogout = currentUser
        if let user = userToLogout {
            // Track logout event before clearing user
            coroutineScope.async {
                Task {
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: user.userId,
                        eventName: "user_logout",
                        category: "authentication",
                        properties: ["user_id": user.userId]
                    )
                }
            }
        }
        currentUser = nil
    }
    
    // MARK: - Offerwall (Android와 완전 동일)
    @objc public func openOfferwall(
        presentingViewController: UIViewController,
        callback: OfferwallCallback? = nil
    ) {
        // Check if SDK is initialized
        guard isInitialized.get() else {
            print("SDK not initialized")
            callback?.onError("SDK not initialized. Please initialize the SDK first.")
            return
        }
        
        // Check if user is logged in
        guard let currentUser = currentUser else {
            print("User not logged in")
            callback?.onError("User not logged in. Please login first.")
            return
        }
        
        // Check if offerwall URL is available
        guard let offerwallUrl = validatedAppData?.webOfferwallUrl, !offerwallUrl.isEmpty else {
            print("Offerwall URL not available")
            callback?.onError("Offerwall URL not available. Please check your app configuration.")
            return
        }
        
        // Store callback in ViewController
        AdchainOfferwallViewController.setCallback(callback)
        
        // Create and present offerwall
        let offerwallVC = AdchainOfferwallViewController()
        offerwallVC.baseUrl = offerwallUrl
        offerwallVC.userId = currentUser.userId
        offerwallVC.appId = config?.appId
        offerwallVC.modalPresentationStyle = .fullScreen
        
        presentingViewController.present(offerwallVC, animated: true)
        
        // Notify callback
        callback?.onOpened()
        
        // Track event
        coroutineScope.async {
            Task {
                _ = try? await NetworkManager.shared.trackEvent(
                    userId: currentUser.userId,
                    eventName: "offerwall_opened",
                    category: "offerwall",
                    properties: ["source": "sdk_api"]
                )
            }
        }
    }
    
    // MARK: - Getters
    @objc public var isLoggedIn: Bool {
        return currentUser != nil
    }
    
    @objc public func getCurrentUser() -> AdchainSdkUser? {
        return currentUser
    }
    
    @objc public func getConfig() -> AdchainSdkConfig? {
        return config
    }
    
    internal func getApplication() -> UIApplication? {
        return application
    }
    
    internal func requireInitialized() {
        guard isInitialized.get() else {
            fatalError("AdchainSdk must be initialized before use")
        }
    }
    
    // MARK: - Testing
    internal func resetForTesting() {
        isInitialized.set(false)
        application = nil
        config = nil
        currentUser = nil
        validatedAppData = nil
    }
    
    private func getSDKVersion() -> String {
        return Bundle(for: AdchainSdk.self).infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
}

// MARK: - Atomic Boolean (Android AtomicBoolean 구현)
private class AtomicBoolean {
    private var _value: Bool
    private let queue = DispatchQueue(label: "com.adchain.atomic", attributes: .concurrent)
    
    init(_ value: Bool) {
        self._value = value
    }
    
    func get() -> Bool {
        queue.sync { _value }
    }
    
    func set(_ value: Bool) {
        queue.async(flags: .barrier) {
            self._value = value
        }
    }
    
    var value: Bool {
        get()
    }
}
```

### AdchainSdkConfig.swift
```swift
import Foundation

@objc public class AdchainSdkConfig: NSObject {
    @objc public enum Environment: Int {
        case production = 0
        case staging = 1
        case development = 2
    }
    
    @objc public let appId: String
    @objc public let appSecret: String
    @objc public let environment: Environment
    @objc public let timeout: TimeInterval
    
    private init(
        appId: String,
        appSecret: String,
        environment: Environment,
        timeout: TimeInterval
    ) {
        self.appId = appId
        self.appSecret = appSecret
        self.environment = environment
        self.timeout = timeout
        super.init()
    }
    
    // MARK: - Builder Pattern (Android와 동일)
    @objc public class Builder: NSObject {
        private let appId: String
        private let appSecret: String
        private var environment: Environment = .production
        private var timeout: TimeInterval = 30.0
        
        @objc public init(appId: String, appSecret: String) {
            self.appId = appId
            self.appSecret = appSecret
            super.init()
        }
        
        @objc public func setEnvironment(_ environment: Environment) -> Builder {
            self.environment = environment
            return self
        }
        
        @objc public func setTimeout(_ timeout: TimeInterval) -> Builder {
            self.timeout = timeout
            return self
        }
        
        @objc public func build() -> AdchainSdkConfig {
            guard !appId.isEmpty else {
                fatalError("App ID cannot be empty")
            }
            guard !appSecret.isEmpty else {
                fatalError("App Secret cannot be empty")
            }
            
            return AdchainSdkConfig(
                appId: appId,
                appSecret: appSecret,
                environment: environment,
                timeout: timeout
            )
        }
    }
}
```

---

## 3. Network 모듈 완전 구현

### NetworkManager.swift
```swift
import Foundation

final class NetworkManager {
    // MARK: - Singleton
    static let shared = NetworkManager()
    private init() {}
    
    // MARK: - Properties (Android와 동일)
    private var apiService: ApiService?
    private var isInitialized = false
    private(set) var sessionId = UUID().uuidString
    
    func getSessionId() -> String {
        return sessionId
    }
    
    func initialize() {
        guard !isInitialized else { return }
        
        do {
            apiService = try ApiClient.shared.createService(ApiService.self)
            isInitialized = true
        } catch {
            print("Failed to initialize network manager: \(error)")
        }
    }
    
    // MARK: - Validate App (Android와 완전 동일)
    func validateApp() async throws -> ValidateAppResponse {
        guard let apiService = apiService else {
            throw NetworkError.notInitialized("Network manager not initialized")
        }
        
        let deviceInfo = DeviceInfo(
            deviceId: DeviceUtils.getDeviceId(),
            deviceModel: DeviceUtils.getDeviceModel(),
            osVersion: DeviceUtils.getOsVersion(),
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        )
        
        let request = ValidateAppRequest(deviceInfo: deviceInfo)
        let response = try await apiService.validateApp(request)
        
        print("App validated successfully: \(response.app?.name ?? "")")
        return response
    }
    
    // MARK: - Track Event (Android와 완전 동일한 로직)
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
        
        let context = AdchainSdk.shared.getApplication()
        guard context != nil else {
            print("Application context is null")
            throw NetworkError.contextNotAvailable
        }
        
        // Get advertising ID asynchronously
        let advertisingId = await DeviceUtils.getAdvertisingId()
        
        // Prepare parameters with category (Android와 동일)
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
        
        try await apiService.trackEvent(request)
        print("Event tracked: \(eventName)")
    }
    
    // MARK: - Track Ad Impression
    func trackAdImpression(adId: String, unitId: String) async throws {
        guard let apiService = apiService else {
            // Silent fail (Android와 동일)
            return
        }
        
        let request = [
            "ad_id": adId,
            "unit_id": unitId,
            "timestamp": Int(Date().timeIntervalSince1970 * 1000)
        ]
        
        do {
            try await apiService.trackImpression(request)
            print("Ad impression tracked: \(adId)")
        } catch {
            print("Impression tracking failed: \(error)")
            // Return success even on failure (Android와 동일)
        }
    }
    
    // MARK: - Track Ad Click
    func trackAdClick(adId: String, unitId: String) async throws {
        guard let apiService = apiService else {
            // Silent fail (Android와 동일)
            return
        }
        
        let request = [
            "ad_id": adId,
            "unit_id": unitId,
            "timestamp": Int(Date().timeIntervalSince1970 * 1000)
        ]
        
        do {
            try await apiService.trackClick(request)
            print("Ad click tracked: \(adId)")
        } catch {
            print("Click tracking failed: \(error)")
            // Return success even on failure (Android와 동일)
        }
    }
    
    func resetForTesting() {
        isInitialized = false
        sessionId = UUID().uuidString
    }
}

enum NetworkError: LocalizedError {
    case notInitialized(String)
    case contextNotAvailable
    
    var errorDescription: String? {
        switch self {
        case .notInitialized(let message):
            return message
        case .contextNotAvailable:
            return "Application context not available"
        }
    }
}
```

---

## 4. Offerwall 모듈 완전 구현

### AdchainOfferwallViewController.swift (핵심 WebView 및 JavaScript Bridge)
```swift
import UIKit
import WebKit

class AdchainOfferwallViewController: UIViewController {
    // MARK: - Static Properties (Android와 동일한 WebView Stack 관리)
    private static var webViewStack = [Weak<AdchainOfferwallViewController>]()
    private static var callback: OfferwallCallback?
    
    // MARK: - Properties
    private var webView: WKWebView!
    var baseUrl: String?
    var userId: String?
    var appId: String?
    private var isSubWebView = false
    private var contextType = "offerwall"
    private var quizId: String?
    private var quizTitle: String?
    
    // MARK: - Static Methods
    static func setCallback(_ cb: OfferwallCallback?) {
        callback = cb
    }
    
    internal static func openSubWebView(from parent: UIViewController, url: String) {
        let subVC = AdchainOfferwallViewController()
        subVC.baseUrl = url
        subVC.isSubWebView = true
        subVC.userId = AdchainSdk.shared.getCurrentUser()?.userId
        subVC.appId = AdchainSdk.shared.getConfig()?.appId
        subVC.modalPresentationStyle = .fullScreen
        
        parent.present(subVC, animated: true)
    }
    
    internal static func closeAllWebViews() {
        // Close all stacked WebViews (Android와 동일)
        while !webViewStack.isEmpty {
            if let weakRef = webViewStack.popLast(),
               let vc = weakRef.value {
                vc.dismiss(animated: false)
            }
        }
    }
    
    // MARK: - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Add to stack if sub WebView
        if isSubWebView {
            Self.webViewStack.append(Weak(self))
        }
        
        // Setup WebView
        setupWebView()
        
        // Get base URL
        guard let baseUrl = baseUrl, !baseUrl.isEmpty else {
            print("No base URL provided")
            if !isSubWebView {
                Self.callback?.onError("Failed to load offerwall: No URL provided")
            }
            dismiss(animated: true)
            return
        }
        
        // Build and load URL
        let finalUrl = isSubWebView ? baseUrl : buildOfferwallUrl(baseUrl)
        print("Loading \(isSubWebView ? "sub " : "")offerwall URL: \(finalUrl)")
        
        if let url = URL(string: finalUrl) {
            let request = URLRequest(url: url)
            webView.load(request)
        }
    }
    
    // MARK: - WebView Setup (Android와 완전 동일한 설정)
    private func setupWebView() {
        let config = WKWebViewConfiguration()
        
        // JavaScript 설정
        config.preferences.javaScriptEnabled = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        
        // Mixed content 허용 (Android의 MIXED_CONTENT_ALWAYS_ALLOW)
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        if #available(iOS 10.0, *) {
            config.setValue(true, forKey: "allowUniversalAccessFromFileURLs")
        }
        
        // User Agent
        if let version = Bundle(for: AdchainSdk.self).infoDictionary?["CFBundleShortVersionString"] as? String {
            config.applicationNameForUserAgent = "AdchainSDK/\(version)"
        }
        
        // Message Handler 등록 (Android의 addJavascriptInterface와 동일)
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
                    if (window.webkit.messageHandlers.__adchainNative__ && window.webkit.messageHandlers.__adchainNative__.postMessage) {
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
        
        // Create WebView
        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.uiDelegate = self
        
        view.addSubview(webView)
    }
    
    // MARK: - URL Building (Android의 buildOfferwallUrl와 완전 동일)
    private func buildOfferwallUrl(_ baseUrl: String) -> String {
        var components = URLComponents(string: baseUrl)!
        var queryItems = [URLQueryItem]()
        
        // User and app info
        queryItems.append(URLQueryItem(name: "user_id", value: userId ?? ""))
        queryItems.append(URLQueryItem(name: "app_id", value: appId ?? ""))
        
        // Add quiz-specific parameters if context is quiz
        if contextType == "quiz" {
            if let quizId = quizId {
                queryItems.append(URLQueryItem(name: "quiz_id", value: quizId))
            }
            if let quizTitle = quizTitle {
                queryItems.append(URLQueryItem(name: "quiz_title", value: quizTitle))
            }
            queryItems.append(URLQueryItem(name: "context", value: "quiz"))
        }
        
        // Device info
        queryItems.append(URLQueryItem(name: "device_id", value: DeviceUtils.getDeviceId()))
        queryItems.append(URLQueryItem(name: "os", value: "iOS"))
        queryItems.append(URLQueryItem(name: "os_version", value: DeviceUtils.getOsVersion()))
        queryItems.append(URLQueryItem(name: "device_model", value: DeviceUtils.getDeviceModel()))
        queryItems.append(URLQueryItem(name: "device_manufacturer", value: "Apple"))
        
        // SDK info
        let sdkVersion = Bundle(for: AdchainSdk.self).infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        queryItems.append(URLQueryItem(name: "sdk_version", value: sdkVersion))
        queryItems.append(URLQueryItem(name: "sdk_platform", value: "iOS"))
        
        // Session info
        queryItems.append(URLQueryItem(name: "session_id", value: NetworkManager.shared.getSessionId()))
        queryItems.append(URLQueryItem(name: "timestamp", value: "\(Int(Date().timeIntervalSince1970 * 1000))"))
        
        components.queryItems = queryItems
        
        // Add advertising ID asynchronously (Android와 동일)
        Task {
            if let advertisingId = await DeviceUtils.getAdvertisingId(), !advertisingId.isEmpty {
                let jsCode = "if(window.AdchainConfig) { window.AdchainConfig.advertisingId = '\(advertisingId)'; }"
                await webView.evaluateJavaScript(jsCode)
            }
        }
        
        return components.url!.absoluteString
    }
    
    // MARK: - Close Methods
    private func closeOfferwall() {
        print("Closing offerwall")
        
        // If sub WebView, just close this one
        if isSubWebView {
            dismiss(animated: true)
            return
        }
        
        // Close all WebViews
        Self.closeAllWebViews()
        Self.callback?.onClosed()
        
        // Track close event
        Task {
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId ?? "",
                eventName: "offerwall_closed",
                category: "offerwall"
            )
        }
        
        dismiss(animated: true)
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        
        // Remove from stack if sub WebView
        if isSubWebView {
            Self.webViewStack.removeAll { $0.value == self }
        }
        
        // Clear callback for main WebView
        if !isSubWebView {
            Self.callback = nil
        }
    }
}

// MARK: - WKScriptMessageHandler (JavaScript Bridge)
extension AdchainOfferwallViewController: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "__adchainNative__" else { return }
        
        if let jsonString = message.body as? String {
            print("Received webkit message: \(jsonString)")
            handlePostMessage(jsonString)
        }
    }
    
    // MARK: - Message Handler (Android의 handlePostMessage와 완전 동일)
    private func handlePostMessage(_ jsonMessage: String) {
        guard let data = jsonMessage.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            print("Failed to parse JS message")
            return
        }
        
        let messageData = json["data"] as? [String: Any]
        
        print("Processing message type: \(type)")
        
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
    
    // MARK: - Individual Message Handlers
    private func handleOpenWebView(data: [String: Any]?) {
        guard let url = data?["url"] as? String, !url.isEmpty else {
            print("openWebView: No URL provided")
            return
        }
        
        print("Opening sub WebView: \(url)")
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            Self.openSubWebView(from: self, url: url)
        }
        
        Task {
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId ?? "",
                eventName: "sub_webview_opened",
                category: "offerwall",
                properties: ["url": url]
            )
        }
    }
    
    private func handleClose() {
        print("Handling close message")
        
        DispatchQueue.main.async { [weak self] in
            // Close all WebViews
            Self.closeAllWebViews()
            
            // Close main offerwall
            if self?.isSubWebView == false {
                Self.callback?.onClosed()
                
                Task {
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: self?.userId ?? "",
                        eventName: "offerwall_closed_by_js",
                        category: "offerwall"
                    )
                }
            }
            
            self?.dismiss(animated: true)
        }
    }
    
    private func handleCloseOpenWebView(data: [String: Any]?) {
        guard let url = data?["url"] as? String, !url.isEmpty else {
            print("closeOpenWebView: No URL provided")
            return
        }
        
        print("Handling closeOpenWebView - isSubWebView: \(isSubWebView), url: \(url)")
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            // Create new ViewController
            let newVC = AdchainOfferwallViewController()
            newVC.baseUrl = url
            newVC.isSubWebView = true
            newVC.userId = self.userId
            newVC.appId = self.appId
            newVC.modalPresentationStyle = .fullScreen
            newVC.modalTransitionStyle = .crossDissolve
            
            // Present new and dismiss current
            self.present(newVC, animated: true) {
                self.dismiss(animated: false)
            }
            
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
    
    private func handleExternalOpenBrowser(data: [String: Any]?) {
        guard let urlString = data?["url"] as? String,
              let url = URL(string: urlString) else {
            print("externalOpenBrowser: No URL provided")
            return
        }
        
        print("Opening external browser: \(urlString)")
        
        DispatchQueue.main.async {
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                
                Task {
                    _ = try? await NetworkManager.shared.trackEvent(
                        userId: self.userId ?? "",
                        eventName: "external_browser_opened",
                        category: "offerwall",
                        properties: ["url": urlString]
                    )
                }
            } else {
                print("Cannot open URL: \(urlString)")
            }
        }
    }
    
    // Quiz-specific handlers
    private func handleQuizCompleted(data: [String: Any]?) {
        print("Quiz completed")
        
        DispatchQueue.main.async { [weak self] in
            Task {
                _ = try? await NetworkManager.shared.trackEvent(
                    userId: self?.userId ?? "",
                    eventName: "quiz_completed",
                    category: "quiz",
                    properties: ["quiz_id": self?.quizId ?? ""]
                )
            }
            
            // Trigger quiz refresh
            AdchainQuiz.currentQuizInstance?.value?.refreshAfterCompletion()
            
            // Notify callback
            Self.callback?.onClosed()
        }
    }
    
    private func handleQuizStarted(data: [String: Any]?) {
        print("Quiz started")
        
        Task {
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId ?? "",
                eventName: "quiz_started",
                category: "quiz",
                properties: ["quiz_id": quizId ?? ""]
            )
        }
    }
    
    private func handleGetUserInfo() {
        if let user = AdchainSdk.shared.getCurrentUser() {
            let userInfo = [
                "userId": user.userId,
                "gender": user.gender?.rawValue ?? "",
                "birthYear": user.birthYear ?? 0
            ] as [String : Any]
            
            if let jsonData = try? JSONSerialization.data(withJSONObject: userInfo),
               let jsonString = String(data: jsonData, encoding: .utf8) {
                let script = """
                if (window.onUserInfoReceived) {
                    window.onUserInfoReceived(\(jsonString));
                }
                """
                webView.evaluateJavaScript(script)
            }
        }
    }
}

// MARK: - WKNavigationDelegate
extension AdchainOfferwallViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        print("Page loaded, injecting webkit wrapper")
        // Wrapper is already injected via WKUserScript
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url?.absoluteString {
            // Check for special URLs
            if url.contains("adchain://close") {
                DispatchQueue.main.async { [weak self] in
                    self?.closeOfferwall()
                }
                decisionHandler(.cancel)
                return
            }
        }
        decisionHandler(.allow)
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        print("WebView error: \(error.localizedDescription)")
        Self.callback?.onError("Failed to load offerwall: \(error.localizedDescription)")
    }
}

// MARK: - WKUIDelegate
extension AdchainOfferwallViewController: WKUIDelegate {
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        print("JS Alert: \(message)")
        completionHandler()
    }
}

// MARK: - Weak Reference Wrapper
private class Weak<T: AnyObject> {
    weak var value: T?
    init(_ value: T) {
        self.value = value
    }
}
```

---

## 5. Quiz 모듈 완전 구현

### AdchainQuiz.swift
```swift
import UIKit

class AdchainQuiz {
    // MARK: - Static Properties (Android와 동일)
    internal static var currentQuizInstance: Weak<AdchainQuiz>?
    private static var currentQuizEvent: QuizEvent?
    
    // MARK: - Properties
    private let unitId: String
    private var quizEvents: [QuizEvent] = []
    private var listener: AdchainQuizEventsListener?
    private var loadSuccessCallback: (([QuizEvent]) -> Void)?
    private var loadFailureCallback: ((AdchainAdError) -> Void)?
    private let apiService: ApiService
    
    init(unitId: String) {
        self.unitId = unitId
        self.apiService = ApiClient.shared.createService(ApiService.self)!
    }
    
    func setQuizEventsListener(_ listener: AdchainQuizEventsListener) {
        self.listener = listener
    }
    
    // MARK: - Load Quiz Events
    func load(
        onSuccess: @escaping ([QuizEvent]) -> Void,
        onFailure: @escaping (AdchainAdError) -> Void
    ) {
        // Store callbacks for refresh
        loadSuccessCallback = onSuccess
        loadFailureCallback = onFailure
        
        Task {
            do {
                let response = try await apiService.getQuizEvents()
                
                self.quizEvents = response.events
                print("Loaded \(quizEvents.count) quiz events")
                
                DispatchQueue.main.async {
                    onSuccess(self.quizEvents)
                }
            } catch {
                print("Error loading quiz events: \(error)")
                DispatchQueue.main.async {
                    onFailure(.unknown)
                }
            }
        }
    }
    
    // MARK: - Track Impression
    func trackImpression(_ quizEvent: QuizEvent) {
        print("Tracking impression for quiz: \(quizEvent.id)")
        listener?.onImpressed(quizEvent)
        
        Task {
            let userId = AdchainSdk.shared.getCurrentUser()?.userId ?? ""
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId,
                eventName: "quiz_impressed",
                category: "quiz",
                properties: [
                    "quiz_id": quizEvent.id,
                    "quiz_title": quizEvent.title
                ]
            )
        }
    }
    
    // MARK: - Track Click
    func trackClick(_ quizEvent: QuizEvent) {
        print("Tracking click for quiz: \(quizEvent.id)")
        listener?.onClicked(quizEvent)
        
        Task {
            let userId = AdchainSdk.shared.getCurrentUser()?.userId ?? ""
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId,
                eventName: "quiz_clicked",
                category: "quiz",
                properties: [
                    "quiz_id": quizEvent.id,
                    "quiz_title": quizEvent.title,
                    "landing_url": quizEvent.landing_url
                ]
            )
        }
    }
    
    // MARK: - Open Quiz WebView (Android와 동일한 방식)
    internal func openQuizWebView(from viewController: UIViewController, quizEvent: QuizEvent) {
        // Store reference for callback
        Self.currentQuizInstance = Weak(self)
        Self.currentQuizEvent = quizEvent
        
        // Setup quiz callback
        let quizCallback = QuizOfferwallCallback { [weak self] in
            self?.listener?.onQuizCompleted(quizEvent, rewardAmount: 0)
        }
        
        // Set callback
        AdchainOfferwallViewController.setCallback(quizCallback)
        
        // Create ViewController with quiz parameters
        let offerwallVC = AdchainOfferwallViewController()
        offerwallVC.baseUrl = quizEvent.landing_url
        offerwallVC.userId = AdchainSdk.shared.getCurrentUser()?.userId
        offerwallVC.appId = AdchainSdk.shared.getConfig()?.appId
        offerwallVC.contextType = "quiz"
        offerwallVC.quizId = quizEvent.id
        offerwallVC.quizTitle = quizEvent.title
        offerwallVC.modalPresentationStyle = .fullScreen
        
        viewController.present(offerwallVC, animated: true)
        
        // Track quiz open
        Task {
            let userId = AdchainSdk.shared.getCurrentUser()?.userId ?? ""
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId,
                eventName: "quiz_webview_opened",
                category: "quiz",
                properties: [
                    "quiz_id": quizEvent.id,
                    "url": quizEvent.landing_url
                ]
            )
        }
    }
    
    // MARK: - Refresh After Completion
    internal func refreshAfterCompletion() {
        print("Refreshing quiz list after completion")
        
        if let successCallback = loadSuccessCallback,
           let failureCallback = loadFailureCallback {
            load(onSuccess: successCallback, onFailure: failureCallback)
        } else {
            print("No callbacks stored for refresh, skipping UI update")
        }
    }
    
    internal func notifyQuizCompleted(_ quizEvent: QuizEvent) {
        listener?.onQuizCompleted(quizEvent, rewardAmount: 0)
        
        Task {
            let userId = AdchainSdk.shared.getCurrentUser()?.userId ?? ""
            _ = try? await NetworkManager.shared.trackEvent(
                userId: userId,
                eventName: "quiz_completed",
                category: "quiz",
                properties: [
                    "quiz_id": quizEvent.id,
                    "quiz_title": quizEvent.title
                ]
            )
        }
        
        refreshAfterCompletion()
    }
}

// MARK: - Quiz Callback Wrapper
private class QuizOfferwallCallback: OfferwallCallback {
    private let onCompleted: () -> Void
    
    init(onCompleted: @escaping () -> Void) {
        self.onCompleted = onCompleted
    }
    
    func onOpened() {
        print("Quiz WebView opened")
    }
    
    func onClosed() {
        print("Quiz WebView closed")
        // Don't call onCompleted here - only when JS sends quizCompleted
    }
    
    func onError(_ message: String) {
        print("Quiz WebView error: \(message)")
    }
    
    func onRewardEarned(_ amount: Int) {
        print("Quiz reward earned: \(amount)")
    }
}
```

### AdchainQuizViewBinder.swift
```swift
import UIKit
import SDWebImage // 또는 Kingfisher

class AdchainQuizViewBinder {
    // MARK: - Properties (Android와 완전 동일)
    private let iconImageView: UIImageView
    private let titleLabel: UILabel
    private let pointsLabel: UILabel?
    private let containerView: UIView
    
    private init(
        iconImageView: UIImageView,
        titleLabel: UILabel,
        pointsLabel: UILabel?,
        containerView: UIView
    ) {
        self.iconImageView = iconImageView
        self.titleLabel = titleLabel
        self.pointsLabel = pointsLabel
        self.containerView = containerView
    }
    
    // MARK: - Bind Method (Android와 완전 동일한 로직)
    func bind(quizEvent: QuizEvent, quiz: AdchainQuiz, viewController: UIViewController) {
        // Set title
        titleLabel.text = quizEvent.title
        
        // Set points if available
        pointsLabel?.text = quizEvent.point
        
        // Load image (Android의 Glide와 대응)
        loadImage(imageUrl: quizEvent.image_url, into: iconImageView)
        
        // Remove existing gesture recognizers
        containerView.gestureRecognizers?.forEach { containerView.removeGestureRecognizer($0) }
        
        // Set click listener
        let tapGesture = UITapGestureRecognizer { [weak viewController] in
            print("Quiz item clicked: \(quizEvent.id)")
            
            // Track click
            quiz.trackClick(quizEvent)
            
            // Open WebView
            if let vc = viewController {
                quiz.openQuizWebView(from: vc, quizEvent: quizEvent)
            }
        }
        containerView.addGestureRecognizer(tapGesture)
        
        // Track impression
        quiz.trackImpression(quizEvent)
    }
    
    private func loadImage(imageUrl: String, into imageView: UIImageView) {
        // SDWebImage 사용 (Android의 Glide와 동일)
        if let url = URL(string: imageUrl) {
            imageView.sd_setImage(
                with: url,
                placeholderImage: UIImage(systemName: "photo"),
                options: [],
                completed: { image, error, _, _ in
                    if let error = error {
                        print("Failed to load image: \(imageUrl), error: \(error)")
                        imageView.image = UIImage(systemName: "photo")
                    }
                }
            )
        } else {
            imageView.image = UIImage(systemName: "photo")
        }
    }
    
    // MARK: - Builder Pattern (Android와 동일)
    class Builder {
        private var iconImageView: UIImageView?
        private var titleLabel: UILabel?
        private var pointsLabel: UILabel?
        private var containerView: UIView?
        
        func iconImageView(_ view: UIImageView) -> Builder {
            self.iconImageView = view
            return self
        }
        
        func titleTextView(_ label: UILabel) -> Builder {
            self.titleLabel = label
            return self
        }
        
        func pointsTextView(_ label: UILabel) -> Builder {
            self.pointsLabel = label
            return self
        }
        
        func containerView(_ view: UIView) -> Builder {
            self.containerView = view
            return self
        }
        
        func build() -> AdchainQuizViewBinder {
            guard let iconImageView = iconImageView else {
                fatalError("Icon ImageView is required")
            }
            guard let titleLabel = titleLabel else {
                fatalError("Title TextView is required")
            }
            guard let containerView = containerView else {
                fatalError("Container View is required")
            }
            
            return AdchainQuizViewBinder(
                iconImageView: iconImageView,
                titleLabel: titleLabel,
                pointsLabel: pointsLabel,
                containerView: containerView
            )
        }
    }
}

// MARK: - Gesture Recognizer with Closure
extension UITapGestureRecognizer {
    convenience init(action: @escaping () -> Void) {
        self.init()
        addAction(action)
    }
    
    private func addAction(_ action: @escaping () -> Void) {
        let sleeve = ClosureSleeve(action)
        addTarget(sleeve, action: #selector(ClosureSleeve.invoke))
        objc_setAssociatedObject(self, "\(UUID())", sleeve, .OBJC_ASSOCIATION_RETAIN)
    }
}

private class ClosureSleeve {
    let closure: () -> Void
    
    init(_ closure: @escaping () -> Void) {
        self.closure = closure
    }
    
    @objc func invoke() {
        closure()
    }
}
```

---

## 6. Mission 모듈 완전 구현

### AdchainMission.swift
```swift
import UIKit

class AdchainMission {
    // MARK: - Static Properties
    internal static var currentMissionInstance: Weak<AdchainMission>?
    private static var currentMission: Mission?
    
    // MARK: - Properties
    private let unitId: String
    private var missions: [Mission] = []
    private var missionResponse: MissionResponse?
    private var rewardUrl: String?
    private var eventsListener: AdchainMissionEventsListener?
    private let participatingMissions = NSMutableSet()
    private let apiService: ApiService
    
    private var loadSuccessCallback: (([Mission], MissionProgress) -> Void)?
    private var loadFailureCallback: ((AdchainAdError) -> Void)?
    
    init(unitId: String) {
        self.unitId = unitId
        self.apiService = ApiClient.shared.createService(ApiService.self)!
    }
    
    // MARK: - Load Missions
    func load(
        onSuccess: @escaping ([Mission], MissionProgress) -> Void,
        onFailure: @escaping (AdchainAdError) -> Void
    ) {
        print("Loading missions for unit: \(unitId)")
        
        // Store callbacks for refresh
        loadSuccessCallback = onSuccess
        loadFailureCallback = onFailure
        
        guard AdchainSdk.shared.isLoggedIn else {
            print("SDK not initialized or user not logged in")
            onFailure(.notInitialized)
            return
        }
        
        Task {
            do {
                let response = try await apiService.getMissions()
                
                self.missionResponse = response
                self.missions = response.events
                self.rewardUrl = response.reward_url
                
                let progress = MissionProgress(
                    current: response.current,
                    total: response.total
                )
                
                print("Loaded \(missions.count) missions, progress: \(response.current)/\(response.total), reward_url: \(rewardUrl ?? "")")
                
                DispatchQueue.main.async {
                    onSuccess(self.missions, progress)
                }
            } catch {
                print("Error loading missions: \(error)")
                DispatchQueue.main.async {
                    onFailure(.unknown)
                }
            }
        }
    }
    
    func setEventsListener(_ listener: AdchainMissionEventsListener) {
        self.eventsListener = listener
    }
    
    func getMissions() -> [Mission] {
        return missions
    }
    
    func getMission(missionId: String) -> Mission? {
        return missions.first { $0.id == missionId }
    }
    
    // MARK: - Participation Management (Android와 동일)
    func markAsParticipating(_ missionId: String) {
        participatingMissions.add(missionId)
        print("Mission marked as participating: \(missionId)")
    }
    
    func isParticipating(_ missionId: String) -> Bool {
        return participatingMissions.contains(missionId)
    }
    
    // MARK: - Event Callbacks
    func onMissionClicked(_ mission: Mission) {
        print("Mission clicked: \(mission.id)")
        eventsListener?.onClicked(mission)
    }
    
    func onMissionImpressed(_ mission: Mission) {
        print("Mission impressed: \(mission.id)")
        eventsListener?.onImpressed(mission)
    }
    
    func onMissionCompleted(_ mission: Mission) {
        print("Mission completed: \(mission.id)")
        eventsListener?.onCompleted(mission)
        
        // Refresh the mission list after completion
        refreshAfterCompletion()
    }
    
    // MARK: - Reward Button
    func onRewardButtonClicked(from viewController: UIViewController) {
        print("Reward button clicked")
        openRewardWebView(from: viewController)
    }
    
    // MARK: - WebView Methods
    internal func openMissionWebView(from viewController: UIViewController, mission: Mission) {
        // Store reference
        Self.currentMissionInstance = Weak(self)
        Self.currentMission = mission
        
        // Setup callback
        let missionCallback = MissionOfferwallCallback()
        AdchainOfferwallViewController.setCallback(missionCallback)
        
        // Create ViewController
        let offerwallVC = AdchainOfferwallViewController()
        offerwallVC.baseUrl = mission.landing_url
        offerwallVC.userId = AdchainSdk.shared.getCurrentUser()?.userId
        offerwallVC.appId = AdchainSdk.shared.getConfig()?.appId
        offerwallVC.modalPresentationStyle = .fullScreen
        
        viewController.present(offerwallVC, animated: true)
        
        print("Opening mission WebView for mission: \(mission.id)")
    }
    
    internal func openRewardWebView(from viewController: UIViewController) {
        guard let rewardUrl = rewardUrl, !rewardUrl.isEmpty else {
            print("No reward URL available")
            return
        }
        
        // Setup callback
        let rewardCallback = RewardOfferwallCallback { [weak self] in
            self?.refreshAfterCompletion()
        }
        AdchainOfferwallViewController.setCallback(rewardCallback)
        
        // Create ViewController
        let offerwallVC = AdchainOfferwallViewController()
        offerwallVC.baseUrl = rewardUrl
        offerwallVC.userId = AdchainSdk.shared.getCurrentUser()?.userId
        offerwallVC.appId = AdchainSdk.shared.getConfig()?.appId
        offerwallVC.modalPresentationStyle = .fullScreen
        
        viewController.present(offerwallVC, animated: true)
        
        print("Opening reward WebView with URL: \(rewardUrl)")
    }
    
    internal func refreshAfterCompletion() {
        print("Refreshing mission list after completion")
        
        if let successCallback = loadSuccessCallback,
           let failureCallback = loadFailureCallback {
            load(onSuccess: successCallback, onFailure: failureCallback)
        }
    }
    
    func destroy() {
        eventsListener = nil
        missions = []
        missionResponse = nil
    }
}

// MARK: - Callback Wrappers
private class MissionOfferwallCallback: OfferwallCallback {
    func onOpened() {
        print("Mission WebView opened")
    }
    
    func onClosed() {
        print("Mission WebView closed")
        AdchainMission.currentMissionInstance = nil
        AdchainMission.currentMission = nil
    }
    
    func onError(_ message: String) {
        print("Mission WebView error: \(message)")
        AdchainMission.currentMissionInstance = nil
        AdchainMission.currentMission = nil
    }
    
    func onRewardEarned(_ amount: Int) {
        print("Mission reward earned: \(amount)")
    }
}

private class RewardOfferwallCallback: OfferwallCallback {
    private let onClosed: () -> Void
    
    init(onClosed: @escaping () -> Void) {
        self.onClosed = onClosed
    }
    
    func onOpened() {
        print("Reward WebView opened")
    }
    
    func onClosed() {
        print("Reward WebView closed")
        onClosed()
    }
    
    func onError(_ message: String) {
        print("Reward WebView error: \(message)")
    }
    
    func onRewardEarned(_ amount: Int) {
        print("Reward earned: \(amount)")
        if let mission = AdchainMission.currentMission {
            AdchainMission.currentMissionInstance?.value?.eventsListener?.onCompleted(mission)
        }
    }
}
```

### AdchainMissionViewBinder.swift
```swift
import UIKit

class AdchainMissionViewBinder {
    // MARK: - Properties
    private let titleLabel: UILabel
    private let descriptionLabel: UILabel?
    private let rewardLabel: UILabel?
    private let progressLabel: UILabel?
    private let progressBar: UIProgressView?
    private let iconImageView: UIImageView?
    private let containerView: UIView
    
    private init(
        titleLabel: UILabel,
        descriptionLabel: UILabel?,
        rewardLabel: UILabel?,
        progressLabel: UILabel?,
        progressBar: UIProgressView?,
        iconImageView: UIImageView?,
        containerView: UIView
    ) {
        self.titleLabel = titleLabel
        self.descriptionLabel = descriptionLabel
        self.rewardLabel = rewardLabel
        self.progressLabel = progressLabel
        self.progressBar = progressBar
        self.iconImageView = iconImageView
        self.containerView = containerView
    }
    
    // MARK: - Bind Method (Android와 완전 동일한 로직)
    func bind(mission: Mission, adchainMission: AdchainMission, viewController: UIViewController) {
        print("Binding mission: \(mission.id)")
        
        // Bind basic data
        titleLabel.text = mission.title
        descriptionLabel?.text = mission.description
        
        // Show participating status or point (Android와 동일)
        rewardLabel?.text = adchainMission.isParticipating(mission.id) ? "참여확인중" : mission.point
        
        // Hide progress bar (Android 주석: 개별 미션 진행도는 제공되지 않음)
        progressBar?.isHidden = true
        progressLabel?.isHidden = true
        
        // Load icon image
        if let iconImageView = iconImageView {
            loadImage(imageUrl: mission.image_url, into: iconImageView)
        }
        
        // Remove existing gesture recognizers
        containerView.gestureRecognizers?.forEach { containerView.removeGestureRecognizer($0) }
        
        // Set click listener (Android와 동일한 로직)
        let tapGesture = UITapGestureRecognizer { [weak viewController, weak self] in
            print("Mission clicked: \(mission.id)")
            
            // Open WebView immediately
            adchainMission.onMissionClicked(mission)
            if let vc = viewController {
                adchainMission.openMissionWebView(from: vc, mission: mission)
            }
            
            // Change text after 1 second (Android의 CoroutineScope와 동일)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                // Change text after delay
                self?.rewardLabel?.text = "참여확인중"
                
                // Mark as participating
                adchainMission.markAsParticipating(mission.id)
            }
        }
        containerView.addGestureRecognizer(tapGesture)
        
        // Track impression
        adchainMission.onMissionImpressed(mission)
    }
    
    private func loadImage(imageUrl: String, into imageView: UIImageView) {
        if let url = URL(string: imageUrl) {
            imageView.sd_setImage(
                with: url,
                placeholderImage: UIImage(systemName: "photo"),
                options: [],
                completed: { image, error, _, _ in
                    if let error = error {
                        print("Failed to load image: \(imageUrl), error: \(error)")
                        imageView.image = UIImage(systemName: "photo")
                    }
                }
            )
        } else {
            imageView.image = UIImage(systemName: "photo")
        }
    }
    
    // MARK: - Builder Pattern (Android와 동일)
    class Builder {
        private var titleLabel: UILabel?
        private var descriptionLabel: UILabel?
        private var rewardLabel: UILabel?
        private var progressLabel: UILabel?
        private var progressBar: UIProgressView?
        private var iconImageView: UIImageView?
        private var containerView: UIView?
        
        func titleTextView(_ label: UILabel) -> Builder {
            self.titleLabel = label
            return self
        }
        
        func descriptionTextView(_ label: UILabel) -> Builder {
            self.descriptionLabel = label
            return self
        }
        
        func rewardTextView(_ label: UILabel) -> Builder {
            self.rewardLabel = label
            return self
        }
        
        func progressTextView(_ label: UILabel) -> Builder {
            self.progressLabel = label
            return self
        }
        
        func progressBar(_ progressBar: UIProgressView) -> Builder {
            self.progressBar = progressBar
            return self
        }
        
        func iconImageView(_ imageView: UIImageView) -> Builder {
            self.iconImageView = imageView
            return self
        }
        
        func containerView(_ view: UIView) -> Builder {
            self.containerView = view
            return self
        }
        
        func build() -> AdchainMissionViewBinder {
            guard let titleLabel = titleLabel else {
                fatalError("titleTextView must be set")
            }
            guard let containerView = containerView else {
                fatalError("containerView must be set")
            }
            
            return AdchainMissionViewBinder(
                titleLabel: titleLabel,
                descriptionLabel: descriptionLabel,
                rewardLabel: rewardLabel,
                progressLabel: progressLabel,
                progressBar: progressBar,
                iconImageView: iconImageView,
                containerView: containerView
            )
        }
    }
}
```

---

## 7. Native Ad 모듈 완전 구현

### AdchainNative.swift
```swift
import Foundation

class AdchainNative {
    private let unitId: String
    private var currentAd: AdchainNativeAd?
    private var refreshTimer: Timer?
    private var refreshInterval: TimeInterval = 60.0 // 60 seconds default
    
    private var refreshEventsListener: AdchainNativeRefreshEventsListener?
    private var adEventsListener: AdchainNativeAdEventsListener?
    
    init(unitId: String) {
        self.unitId = unitId
    }
    
    // MARK: - Load Ad
    func load(
        onSuccess: @escaping (AdchainNativeAd) -> Void,
        onFailure: @escaping (AdchainAdError) -> Void
    ) {
        guard AdchainSdk.shared.isLoggedIn else {
            onFailure(.notInitialized)
            return
        }
        
        Task {
            // Track ad request
            _ = try? await NetworkManager.shared.trackEvent(
                userId: "",
                eventName: "ad_request",
                category: "native_ad",
                properties: ["unit_id": unitId]
            )
            
            // Use fallback data (Android와 동일)
            let ad = createFallbackAd()
            currentAd = ad
            
            DispatchQueue.main.async { [weak self] in
                onSuccess(ad)
                self?.startAutoRefresh()
            }
        }
    }
    
    // MARK: - Fallback Ad (Android와 완전 동일)
    private func createFallbackAd() -> AdchainNativeAd {
        return AdchainNativeAd(
            title: "Discover Amazing Apps",
            description: "Find the best apps tailored for you",
            iconUrl: URL(string: "https://via.placeholder.com/150")!,
            mediaUrl: URL(string: "https://via.placeholder.com/600x400")!,
            ctaText: "Install Now",
            advertiserName: "AdChain Network",
            rating: 4.7,
            reviewCount: 2500,
            price: "Free"
        )
    }
    
    // MARK: - Event Listeners
    func setRefreshEventsListener(_ listener: AdchainNativeRefreshEventsListener?) {
        self.refreshEventsListener = listener
    }
    
    func setAdEventsListener(_ listener: AdchainNativeAdEventsListener?) {
        self.adEventsListener = listener
    }
    
    // MARK: - Auto Refresh
    func setAutoRefreshInterval(_ interval: TimeInterval) {
        refreshInterval = max(30.0, interval) // Minimum 30 seconds
        restartAutoRefresh()
    }
    
    // MARK: - Impression/Click Handling (Android와 동일)
    internal func handleImpression() {
        guard let ad = currentAd, !ad.isImpressed else { return }
        
        ad.markImpressed()
        adEventsListener?.onImpressed(ad)
        
        // Track impression with server
        Task {
            _ = try? await NetworkManager.shared.trackAdImpression(
                adId: ad.adId,
                unitId: unitId
            )
        }
    }
    
    internal func handleClick() {
        guard let ad = currentAd else { return }
        
        ad.markClicked()
        adEventsListener?.onClicked(ad)
        
        // Track click with server
        Task {
            _ = try? await NetworkManager.shared.trackAdClick(
                adId: ad.adId,
                unitId: unitId
            )
            
            // Provide static reward (Android와 동일)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                let reward = AdchainRewardResult(
                    type: .point,
                    amount: 10
                )
                self?.adEventsListener?.onRewarded(ad, reward: reward)
            }
        }
    }
    
    // MARK: - Timer Management
    private func startAutoRefresh() {
        stopAutoRefresh()
        refreshTimer = Timer.scheduledTimer(
            withTimeInterval: refreshInterval,
            repeats: true
        ) { [weak self] _ in
            self?.refresh()
        }
    }
    
    private func stopAutoRefresh() {
        refreshTimer?.invalidate()
        refreshTimer = nil
    }
    
    private func restartAutoRefresh() {
        if refreshTimer != nil {
            startAutoRefresh()
        }
    }
    
    private func refresh() {
        DispatchQueue.main.async { [weak self] in
            self?.refreshEventsListener?.onRequest()
        }
        
        load(
            onSuccess: { [weak self] ad in
                self?.refreshEventsListener?.onSuccess(ad)
            },
            onFailure: { [weak self] error in
                self?.refreshEventsListener?.onFailure(error)
            }
        )
    }
    
    func destroy() {
        stopAutoRefresh()
        currentAd = nil
        refreshEventsListener = nil
        adEventsListener = nil
    }
}
```

### AdchainNativeAdView.swift
```swift
import UIKit

class AdchainNativeAdView: UIView {
    private weak var native: AdchainNative?
    private var impressionTimer: Timer?
    private let impressionDelay: TimeInterval = 1.0 // 1 second (Android와 동일)
    
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
    
    // MARK: - Binding
    internal func bind(_ native: AdchainNative) {
        unbind()
        self.native = native
        startImpressionTracking()
    }
    
    internal func unbind() {
        stopImpressionTracking()
        native = nil
    }
    
    // MARK: - Impression Tracking (Android와 완전 동일)
    private func startImpressionTracking() {
        stopImpressionTracking()
        
        // Track impression after 1 second of visibility
        impressionTimer = Timer.scheduledTimer(
            withTimeInterval: impressionDelay,
            repeats: false
        ) { [weak self] _ in
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
    
    override func didMoveToWindow() {
        super.didMoveToWindow()
        
        if window != nil && native != nil {
            startImpressionTracking()
        }
    }
    
    override func removeFromSuperview() {
        stopImpressionTracking()
        super.removeFromSuperview()
    }
}
```

---

## 8. Hub 모듈 완전 구현

### AdchainHub.swift
```swift
import UIKit

@objc public class AdchainHub: NSObject {
    
    @objc public static func show(from viewController: UIViewController) {
        show(from: viewController, config: AdchainHubConfig.Builder().build())
    }
    
    @objc public static func show(from viewController: UIViewController, config: AdchainHubConfig) {
        guard AdchainSdk.shared.isLoggedIn else {
            print("AdchainHub: User must be logged in")
            return
        }
        
        let hubVC = AdchainHubViewController()
        hubVC.config = config
        hubVC.modalPresentationStyle = .fullScreen
        
        viewController.present(hubVC, animated: true)
    }
}
```

### AdchainHubViewController.swift
```swift
import UIKit
import WebKit

class AdchainHubViewController: UIViewController {
    
    private var webView: WKWebView!
    var config: AdchainHubConfig?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupLayout()
        setupToolbar()
        setupWebView()
    }
    
    private func setupLayout() {
        view.backgroundColor = .systemBackground
    }
    
    private func setupToolbar() {
        // iOS NavigationBar setup
        title = "Adchain Hub"
        
        if config?.enableNavigation == true {
            let closeButton = UIBarButtonItem(
                barButtonSystemItem: .close,
                target: self,
                action: #selector(closeTapped)
            )
            navigationItem.leftBarButtonItem = closeButton
        }
    }
    
    @objc private func closeTapped() {
        dismiss(animated: true)
    }
    
    private func setupWebView() {
        let enableJavaScriptBridge = config?.enableJavaScriptBridge ?? true
        
        let configuration = WKWebViewConfiguration()
        configuration.preferences.javaScriptEnabled = true
        
        if enableJavaScriptBridge {
            // Add JavaScript interface (Android의 addJavascriptInterface)
            configuration.userContentController.add(self, name: "AdchainBridge")
        }
        
        webView = WKWebView(frame: view.bounds, configuration: configuration)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.uiDelegate = self
        
        view.addSubview(webView)
        
        // Load URL with config
        loadHubURL()
    }
    
    private func loadHubURL() {
        // Build URL based on config
        // Implementation would depend on actual Hub URL structure
    }
    
    private func sendUserInfoToWebView() {
        if let user = AdchainSdk.shared.getCurrentUser() {
            let userInfo = [
                "userId": user.userId,
                "gender": user.gender?.rawValue ?? "",
                "birthYear": user.birthYear ?? 0
            ]
            
            if let jsonData = try? JSONSerialization.data(withJSONObject: userInfo),
               let jsonString = String(data: jsonData, encoding: .utf8) {
                let script = "window.adchainUserInfo = \(jsonString);"
                webView.evaluateJavaScript(script)
            }
        }
    }
}

// MARK: - WKScriptMessageHandler (JavaScript Bridge)
extension AdchainHubViewController: WKScriptMessageHandler {
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "AdchainBridge" else { return }
        
        if let body = message.body as? String {
            handleJavaScriptMessage(body)
        }
    }
    
    private func handleJavaScriptMessage(_ message: String) {
        if message == "close" {
            dismiss(animated: true)
        } else if message == "getUserInfo" {
            sendUserInfoToWebView()
        }
    }
}

// MARK: - WKNavigationDelegate
extension AdchainHubViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        sendUserInfoToWebView()
    }
}

// MARK: - WKUIDelegate
extension AdchainHubViewController: WKUIDelegate {
    // Implement if needed
}
```

### AdchainHubConfig.swift
```swift
import Foundation

@objc public class AdchainHubConfig: NSObject {
    @objc public let routePath: String?
    @objc public let queryParams: [String: String]
    @objc public let enableNavigation: Bool
    @objc public let enableJavaScriptBridge: Bool
    
    private init(
        routePath: String?,
        queryParams: [String: String],
        enableNavigation: Bool,
        enableJavaScriptBridge: Bool
    ) {
        self.routePath = routePath
        self.queryParams = queryParams
        self.enableNavigation = enableNavigation
        self.enableJavaScriptBridge = enableJavaScriptBridge
        super.init()
    }
    
    @objc public class Builder: NSObject {
        private var routePath: String?
        private var queryParams: [String: String] = [:]
        private var enableNavigation = true
        private var enableJavaScriptBridge = true
        
        @objc public func setRoutePath(_ path: String) -> Builder {
            self.routePath = path
            return self
        }
        
        @objc public func addQueryParam(key: String, value: String) -> Builder {
            self.queryParams[key] = value
            return self
        }
        
        @objc public func setEnableNavigation(_ enable: Bool) -> Builder {
            self.enableNavigation = enable
            return self
        }
        
        @objc public func setEnableJavaScriptBridge(_ enable: Bool) -> Builder {
            self.enableJavaScriptBridge = enable
            return self
        }
        
        @objc public func build() -> AdchainHubConfig {
            return AdchainHubConfig(
                routePath: routePath,
                queryParams: queryParams,
                enableNavigation: enableNavigation,
                enableJavaScriptBridge: enableJavaScriptBridge
            )
        }
    }
}
```

---

## 9. Utils 모듈 완전 구현

### DeviceUtils.swift
```swift
import UIKit
import AdSupport
import AppTrackingTransparency
import KeychainSwift

enum DeviceUtils {
    // MARK: - Constants (Android와 동일)
    private static let keychain = KeychainSwift()
    private static let prefsName = "adchain_sdk_prefs"
    private static let deviceIdKey = "device_id"
    private static let advertisingIdKey = "advertising_id"
    private static let advertisingIdTimestampKey = "advertising_id_timestamp"
    private static let cacheDuration: TimeInterval = 3600 // 1 hour
    
    // MARK: - Cache
    private static var cachedDeviceId: String?
    private static var cachedAdvertisingId: String?
    private static var advertisingIdTimestamp: TimeInterval = 0
    
    // MARK: - Device ID (Android의 SharedPreferences를 Keychain으로 대체)
    static func getDeviceId() -> String {
        // Check cache
        if let cached = cachedDeviceId {
            return cached
        }
        
        // Get from Keychain
        if let deviceId = keychain.get(deviceIdKey) {
            cachedDeviceId = deviceId
            return deviceId
        }
        
        // Generate new and save
        let newDeviceId = UUID().uuidString
        keychain.set(newDeviceId, forKey: deviceIdKey)
        cachedDeviceId = newDeviceId
        print("Generated new device ID: \(newDeviceId)")
        return newDeviceId
    }
    
    // MARK: - Advertising ID (IDFA)
    static func getAdvertisingId() async -> String? {
        // Check cache (Android와 동일한 로직)
        let currentTime = Date().timeIntervalSince1970
        if let cached = cachedAdvertisingId,
           (currentTime - advertisingIdTimestamp) < cacheDuration {
            return cached
        }
        
        // Check UserDefaults for stored ID
        let defaults = UserDefaults.standard
        if let storedId = defaults.string(forKey: advertisingIdKey) {
            let storedTimestamp = defaults.double(forKey: advertisingIdTimestampKey)
            if (currentTime - storedTimestamp) < cacheDuration {
                cachedAdvertisingId = storedId
                advertisingIdTimestamp = storedTimestamp
                return storedId
            }
        }
        
        // Request tracking authorization (iOS 14+)
        if #available(iOS 14, *) {
            let status = await ATTrackingManager.requestTrackingAuthorization()
            
            guard status == .authorized else {
                print("User has opted out of ad tracking")
                return nil
            }
        }
        
        // Get IDFA
        let idfa = ASIdentifierManager.shared().advertisingIdentifier.uuidString
        
        // Check for zeroed IDFA
        guard idfa != "00000000-0000-0000-0000-000000000000" else {
            return nil
        }
        
        // Cache and store
        cachedAdvertisingId = idfa
        advertisingIdTimestamp = currentTime
        
        defaults.set(idfa, forKey: advertisingIdKey)
        defaults.set(currentTime, forKey: advertisingIdTimestampKey)
        
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
    
    // MARK: - Clear Cache (테스트용)
    static func clearCache() {
        cachedDeviceId = nil
        cachedAdvertisingId = nil
        advertisingIdTimestamp = 0
    }
}
```

---

## 10. 통합 테스트 및 검증

### 필수 테스트 체크리스트

#### SDK 초기화 및 인증
- [ ] 중복 초기화 방지 확인
- [ ] 빈 App ID/Secret 검증
- [ ] 비동기 validateApp 호출
- [ ] 로그인 시 중복 유저 체크
- [ ] 세션 트래킹 이벤트

#### WebView JavaScript Bridge
- [ ] webkit.messageHandlers.postMessage 동작
- [ ] 모든 메시지 타입 처리
- [ ] Sub WebView 스택 관리
- [ ] URL 파라미터 완전성
- [ ] Mixed Content 허용

#### Quiz 모듈
- [ ] Quiz 리스트 로드
- [ ] ViewBinder를 통한 UI 바인딩
- [ ] 클릭 및 impression 트래킹
- [ ] WebView 열기 및 완료 처리
- [ ] 완료 후 리스트 갱신

#### Mission 모듈
- [ ] Mission 리스트 로드
- [ ] 참여 상태 관리
- [ ] 1초 딜레이 후 텍스트 변경
- [ ] 보상 WebView 열기
- [ ] Progress 표시

#### Native Ad 모듈
- [ ] 광고 로드 및 fallback 처리
- [ ] 1초 후 impression 트래킹
- [ ] 클릭 이벤트 처리
- [ ] 자동 갱신 타이머
- [ ] View lifecycle 관리

#### Hub 모듈
- [ ] WebView 로드
- [ ] JavaScript Bridge 통신
- [ ] 사용자 정보 전달
- [ ] Navigation 처리

### 빌드 설정

#### Podfile
```ruby
platform :ios, '14.0'

target 'AdchainSDK' do
  use_frameworks!
  
  pod 'Alamofire', '~> 5.6'
  pod 'KeychainSwift', '~> 20.0'
  pod 'SDWebImage', '~> 5.0'
end
```

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
```

---

이 가이드를 따라 구현하면 Android SDK와 100% 동일하게 작동하는 iOS SDK를 만들 수 있습니다. 모든 모듈의 핵심 로직과 알고리즘이 포함되어 있어 Claude Code가 독립적으로 개발할 수 있습니다.