package com.adchain.sdk.core

data class AdchainSdkUser(
    val userId: String,
    val gender: Gender? = null,
    val birthYear: Int? = null,
    val customProperties: Map<String, Any> = emptyMap()
) {
    enum class Gender(val value: String) {
        MALE("M"),
        FEMALE("F")
    }
    
    init {
        require(userId.isNotEmpty()) { "User ID cannot be empty" }
        birthYear?.let {
            require(it in 1900..2100) { "Birth year must be between 1900 and 2100" }
        }
    }
    
    class Builder(private val userId: String) {
        private var gender: Gender? = null
        private var birthYear: Int? = null
        private val customProperties = mutableMapOf<String, Any>()
        
        fun setGender(gender: Gender) = apply {
            this.gender = gender
        }
        
        fun setBirthYear(year: Int) = apply {
            this.birthYear = year
        }
        
        fun setCustomProperty(key: String, value: Any) = apply {
            customProperties[key] = value
        }
        
        fun build(): AdchainSdkUser {
            return AdchainSdkUser(
                userId = userId,
                gender = gender,
                birthYear = birthYear,
                customProperties = customProperties.toMap()
            )
        }
    }
}