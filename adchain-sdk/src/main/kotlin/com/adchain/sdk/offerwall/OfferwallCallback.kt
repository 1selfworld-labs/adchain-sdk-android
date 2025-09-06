package com.adchain.sdk.offerwall

/**
 * Callback interface for offerwall events
 */
interface OfferwallCallback {
    /**
     * Called when the offerwall is successfully opened
     */
    fun onOpened()
    
    /**
     * Called when the offerwall is closed by user
     */
    fun onClosed()
    
    /**
     * Called when an error occurs
     * @param message Error message describing what went wrong
     */
    fun onError(message: String)
    
    /**
     * Called when user earns a reward from the offerwall
     * @param amount The amount of reward earned
     */
    fun onRewardEarned(amount: Int)
}