package com.adchain.sdk.mission

interface AdchainMissionEventsListener {
    fun onClicked(mission: Mission)
    fun onImpressed(mission: Mission)
    fun onCompleted(mission: Mission)
}