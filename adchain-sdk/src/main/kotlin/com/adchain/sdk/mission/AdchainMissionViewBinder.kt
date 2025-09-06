package com.adchain.sdk.mission

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.util.Log
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdchainMissionViewBinder private constructor(
    private val titleTextView: TextView,
    private val descriptionTextView: TextView?,
    private val rewardTextView: TextView?,
    private val progressTextView: TextView?,
    private val progressBar: ProgressBar?,
    private val iconImageView: ImageView?,
    private val containerView: View
) {
    
    companion object {
        private const val TAG = "AdchainMissionViewBinder"
    }
    
    class Builder {
            private var titleTextView: TextView? = null
            private var descriptionTextView: TextView? = null
            private var rewardTextView: TextView? = null
            private var progressTextView: TextView? = null
            private var progressBar: ProgressBar? = null
            private var iconImageView: ImageView? = null
            private var containerView: View? = null
            
            fun titleTextView(view: TextView): Builder {
                this.titleTextView = view
                return this
            }
            
            fun descriptionTextView(view: TextView): Builder {
                this.descriptionTextView = view
                return this
            }
            
            fun rewardTextView(view: TextView): Builder {
                this.rewardTextView = view
                return this
            }
            
            fun progressTextView(view: TextView): Builder {
                this.progressTextView = view
                return this
            }
            
            fun progressBar(view: ProgressBar): Builder {
                this.progressBar = view
                return this
            }
            
            fun iconImageView(view: ImageView): Builder {
                this.iconImageView = view
                return this
            }
            
            fun containerView(view: View): Builder {
                this.containerView = view
                return this
            }
            
            fun build(): AdchainMissionViewBinder {
                requireNotNull(titleTextView) { "titleTextView must be set" }
                requireNotNull(containerView) { "containerView must be set" }
                
                return AdchainMissionViewBinder(
                    titleTextView = titleTextView!!,
                    descriptionTextView = descriptionTextView,
                    rewardTextView = rewardTextView,
                    progressTextView = progressTextView,
                    progressBar = progressBar,
                    iconImageView = iconImageView,
                    containerView = containerView!!
                )
            }
        }
    
    fun bind(mission: Mission, adchainMission: AdchainMission) {
        Log.d(TAG, "Binding mission: ${mission.id}")
        
        // Bind basic data
        titleTextView.text = mission.title
        descriptionTextView?.text = mission.description
        
        // Show participating status or point
        rewardTextView?.text = if (adchainMission.isParticipating(mission.id)) {
            "참여확인중"
        } else {
            mission.point
        }
        
        // Note: Progress is now at the response level, not individual mission
        // Hide progress bar since individual mission progress is not provided
        progressBar?.visibility = View.GONE
        progressTextView?.visibility = View.GONE
        
        // Load icon image if available
        iconImageView?.let { imageView ->
            loadImage(mission.imageUrl, imageView)
        }
        
        // Set click listener
        containerView.setOnClickListener {
            Log.d(TAG, "Mission clicked: ${mission.id}")
            
            // Open WebView immediately
            adchainMission.onMissionClicked(mission)
            adchainMission.openMissionWebView(containerView.context, mission)
            
            // Change text after 1 second in background
            CoroutineScope(Dispatchers.Main).launch {
                // Wait 1 second
                delay(1000)
                
                // Change text after delay
                rewardTextView?.text = "참여확인중"
                
                // Mark as participating
                adchainMission.markAsParticipating(mission.id)
            }
        }
        
        // Track impression
        adchainMission.onMissionImpressed(mission)
    }
    
    private fun loadImage(imageUrl: String, imageView: ImageView) {
        try {
            Glide.with(imageView.context)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(imageView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: $imageUrl", e)
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }
}