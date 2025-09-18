/**
 * AdChain SDK Offline Page JavaScript
 * Handles retry logic and native communication
 */

(function() {
    'use strict';

    // Configuration
    const CONFIG = {
        maxRetries: 3,
        retryDelay: 1000,
        messages: {
            ko: {
                retrying: '재시도 중...',
                retry: '다시 시도',
                maxRetriesReached: '최대 재시도 횟수에 도달했습니다.\n잠시 후 다시 시도해주세요.'
            },
            en: {
                retrying: 'Retrying...',
                retry: 'Retry',
                maxRetriesReached: 'Maximum retry attempts reached.\nPlease try again later.'
            }
        }
    };

    // State management
    let retryCount = 0;
    let isRetrying = false;
    const lang = navigator.language.startsWith('ko') ? 'ko' : 'en';

    // Native bridge communication
    function sendToNative(message) {
        const jsonString = typeof message === 'string' ? message : JSON.stringify(message);

        try {
            if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.adchainNative) {
                // iOS
                window.webkit.messageHandlers.adchainNative.postMessage(jsonString);
            } else if (window.__adchainNative__ && window.__adchainNative__.postMessage) {
                // Android
                window.__adchainNative__.postMessage(jsonString);
            } else {
                console.warn('Native bridge not available');
            }
        } catch (error) {
            console.error('Failed to send message to native:', error);
        }
    }

    // Retry handler
    window.handleRetry = async function() {
        if (isRetrying) return;

        const button = document.querySelector('.btn-primary');
        const buttonText = button.querySelector('.btn-text');

        if (retryCount >= CONFIG.maxRetries) {
            alert(CONFIG.messages[lang].maxRetriesReached);
            retryCount = 0; // Reset for next attempt
            return;
        }

        isRetrying = true;
        retryCount++;

        // Update UI
        button.classList.add('loading');
        buttonText.textContent = `${CONFIG.messages[lang].retrying} (${retryCount}/${CONFIG.maxRetries})`;

        // Check network status
        if (navigator.onLine) {
            // Try to reload the page
            setTimeout(() => {
                location.reload();
            }, CONFIG.retryDelay);
        } else {
            // Still offline
            setTimeout(() => {
                button.classList.remove('loading');
                buttonText.textContent = CONFIG.messages[lang].retry;
                isRetrying = false;

                // Show offline notification
                showNotification('아직 오프라인 상태입니다');
            }, CONFIG.retryDelay);
        }

        // Send retry event to native
        sendToNative({
            type: 'retryAttempt',
            attempt: retryCount,
            maxRetries: CONFIG.maxRetries
        });
    };

    // Close handler
    window.handleClose = function() {
        sendToNative({ type: 'close' });

        // Fallback if native bridge doesn't work
        if (window.history.length > 1) {
            window.history.back();
        }
    };

    // Show notification
    function showNotification(message) {
        // Create notification element
        const notification = document.createElement('div');
        notification.className = 'notification';
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            bottom: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0, 0, 0, 0.8);
            color: white;
            padding: 12px 24px;
            border-radius: 24px;
            font-size: 14px;
            z-index: 1000;
            animation: slideUp 0.3s ease-out;
        `;

        document.body.appendChild(notification);

        // Remove after 3 seconds
        setTimeout(() => {
            notification.style.animation = 'slideDown 0.3s ease-out';
            setTimeout(() => {
                notification.remove();
            }, 300);
        }, 3000);
    }

    // Add CSS animations dynamically
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideUp {
            from {
                opacity: 0;
                transform: translate(-50%, 20px);
            }
            to {
                opacity: 1;
                transform: translate(-50%, 0);
            }
        }
        @keyframes slideDown {
            from {
                opacity: 1;
                transform: translate(-50%, 0);
            }
            to {
                opacity: 0;
                transform: translate(-50%, 20px);
            }
        }
    `;
    document.head.appendChild(style);

    // Listen for online/offline events
    window.addEventListener('online', function() {
        console.log('Network connection restored');
        showNotification('네트워크 연결됨');
        // Auto reload disabled - user must manually retry
    });

    window.addEventListener('offline', function() {
        console.log('Network connection lost');
        showNotification('네트워크 연결 끊김');
    });

    // Initialize
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Offline page initialized');

        // Send page loaded event to native
        sendToNative({
            type: 'offlinePageLoaded',
            timestamp: Date.now()
        });

        // Check if already online (rare case)
        if (navigator.onLine) {
            console.log('Already online, but not auto-reloading');
            // Auto reload disabled - show offline page for user action
        }
    });
})();