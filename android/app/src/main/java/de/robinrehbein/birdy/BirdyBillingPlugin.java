package de.robinrehbein.birdy;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.Purchase;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CapacitorPlugin(name = "BirdyBilling")
public class BirdyBillingPlugin extends Plugin {
    private BillingClient client;
    private final Map<String, ProductDetails> products = new HashMap<>();

    @Override
    public void load() {
        super.load();
        client = BillingClient.newBuilder(getContext())
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .setListener((result, purchases) -> {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase purchase : purchases) notifyListeners("purchase", toJs(purchase));
                }
            }).build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) notifyListeners("ready", new JSObject());
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private JSObject toJs(Purchase purchase) {
        JSObject item = new JSObject();
        item.put("products", new JSArray(purchase.getProducts()));
        item.put("token", purchase.getPurchaseToken());
        item.put("state", purchase.getPurchaseState());
        item.put("acknowledged", purchase.isAcknowledged());
        item.put("time", purchase.getPurchaseTime());
        return item;
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject response = new JSObject();
        response.put("ready", client != null && client.isReady());
        call.resolve(response);
    }

    @PluginMethod
    public void getProducts(PluginCall call) {
        if (!client.isReady()) { call.reject("Billing unavailable"); return; }
        JSArray ids = call.getArray("ids");
        if (ids == null || ids.length() == 0) { call.reject("Product IDs required"); return; }
        List<QueryProductDetailsParams.Product> query = new ArrayList<>();
        try {
            for (int i = 0; i < ids.length(); i++) {
                query.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(ids.getString(i))
                    .setProductType(BillingClient.ProductType.INAPP).build());
            }
        } catch (Exception error) { call.reject("Invalid product ID list"); return; }
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(query).build();
        client.queryProductDetailsAsync(params, (result, details) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                call.reject(result.getDebugMessage()); return;
            }
            JSArray items = new JSArray();
            for (ProductDetails product : details.getProductDetailsList()) {
                List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
                if (offers == null || offers.isEmpty()) continue;
                ProductDetails.OneTimePurchaseOfferDetails offer = offers.get(0);
                products.put(product.getProductId(), product);
                JSObject item = new JSObject();
                item.put("id", product.getProductId());
                item.put("title", product.getTitle());
                item.put("description", product.getDescription());
                item.put("price", offer.getFormattedPrice());
                items.put(item);
            }
            JSObject response = new JSObject();
            response.put("items", items);
            call.resolve(response);
        });
    }

    @PluginMethod
    public void purchase(PluginCall call) {
        if (!client.isReady()) { call.reject("Billing unavailable"); return; }
        ProductDetails product = products.get(call.getString("id", ""));
        if (product == null) { call.reject("Product unavailable"); return; }
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
        if (offers == null || offers.isEmpty()) { call.reject("Offer unavailable"); return; }
        BillingFlowParams.ProductDetailsParams detail = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offers.get(0).getOfferToken()).build();
        getActivity().runOnUiThread(() -> {
            BillingResult result = client.launchBillingFlow(getActivity(), BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(detail)).build());
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) call.resolve();
            else call.reject(result.getDebugMessage());
        });
    }

    @PluginMethod
    public void restore(PluginCall call) {
        if (!client.isReady()) { call.reject("Billing unavailable"); return; }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                call.reject(result.getDebugMessage()); return;
            }
            JSArray items = new JSArray();
            for (Purchase purchase : purchases) items.put(toJs(purchase));
            JSObject response = new JSObject();
            response.put("items", items);
            call.resolve(response);
        });
    }

    @PluginMethod
    public void consume(PluginCall call) {
        if (!client.isReady()) { call.reject("Billing unavailable"); return; }
        String token = call.getString("token", "");
        if (token.isEmpty()) { call.reject("Purchase token required"); return; }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                call.reject(result.getDebugMessage()); return;
            }
            boolean owned = false;
            for (Purchase purchase : purchases) {
                if (purchase.getPurchaseToken().equals(token)
                    && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    owned = true; break;
                }
            }
            if (!owned) { call.reject("Purchase not found"); return; }
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(),
                (consumeResult, consumedToken) -> {
                    if (consumeResult.getResponseCode() == BillingClient.BillingResponseCode.OK) call.resolve();
                    else call.reject(consumeResult.getDebugMessage());
                });
        });
    }

    @PluginMethod
    public void acknowledge(PluginCall call) {
        if (!client.isReady()) { call.reject("Billing unavailable"); return; }
        String token = call.getString("token", "");
        if (token.isEmpty()) { call.reject("Purchase token required"); return; }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                call.reject(result.getDebugMessage()); return;
            }
            for (Purchase purchase : purchases) {
                if (!purchase.getPurchaseToken().equals(token)
                    || purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                if (purchase.isAcknowledged()) { call.resolve(); return; }
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
                    ack -> {
                        if (ack.getResponseCode() == BillingClient.BillingResponseCode.OK) call.resolve();
                        else call.reject(ack.getDebugMessage());
                    });
                return;
            }
            call.reject("Purchase not found");
        });
    }

    @Override
    protected void handleOnDestroy() {
        if (client != null) client.endConnection();
        super.handleOnDestroy();
    }
}
