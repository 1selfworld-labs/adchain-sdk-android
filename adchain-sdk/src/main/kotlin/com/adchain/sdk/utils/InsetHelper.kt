package com.adchain.sdk.utils

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Helper class for handling window insets in WebView to prevent content from being hidden
 * behind system bars (especially navigation bar).
 *
 * This uses a 3-stage fallback strategy:
 * 1. Standard insets listener
 * 2. Root window insets with ignoring visibility
 * 3. Resource-based navigation bar height
 */
object InsetHelper {
    private const val TAG = "InsetHelper"

    /**
     * Applies bottom inset to the target view with multiple fallback strategies.
     * This ensures the view content is not hidden behind the navigation bar.
     *
     * @param target The view to apply insets to (typically WebView)
     */
    fun applyBottomInsetWithFallback(target: View) {
        AdchainLogger.v(TAG, "=== Starting InsetHelper for view: ${target.javaClass.simpleName} ===")
        AdchainLogger.v(TAG, "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")

        var applied = false

        // Stage 1: Standard insets listener
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBarsInsets.bottom, imeInsets.bottom)

            AdchainLogger.v(TAG, "Stage 1 - Insets listener called")
            AdchainLogger.v(TAG, "  - systemBars: bottom=${systemBarsInsets.bottom}, top=${systemBarsInsets.top}")
            AdchainLogger.v(TAG, "  - ime: bottom=${imeInsets.bottom}")
            AdchainLogger.v(TAG, "  - final bottom=$bottomInset")

            if (bottomInset > 0) {
                applied = true
                AdchainLogger.v(TAG, "Stage 1 - Applying padding: $bottomInset")
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    bottomInset
                )
            } else {
                AdchainLogger.v(TAG, "Stage 1 - No insets to apply (bottom=0)")
            }

            // Return the insets without consuming them
            windowInsets
        }

        // Request insets when view is attached
        requestApplyInsetsWhenAttached(target)

        // Stage 2 & 3: Fallback strategies (executed after view is laid out)
        target.post {
            AdchainLogger.v(TAG, "Post block executed - applied=$applied")
            if (!applied) {
                applyFallbackInsets(target)
            } else {
                AdchainLogger.v(TAG, "Insets already applied via listener, skipping fallback")
            }

            // Final padding check
            AdchainLogger.v(TAG, "=== Final padding: left=${target.paddingLeft}, top=${target.paddingTop}, right=${target.paddingRight}, bottom=${target.paddingBottom} ===")
        }
    }

    /**
     * Apply fallback insets when standard listener doesn't work
     */
    private fun applyFallbackInsets(target: View) {
        AdchainLogger.v(TAG, "Starting fallback insets...")

        // Stage 2: Get root window insets ignoring visibility
        val rootInsets = ViewCompat.getRootWindowInsets(target)
        if (rootInsets != null) {
            val allInsets = rootInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )

            AdchainLogger.v(TAG, "Stage 2 - Root insets found")
            AdchainLogger.v(TAG, "  - bottom=${allInsets.bottom}, top=${allInsets.top}")
            AdchainLogger.v(TAG, "  - left=${allInsets.left}, right=${allInsets.right}")

            if (allInsets.bottom > 0) {
                AdchainLogger.v(TAG, "Stage 2 - Applying root insets padding: ${allInsets.bottom}")
                target.setPadding(
                    target.paddingLeft,
                    target.paddingTop,
                    target.paddingRight,
                    allInsets.bottom
                )
                return
            } else {
                AdchainLogger.v(TAG, "Stage 2 - Root insets bottom is 0, trying Stage 3")
            }
        } else {
            AdchainLogger.v(TAG, "Stage 2 - Root insets is null")
        }

        // Stage 3: Resource-based navigation bar height fallback
        val navBarHeight = getNavigationBarHeight(target.context)
        AdchainLogger.v(TAG, "Stage 3 - Resource fallback: navBarHeight=$navBarHeight")

        if (navBarHeight > 0) {
            AdchainLogger.v(TAG, "Stage 3 - Applying resource-based padding: $navBarHeight")
            target.setPadding(
                target.paddingLeft,
                target.paddingTop,
                target.paddingRight,
                navBarHeight
            )
        } else {
            AdchainLogger.v(TAG, "Stage 3 - No navigation bar height found! Content may be hidden.")
        }
    }

    /**
     * Request apply insets when view is attached to window
     */
    private fun requestApplyInsetsWhenAttached(view: View) {
        if (view.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(view)
        } else {
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(v)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    // No-op
                }
            })
        }
    }

    /**
     * Get navigation bar height from system resources
     * Note: This may return 0 for gesture navigation
     */
    private fun getNavigationBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier(
            "navigation_bar_height",
            "dimen",
            "android"
        )
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    /**
     * Check if the device is using gesture navigation
     * This is a best-effort check and may not be 100% accurate
     */
    fun isGestureNavigationEnabled(context: Context): Boolean {
        // For Android 10+ (Q), check the system gesture navigation setting
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val navBarHeight = getNavigationBarHeight(context)
            // If navigation bar height is very small or 0, it's likely gesture navigation
            return navBarHeight <= 24
        }
        return false
    }
}