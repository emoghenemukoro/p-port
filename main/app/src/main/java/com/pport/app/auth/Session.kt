package com.pport.app.auth

object Session {
    var isLoggedIn: Boolean = false
    var accessToken: String? = null
    var userId: String? = null
    var role: String? = null

    // ✅ USE ONLY THIS (single source of truth)
    var countryIso2: String? = null

    var flutterwaveSubaccountId: String? = null

    fun clear() {
        isLoggedIn = false
        accessToken = null
        userId = null
        role = null
        countryIso2 = null
        flutterwaveSubaccountId = null
    }
}