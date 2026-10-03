package de.robinrehbein.birdy.platform.ads

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
private const val TEST_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"
private const val LIVE_INTERSTITIAL_UNIT = "ca-app-pub-1786159152036324/6477156238"

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
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false
    private var consent: ConsentInformation? = null
    private var initialized = false
    private var disabledForSession = false
    private val agePreferences = context.getSharedPreferences("birdy_ad_age", Context.MODE_PRIVATE)

    override fun init() {
        val age = agePreferences.getString("group", null)
        if (age == null) {
            showAgeDialog(firstRun = true)
            return
        }
        startForAge(age)
    }

    /** The locally selected age group can be changed without sharing a birth date. */
    fun showAgeSettings() = showAgeDialog(firstRun = false)

    private fun showAgeDialog(firstRun: Boolean) {
        val act = activity() ?: return
        val german = context.resources.configuration.locales[0].language == "de"
        val choose: (String) -> Unit = { chosen ->
            agePreferences.edit().putString("group", chosen).apply()
            if (firstRun) startForAge(chosen) else {
                // Previously loaded ads must never be shown with the wrong age treatment.
                disabledForSession = true
                rewardedAds.clear()
                interstitial = null
                publishStatus()
                act.recreate()
            }
        }
        AlertDialog.Builder(act)
            .setTitle(if (german) "Altersgruppe für Werbung" else "Age group for ads")
            .setMessage(if (german) "Ab 16 zeigt Birdy nach längerer Spielzeit gelegentlich eine Anzeige zwischen Runden. Freiwillige Belohnungsanzeigen bleiben verfügbar. Wähle deine Altersgruppe für passende Anzeigen und Einwilligung. Die Auswahl bleibt auf diesem Gerät."
                else "At 16+, Birdy occasionally shows an ad between rounds after extended play. Optional reward ads remain available. Choose your age group for appropriate ads and consent. This choice stays on this device.")
            .setNegativeButton("13–15") { _, _ -> choose("13-15") }
            .setPositiveButton("16+") { _, _ -> choose("16+") }
            .setNeutralButton(if (german) "Später" else "Later") { _, _ -> }
            .show()
    }

    private fun startForAge(age: String) {
        if (disabledForSession) return
        val underAge = age == "13-15"
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setAgeRestrictedTreatment(if (underAge) AgeRestrictedTreatment.CHILD else AgeRestrictedTreatment.UNSPECIFIED)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build()
        )
        val info = UserMessagingPlatform.getConsentInformation(context)
        consent = info
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(underAge).build()
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

    /** The loaded ad (and unit) behind a [RewardKind]: the game-over x2 bonus reuses the coin ad. */
    private fun adSlot(kind: RewardKind): RewardKind = when (kind) {
        RewardKind.Coins, RewardKind.DoubleCoins -> RewardKind.Coins
        RewardKind.Pass -> RewardKind.Pass
    }

    override fun showRewarded(kind: RewardKind, onResult: (earned: Boolean) -> Unit) {
        val unit = adSlot(kind)
        val act = activity()
        // Pop the ad regardless of outcome (platform.md §1.6 `showRewarded` step 2): a failed
        // show must not leave a stale ad instance around.
        val ad = rewardedAds.remove(unit)
        publishStatus()
        val allowed = consent?.canRequestAds() == true
        if (ad == null || !allowed || act == null || disabledForSession) {
            onResult(false)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onResult(earned)
                loadRewarded(unit)
            }

            override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                onResult(false)
                loadRewarded(unit)
            }
        }
        ad.setImmersiveMode(true)
        ad.show(act) { earned = true }
    }

    override fun showInterstitial(onResult: (shown: Boolean) -> Unit) {
        val act = activity()
        val ad = interstitial
        interstitial = null
        publishStatus()
        if (ad == null || act == null || act.isFinishing || act.isDestroyed || !interstitialAllowed()) {
            onResult(false)
            return
        }
        var reported = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                reported = true
                onResult(true)
            }

            override fun onAdDismissedFullScreenContent() {
                if (!reported) onResult(false)
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                if (!reported) onResult(false)
                loadInterstitial()
            }
        }
        ad.setImmersiveMode(true)
        ad.show(act)
    }

    override fun showPrivacyOptions() {
        val info = consent ?: return
        val act = activity() ?: return
        if (info.privacyOptionsRequirementStatus != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) return
        UserMessagingPlatform.showPrivacyOptionsForm(act) { startAdsIfAllowed() }
    }

    private fun startAdsIfAllowed() {
        val info = consent
        if (disabledForSession || info == null || !info.canRequestAds()) {
            publishStatus()
            return
        }
        if (initialized) {
            loadRewarded(RewardKind.Coins)
            loadRewarded(RewardKind.Pass)
            loadInterstitial()
            publishStatus()
            return
        }
        MobileAds.initialize(context) {
            initialized = true
            loadRewarded(RewardKind.Coins)
            loadRewarded(RewardKind.Pass)
            loadInterstitial()
            publishStatus()
        }
    }

    /** Also called opportunistically from [showRewarded]/status reads, mirroring `getStatus()`. */
    private fun loadRewarded(kind: RewardKind) {
        val info = consent
        if (disabledForSession || !initialized || info == null || !info.canRequestAds()) return
        if (rewardedAds.containsKey(kind) || loading.contains(kind)) return
        loading += kind
        val unit = if (isDebug) TEST_UNIT else when (adSlot(kind)) {
            RewardKind.Pass -> LIVE_PASS_UNIT
            else -> LIVE_COINS_UNIT
        }
        RewardedAd.load(context, unit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loading -= kind
                if (!disabledForSession) rewardedAds[kind] = ad
                publishStatus()
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                loading -= kind
                // No retry/backoff here (platform.md §1.6): the next getStatus()/showRewarded
                // round trip (below) is what retries.
            }
        })
    }

    private fun interstitialAllowed(): Boolean = !disabledForSession &&
        agePreferences.getString("group", null) == "16+" && consent?.canRequestAds() == true

    private fun loadInterstitial() {
        if (!initialized || !interstitialAllowed() || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        val unit = if (isDebug) TEST_INTERSTITIAL_UNIT else LIVE_INTERSTITIAL_UNIT
        InterstitialAd.load(context, unit, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                loadingInterstitial = false
                if (interstitialAllowed()) interstitial = ad
                publishStatus()
            }

            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                loadingInterstitial = false
            }
        })
    }

    private fun publishStatus() {
        // Every status read is also a load-retry trigger, like the Java plugin's getStatus().
        loadRewarded(RewardKind.Coins)
        loadRewarded(RewardKind.Pass)
        loadInterstitial()
        val allowed = !disabledForSession && consent?.canRequestAds() == true
        state.value = AdsStatus(
            supported = true,
            ready = RewardKind.entries.filter { allowed && rewardedAds.containsKey(adSlot(it)) }.toSet(),
            interstitialReady = interstitialAllowed() && interstitial != null,
            privacyOptionsRequired = consent?.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        )
    }
}
