package com.adchain.sdk.quiz

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.adchain.sdk.quiz.models.QuizEvent
import com.bumptech.glide.Glide

class AdchainQuizViewBinder private constructor(
    private val iconImageView: ImageView,
    private val titleTextView: TextView,
    private val pointsTextView: TextView?,
    private val containerView: View
) {
    
    companion object {
        private const val TAG = "AdchainQuizViewBinder"
    }
    
    fun bind(quizEvent: QuizEvent, quiz: AdchainQuiz, context: Context) {
        // Set title
        titleTextView.text = quizEvent.title
        
        // Set points if available
        pointsTextView?.text = quizEvent.point
        
        // Load image using Glide (SDK handles image loading)
        loadImage(quizEvent.imageUrl, iconImageView)
        
        // Set click listener
        containerView.setOnClickListener {
            Log.d(TAG, "Quiz item clicked: ${quizEvent.id}")
            
            // Track click
            quiz.trackClick(quizEvent)
            
            // Open WebView using AdchainOfferwallActivity
            quiz.openQuizWebView(context, quizEvent)
        }
        
        // Track impression
        quiz.trackImpression(quizEvent)
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
    
    class Builder {
        private var iconImageView: ImageView? = null
        private var titleTextView: TextView? = null
        private var pointsTextView: TextView? = null
        private var containerView: View? = null
        
        fun iconImageView(view: ImageView) = apply { 
            iconImageView = view 
        }
        
        fun titleTextView(view: TextView) = apply { 
            titleTextView = view 
        }
        
        fun pointsTextView(view: TextView) = apply { 
            pointsTextView = view 
        }
        
        fun containerView(view: View) = apply { 
            containerView = view 
        }
        
        fun build(): AdchainQuizViewBinder {
            requireNotNull(iconImageView) { "Icon ImageView is required" }
            requireNotNull(titleTextView) { "Title TextView is required" }
            requireNotNull(containerView) { "Container View is required" }
            
            return AdchainQuizViewBinder(
                iconImageView!!,
                titleTextView!!,
                pointsTextView,
                containerView!!
            )
        }
    }
}