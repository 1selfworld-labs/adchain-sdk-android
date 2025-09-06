package com.adchain.sdk.quiz

import com.adchain.sdk.quiz.models.QuizEvent

interface AdchainQuizEventsListener {
    fun onImpressed(quizEvent: QuizEvent)
    fun onClicked(quizEvent: QuizEvent)
    fun onQuizCompleted(quizEvent: QuizEvent, score: Int)
}