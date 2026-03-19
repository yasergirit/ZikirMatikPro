package com.yasergirit.zikirmasterpro.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(
    private val appContext: Context,
    private val productIds: List<String> = listOf(
        "support_10",
        "support_25",
        "support_50",
        "support_100",
        "support_200"
    )
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
    }

    private var billingClient: BillingClient? = null

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    private val _purchaseMessage = MutableStateFlow<String?>(null)
    val purchaseMessage: StateFlow<String?> = _purchaseMessage

    fun clearMessage() {
        _purchaseMessage.value = null
    }

    fun start() {
        if (billingClient != null) return

        try {
            billingClient = BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()

            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    Log.d(TAG, "Setup: ${result.responseCode} - ${result.debugMessage}")
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isReady.value = true
                        _connectionError.value = null
                        queryProducts()
                    } else {
                        _isReady.value = false
                        _connectionError.value = "Bağlantı hatası: ${result.debugMessage}"
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Log.d(TAG, "Billing disconnected")
                    _isReady.value = false
                    _connectionError.value = "Google Play bağlantısı kesildi"
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Billing start error", e)
            _connectionError.value = "Google Play Hizmetleri bulunamadı"
        }
    }

    fun end() {
        billingClient?.endConnection()
        billingClient = null
        _isReady.value = false
        _products.value = emptyList()
        _connectionError.value = null
    }

    private fun queryProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val productList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            Log.d(TAG, "Query: ${billingResult.responseCode}, count: ${productDetailsList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = productDetailsList
                if (productDetailsList.isEmpty()) {
                    _connectionError.value = "Ürünler henüz Google Play'de tanımlanmamış"
                }
            } else {
                _connectionError.value = "Ürün sorgulama hatası: ${billingResult.debugMessage}"
            }
        }
    }

    fun isInstalledFromPlayStore(): Boolean {
        return try {
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                appContext.packageManager.getInstallSourceInfo(appContext.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getInstallerPackageName(appContext.packageName)
            }
            installer == "com.android.vending" || installer == "com.google.android.feedback"
        } catch (e: Exception) {
            false
        }
    }

    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val client = billingClient ?: return

        if (!isInstalledFromPlayStore()) {
            _purchaseMessage.value = "Bu özellik yalnızca Google Play Store'dan indirilen sürümde çalışır."
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        Log.d(TAG, "LaunchFlow: ${result.responseCode}")
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) handlePurchases(purchases)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseMessage.value = "İşlem iptal edildi."
            }
            else -> {
                _purchaseMessage.value = "Satın alma hatası: ${result.debugMessage}"
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val client = billingClient ?: return

        purchases.forEach { purchase ->
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach

            // Consumable: consume et ki tekrar satın alınabilsin
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            client.consumeAsync(consumeParams) { billingResult, _ ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _purchaseMessage.value = "Desteğiniz için teşekkürler! ❤️"
                } else {
                    Log.e(TAG, "Consume error: ${billingResult.debugMessage}")
                    _purchaseMessage.value = "Desteğiniz için teşekkürler! ❤️"
                }
            }
        }
    }
}
