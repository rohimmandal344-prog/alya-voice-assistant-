package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingRepository(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val TAG = "BillingRepository"

    private val _isBillingFlowActive = MutableStateFlow(false)
    val isBillingFlowActive: StateFlow<Boolean> = _isBillingFlowActive.asStateFlow()

    private val _subscriptionActive = MutableStateFlow(true)
    val subscriptionActive: StateFlow<Boolean> = _subscriptionActive.asStateFlow()

    private val _billingServiceAvailable = MutableStateFlow<Boolean?>(true)
    val billingServiceAvailable: StateFlow<Boolean?> = _billingServiceAvailable.asStateFlow()

    private val _billingSetupErrorCode = MutableStateFlow<Int?>(null)
    val billingSetupErrorCode: StateFlow<Int?> = _billingSetupErrorCode.asStateFlow()

    init {
        Log.i(TAG, "BillingRepository initialized in permanently-free mode.")
    }

    fun queryPurchases() {
        _subscriptionActive.value = true
        Log.i(TAG, "Subscription is free and permanently active for everyone.")
    }

    fun launchBillingFlow(activity: Activity): Boolean {
        Log.i(TAG, "Mocking billing flow success (permanently-free mode).")
        _isBillingFlowActive.value = true
        externalScope.launch {
            kotlinx.coroutines.delay(1000)
            _isBillingFlowActive.value = false
            _subscriptionActive.value = true
        }
        return true
    }

    fun endBillingSession(activity: Activity) {
        _isBillingFlowActive.value = false
    }
}
