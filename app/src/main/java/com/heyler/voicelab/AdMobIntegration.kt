package com.heyler.voicelab

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.security.MessageDigest

internal enum class AdAgeTreatment {
    TEEN,
    ADULT;

    fun sdkValue(): AgeRestrictedTreatment = when (this) {
        TEEN -> AgeRestrictedTreatment.TEEN
        ADULT -> AgeRestrictedTreatment.UNSPECIFIED
    }

    companion object {
        fun forAge(age: Int): AdAgeTreatment = if (age < 18) TEEN else ADULT
    }
}

/** UMP and full-screen AdMob ads. The selected age is stored only on this device. */
internal class AdMobIntegration(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("admob_preferences", Context.MODE_PRIVATE)
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    var ageTreatment: AdAgeTreatment? by mutableStateOf(null)
        private set
    var ageYears: Int? by mutableStateOf(null)
        private set
    var activeAccountUid: String? by mutableStateOf(null)
        private set
    var canRequestAds: Boolean by mutableStateOf(false)
        private set
    var initialized: Boolean by mutableStateOf(false)
        private set
    var privacyOptionsRequired: Boolean by mutableStateOf(false)
        private set

    private var initializationStarted = false
    private var firstForegroundSeen = false
    private var appOpenLoading = false
    private var rewardedLoading = false
    private var appOpenAd: AppOpenAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenLoadedAt = 0L
    private var rewardedLoadedAt = 0L
    private var fullScreenShowing = false

    fun updateConsent(activity: Activity, treatment: AdAgeTreatment?) {
        val underConsentAge = treatment == AdAgeTreatment.TEEN
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(underConsentAge)
            .build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refreshConsentState()
                    maybeInitialize()
                }
            },
            {
                refreshConsentState()
                maybeInitialize()
            }
        )
    }

    fun selectAccount(uid: String, activity: Activity) {
        if (activeAccountUid == uid) return
        activeAccountUid = uid
        ageYears = preferences.getInt(ageKey(uid), 0).takeIf { it in MINIMUM_AGE..MAXIMUM_AGE }
        ageTreatment = ageYears?.let { AdAgeTreatment.forAge(it) }
        // The previous version stored only a category; ask existing users to select their age again.
        preferences.edit().remove(legacyAgeKey(uid)).remove(LEGACY_AGE_KEY).apply()
        appOpenAd = null
        rewardedAd = null
        applyRequestConfiguration()
        if (ageTreatment != null && initialized) {
            loadAppOpenAd()
            loadRewardedAd()
        } else maybeInitialize()
        ageTreatment?.let { updateConsent(activity, it) }
    }

    fun clearAccount() {
        activeAccountUid = null
        ageTreatment = null
        ageYears = null
        appOpenAd = null
        rewardedAd = null
    }

    fun forgetDeletedAccount(uid:String){
        preferences.edit().remove(ageKey(uid)).remove(legacyAgeKey(uid)).apply()
        if(activeAccountUid==uid)clearAccount()
    }

    fun saveAge(activity: Activity, uid: String, age: Int) {
        require(age in MINIMUM_AGE..MAXIMUM_AGE)
        if (activeAccountUid != uid) selectAccount(uid, activity)
        ageYears = age
        val treatment = AdAgeTreatment.forAge(age)
        ageTreatment = treatment
        preferences.edit().putInt(ageKey(uid), age).apply()
        applyRequestConfiguration()
        appOpenAd = null
        rewardedAd = null
        maybeInitialize()
        if (initialized) {
            loadAppOpenAd()
            loadRewardedAd()
        }
        updateConsent(activity, treatment)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            refreshConsentState()
            maybeInitialize()
        }
    }

    /** App-open ads are infrequent and never cover an active recording or answer generation. */
    fun onAppForeground(activity: Activity, recording: Boolean) {
        val foregroundCount = preferences.getInt(KEY_FOREGROUND_COUNT, 0) + 1
        preferences.edit().putInt(KEY_FOREGROUND_COUNT, foregroundCount).apply()
        if (!firstForegroundSeen || foregroundCount <= FIRST_OPEN_AD_FREE_USES) {
            firstForegroundSeen = true
            if(preferences.getLong(KEY_LAST_APP_OPEN_WALL,0L)==0L)preferences.edit().putLong(KEY_LAST_APP_OPEN_WALL,System.currentTimeMillis()).apply()
            return
        }
        if (recording || ageTreatment == null || !canRequestAds || !initialized || fullScreenShowing) return
        val now = System.currentTimeMillis()
        val previousAd=preferences.getLong(KEY_LAST_APP_OPEN_WALL,0L)
        if(previousAd==0L){preferences.edit().putLong(KEY_LAST_APP_OPEN_WALL,now).apply();return}
        if (now - previousAd < APP_OPEN_GAP_MS) return
        val ad = appOpenAd
        if (ad == null || now - appOpenLoadedAt >= APP_OPEN_TTL_MS) {
            appOpenAd = null
            loadAppOpenAd()
            return
        }
        appOpenAd = null
        fullScreenShowing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishFullScreen(appOpen=true)
            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finishFullScreen(appOpen=true)
        }
        ad.show(activity)
    }

    /** An explicit tap opts into one video; the summary is delivered only after the reward callback. */
    fun showRewardedSummary(activity:Activity,onRewarded:()->Unit,onUnavailable:()->Unit,onNotCompleted:()->Unit){
        if(!initialized||!canRequestAds||ageTreatment==null||fullScreenShowing){
            loadRewardedAd();onUnavailable();return
        }
        val ad=rewardedAd?.takeIf{SystemClock.elapsedRealtime()-rewardedLoadedAt<REWARDED_TTL_MS}
        if(ad==null){rewardedAd=null;loadRewardedAd();onUnavailable();return}
        rewardedAd=null
        fullScreenShowing=true
        var earned=false
        ad.fullScreenContentCallback=object:FullScreenContentCallback(){
            override fun onAdDismissedFullScreenContent(){finishFullScreen(appOpen=false);if(!earned)onNotCompleted()}
            override fun onAdFailedToShowFullScreenContent(adError:AdError){finishFullScreen(appOpen=false);if(!earned)onUnavailable()}
        }
        ad.show(activity){earned=true;onRewarded()}
    }

    private fun refreshConsentState() {
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        canRequestAds = consentInformation.canRequestAds()
    }

    private fun maybeInitialize() {
        if (ageTreatment == null || !canRequestAds || initializationStarted) return
        initializationStarted = true
        applyRequestConfiguration()
        MobileAds.initialize(appContext) {
            initialized = true
            loadAppOpenAd()
            loadRewardedAd()
        }
    }

    private fun applyRequestConfiguration() {
        val treatment = ageTreatment ?: return
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setAgeRestrictedTreatment(treatment.sdkValue())
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .build()
        )
    }

    private fun loadAppOpenAd() {
        if (!initialized || !canRequestAds || ageTreatment == null || appOpenLoading || appOpenAd != null) return
        appOpenLoading = true
        AppOpenAd.load(
            appContext,
            if (BuildConfig.DEBUG) TEST_APP_OPEN_ID else PRODUCTION_APP_OPEN_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadedAt = SystemClock.elapsedRealtime()
                    appOpenLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    appOpenLoading = false
                }
            }
        )
    }

    private fun loadRewardedAd(){
        if(!initialized||!canRequestAds||ageTreatment==null||rewardedLoading||rewardedAd!=null)return
        rewardedLoading=true
        RewardedAd.load(appContext,if(BuildConfig.DEBUG)TEST_REWARDED_ID else PRODUCTION_REWARDED_ID,AdRequest.Builder().build(),object:RewardedAdLoadCallback(){
            override fun onAdLoaded(ad:RewardedAd){rewardedAd=ad;rewardedLoadedAt=SystemClock.elapsedRealtime();rewardedLoading=false}
            override fun onAdFailedToLoad(error:LoadAdError){rewardedAd=null;rewardedLoading=false}
        })
    }

    private fun finishFullScreen(appOpen:Boolean) {
        fullScreenShowing = false
        if(appOpen)preferences.edit().putLong(KEY_LAST_APP_OPEN_WALL,System.currentTimeMillis()).apply()
        loadAppOpenAd()
        loadRewardedAd()
    }

    companion object {
        private const val LEGACY_AGE_KEY = "age_treatment"
        private const val MINIMUM_AGE = 13
        private const val MAXIMUM_AGE = 100
        private const val KEY_FOREGROUND_COUNT = "foreground_count"
        private const val KEY_LAST_APP_OPEN_WALL = "last_app_open_wall"
        private const val FIRST_OPEN_AD_FREE_USES = 1
        private const val APP_OPEN_TTL_MS = 4L * 60L * 60L * 1000L
        private const val APP_OPEN_GAP_MS = 30L * 60L * 1000L
        private const val REWARDED_TTL_MS = 60L * 60L * 1000L
        private const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"
        private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val PRODUCTION_APP_OPEN_ID = "ca-app-pub-6640063937664487/4794541884"
        private const val PRODUCTION_REWARDED_ID = "ca-app-pub-6640063937664487/2168378544"
    }

    private fun ageKey(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        val suffix = digest.joinToString("") { byte -> "%02x".format(byte) }.take(24)
        return "age_years_$suffix"
    }

    private fun legacyAgeKey(uid: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        val suffix = digest.joinToString("") { byte -> "%02x".format(byte) }.take(24)
        return "age_treatment_$suffix"
    }
}
