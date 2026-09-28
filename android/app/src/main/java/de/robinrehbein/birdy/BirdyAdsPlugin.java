package de.robinrehbein.birdy;

import androidx.annotation.NonNull;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@CapacitorPlugin(name = "BirdyAds")
public class BirdyAdsPlugin extends Plugin {
    private static final String TEST_UNIT = "ca-app-pub-3940256099942544/5224354917";
    private static final String LIVE_COINS_UNIT = "ca-app-pub-1786159152036324/7854280106";
    private static final String LIVE_PASS_UNIT = "ca-app-pub-1786159152036324/8020434087";
    private ConsentInformation consent;
    private final Map<String, RewardedAd> rewardedAds = new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private boolean initialized;

    @Override
    public void load() {
        super.load();
        getActivity().runOnUiThread(() -> {
            // Birdy targets ages 13+. Treat every player as under the age of consent:
            // only age-appropriate, non-personalized ads are requested.
            MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
                .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build());
            consent = UserMessagingPlatform.getConsentInformation(getContext());
            ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(true).build();
            consent.requestConsentInfoUpdate(getActivity(), params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(getActivity(), error -> startAdsIfAllowed()),
                error -> startAdsIfAllowed());
        });
    }

    private void startAdsIfAllowed() {
        if (consent == null || !consent.canRequestAds()) return;
        if (initialized) return;
        MobileAds.initialize(getContext(), status -> getActivity().runOnUiThread(() -> {
            initialized = true;
            loadRewarded("coins");
            loadRewarded("pass");
        }));
    }

    private void loadRewarded(String kind) {
        if (!initialized || consent == null || !consent.canRequestAds()
            || rewardedAds.containsKey(kind) || loading.contains(kind)) return;
        loading.add(kind);
        String unit = BuildConfig.DEBUG ? TEST_UNIT
            : ("pass".equals(kind) ? LIVE_PASS_UNIT : LIVE_COINS_UNIT);
        RewardedAd.load(getContext(), unit,
            new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    loading.remove(kind);
                    rewardedAds.put(kind, ad);
                    notifyListeners("status", status());
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError error) {
                    loading.remove(kind);
                    rewardedAds.remove(kind);
                    notifyListeners("status", status());
                }
            });
    }

    private JSObject status() {
        JSObject result = new JSObject();
        result.put("ready", rewardedAds.containsKey("coins") && consent != null && consent.canRequestAds());
        result.put("passReady", rewardedAds.containsKey("pass") && consent != null && consent.canRequestAds());
        result.put("privacyOptionsRequired", consent != null &&
            consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED);
        return result;
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            loadRewarded("coins");
            loadRewarded("pass");
            call.resolve(status());
        });
    }

    @PluginMethod
    public void showRewarded(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            String kind = call.getString("kind", "coins");
            if (!"coins".equals(kind) && !"pass".equals(kind)) {
                call.reject("Unknown reward");
                return;
            }
            RewardedAd ad = rewardedAds.remove(kind);
            if (ad == null || consent == null || !consent.canRequestAds()) {
                call.reject("Ad unavailable");
                return;
            }
            notifyListeners("status", status());
            final boolean[] earned = { false };
            ad.setImmersiveMode(true);
            ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    JSObject result = new JSObject();
                    result.put("earned", earned[0]);
                    call.resolve(result);
                    loadRewarded(kind);
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                    call.reject("Ad could not be shown");
                    loadRewarded(kind);
                }
            });
            ad.show(getActivity(), reward -> earned[0] = true);
        });
    }

    @PluginMethod
    public void showPrivacyOptions(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (consent == null || consent.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
                call.resolve();
                return;
            }
            UserMessagingPlatform.showPrivacyOptionsForm(getActivity(), error -> {
                if (error != null) call.reject(error.getMessage());
                else call.resolve();
                startAdsIfAllowed();
            });
        });
    }
}
