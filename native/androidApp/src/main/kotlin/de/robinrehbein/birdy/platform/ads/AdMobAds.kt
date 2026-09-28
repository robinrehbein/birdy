package de.robinrehbein.birdy.platform.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import de.robinrehbein.birdy.platform.Ads
import de.robinrehbein.birdy.platform.AdsStatus
import de.robinrehbein.birdy.platform.RewardKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ad-unit ids (platform.md §1.6, `BirdyAdsPlugin.java:27-29`). */
private const val TEST_UNIT = "ca-app-pub-3940256099942544/5224354917"
private const val LIVE_COINS_UNIT = "ca-app-pub-1786159152036324/7854280106"
private const val LIVE_PASS_UNIT = "ca-app-pub-1786159152036324/8020434087"

/**
 * Port of `ads.js` + `BirdyAdsPlugin.java` (platform.md §1) onto the Google Mobile Ads SDK + UMP.
 * All calls run on the UI thread, as the Java plugin did. [activity] resolves the current
 * Activity needed to show a consent form or a rewarded ad; a null result (e.g. called after the
 * Activity was destroyed) is treated like "not shown yet" and simply skipped, matching the JS
 * behaviour of never crashing when ads aren't available.
 */
class AdMobAds(
    private val context: Context,
    private val isDebug: Boolean,
    private val activity: () -> Activity?,
) : Ads {
    private val state = MutableStateFlow(AdsStatus())
    override val status: StateFlow<AdsStatus> = state

    private val rewardedAds = HashMap<RewardKind, RewardedAd>()
    private val loading = HashSet<RewardKind>()
    private var consent: ConsentInformation? = null
    private var initialized = false

    override fun init() {
        // Every player is treated as under the age of consent: non-personalized, age-appropriate
        // ads only, regardless of Birdy's actual 13+ target audience (platform.md §1.6 step 1).
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
        val info = UserMessagingPlatform.getConsentInformation(context)
        consent = info
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build()
        val act = activity()
        if (act == null) {
            startAdsIfAllowed()
            return
        }
        info.requestConsentInfoUpdate(
            act,
            params,
            {
                // Re-resolve after the network round trip: the Activity may be gone by now.
                val current = activity()?.takeUnless { it.isFinishing || it.isDestroyed }
                if (current == null) {
                    startAdsIfAllowed()
                } else {
                    runCatching {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(current) { startAdsIfAllowed() }
                    }.onFailure { startAdsIfAllowed() }
                }
            },
            { startAdsIfAllowed() },
        )
    }

    override fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit) {
        val act = activity()
        // Pop the ad regardless of outcome (platform.md §1.6 `showRewarded` step 2): a failed
        // show must not leave a stale ad instance around.
        val ad = rewardedAds.remove(kind)
        publishStatus()
        val allowed = consent?.canRequestAds() == true
        if (ad == null || !allowed || act == null) {
            onResult(false)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onResult(earned)
                loadRewarded(kind)
            }

            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                onResult(false)
                loadRewarded(kind)
            }
        }
        ad.setImmersiveMode(true)
        ad.show(act) { earned = true }
    }

    override fun showPrivacyOptions() {
        val info = consent ?: return
        val act = activity() ?: return
        if (info.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) return
        UserMessagingPlatform.showPrivacyOptionsForm(act) { startAdsIfAllowed() }
    }

    private fun startAdsIfAllowed() {
        val info = consent
        if (info == null || !info.canRequestAds() || initialized) {
            publishStatus()
            return
        }
        MobileAds.initialize(context) {
            initialized = true
            loadRewarded(RewardKind.Coins)
            loadRewarded(RewardKind.Pass)
            publishStatus()
        }
    }

    /** Also called opportunistically from [showRewarded]/status reads, mirroring `getStatus()`. */
    private fun loadRewarded(kind: RewardKind) {
        val info = consent
        if (!initialized || info == null || !info.canRequestAds()) return
        if (rewardedAds.containsKey(kind) || loading.contains(kind)) return
        loading += kind
        val unit = if (isDebug) TEST_UNIT else if (kind == RewardKind.Coins) LIVE_COINS_UNIT else LIVE_PASS_UNIT
        RewardedAd.load(context, unit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loading -= kind
                rewardedAds[kind] = ad
                publishStatus()
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                loading -= kind
                // No retry/backoff here (platform.md §1.6): the next getStatus()/showRewarded
                // round trip (below) is what retries.
            }
        })
    }

    private fun publishStatus() {
        // Every status read is also a load-retry trigger, like the Java plugin's getStatus().
        loadRewarded(RewardKind.Coins)
        loadRewarded(RewardKind.Pass)
        val allowed = consent?.canRequestAds() == true
        state.value = AdsStatus(
            supported = true,
            ready = RewardKind.entries.filter { allowed && rewardedAds.containsKey(it) }.toSet(),
            privacyOptionsRequired = consent?.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
    }
}
