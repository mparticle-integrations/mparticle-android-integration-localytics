package com.mparticle.kits;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.localytics.android.Customer;
import com.localytics.android.GcmListenerService;
import com.localytics.android.Localytics;
import com.localytics.android.ReferralReceiver;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Impression;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.TransactionAttributes;
import com.mparticle.internal.Logger;
import com.mparticle.internal.MPUtility;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



public class LocalyticsKit extends KitIntegration implements KitIntegration.EventListener, KitIntegration.CommerceListener, KitIntegration.AttributeListener, KitIntegration.PushListener {
    static final String API_KEY = "appKey";
    static final String CUSTOM_DIMENSIONS = "customDimensions";
    private static final String RAW_LTV = "trackClvAsRawValue";

    private JSONArray customDimensionJson = null;
    private boolean trackAsRawLtv = false;

    @Override
    public String getName() {
        return "Localytics";
    }

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        try {
            customDimensionJson = new JSONArray(getSettings().get(CUSTOM_DIMENSIONS));
        } catch (Exception jse) {

        }
        trackAsRawLtv = Boolean.parseBoolean(getSettings().get(RAW_LTV));
        Localytics.autoIntegrate((Application)context.getApplicationContext(), getSettings().get(API_KEY));
        Localytics.setLoggingEnabled(MParticle.getInstance().getEnvironment() == MParticle.Environment.Development);
        return null;
    }

    @Override
    public void setLocation(Location location) {
        Localytics.setLocation(location);
    }

    @Override
    public void setInstallReferrer(Intent intent) {
        new ReferralReceiver().onReceive(getContext(), intent);
    }

    @Override
    public void setUserIdentity(MParticle.IdentityType identityType, String id) {
        if (identityType.equals(MParticle.IdentityType.Email)) {
            Localytics.setCustomerEmail(id);
        } else if (identityType.equals(MParticle.IdentityType.CustomerId)) {
            Localytics.setCustomerId(id);
        } else {
            Localytics.setIdentifier(identityType.name(), id);
        }
    }

    @Override
    public void removeUserIdentity(MParticle.IdentityType identityType) {
        if (identityType.equals(MParticle.IdentityType.Email)) {
            Localytics.setCustomerEmail("");
        } else if (identityType.equals(MParticle.IdentityType.CustomerId)) {
            Localytics.setCustomerId("");
        } else {
            Localytics.setIdentifier(identityType.name(), "");
        }
    }

    @Override
    public List<ReportingMessage> logout() {
        Localytics.tagCustomerLoggedOut(null);
        return null;
    }

    int getDimensionIndexForAttribute(String key) {
        if (customDimensionJson == null) {
            try {
                customDimensionJson = new JSONArray(getSettings().get(CUSTOM_DIMENSIONS));
            } catch (Exception jse) {

            }
        }
        if (customDimensionJson != null) {
            try {
                for (int i = 0; i < customDimensionJson.length(); i++) {
                    JSONObject dimension = customDimensionJson.getJSONObject(i);
                    if (dimension.getString("maptype").equals("UserAttributeClass.Name")) {
                        String attributeName = dimension.getString("map");
                        if (key.equalsIgnoreCase(attributeName)) {
                            return Integer.parseInt(dimension.getString("value").substring("Dimension ".length()));
                        }
                    }
                }
            } catch (Exception e) {
                Logger.debug("Exception while mapping mParticle user attributes to Localytics custom dimensions: " + e.toString());
            }
        }
        return -1;
    }

    @Override
    public void setUserAttribute(String key, String value) {
        int dimensionIndex = getDimensionIndexForAttribute(key);
        if (dimensionIndex >= 0) {
            Localytics.setCustomDimension(dimensionIndex, value);
        }
        if (key.equalsIgnoreCase(MParticle.UserAttributes.FIRSTNAME)) {
            Localytics.setCustomerFirstName(value);
        } else if (key.equalsIgnoreCase(MParticle.UserAttributes.LASTNAME)) {
            Localytics.setCustomerLastName(value);
        } else {
            Localytics.setProfileAttribute(KitUtils.sanitizeAttributeKey(key), value);
        }
    }

    @Override
    public void setUserAttributeList(String key, List<String> list) {
        String[] array = list.toArray(new String[list.size()]);
        Localytics.setProfileAttribute(key, array);
    }

    @Override
    public boolean supportsAttributeLists() {
        return true;
    }

    @Override
    public void setAllUserAttributes(Map<String, String> attributes, Map<String, List<String>> attributeLists) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            setUserAttribute(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> entry : attributeLists.entrySet()) {
            setUserAttributeList(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void removeUserAttribute(String key) {
        Localytics.deleteProfileAttribute(key);
    }

    @Override
    public List<ReportingMessage> setOptOut(boolean optOutStatus) {
        Localytics.setOptedOut(optOutStatus);
        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(new ReportingMessage(this, ReportingMessage.MessageType.OPT_OUT, System.currentTimeMillis(), null));
        return messageList;
    }

    @Override
    public List<ReportingMessage> leaveBreadcrumb(String breadcrumb) {
        return null;
    }

    @Override
    public List<ReportingMessage> logError(String message, Map<String, String> errorAttributes) {
        return null;
    }

    @Override
    public List<ReportingMessage> logException(Exception exception, Map<String, String> exceptionAttributes, String message) {
        return null;
    }

    @Override
    public List<ReportingMessage> logEvent(MPEvent event) {
        if (event.getInfo() == null || event.getInfo().size() == 0) {
            return null;
        }
        Localytics.tagEvent(event.getEventName(), event.getInfo());
        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(ReportingMessage.fromEvent(this, event));
        return messageList;
    }

    @Override
    public List<ReportingMessage> logScreen(String screenName, Map<String, String> eventAttributes) {
        Localytics.tagScreen(screenName);
        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(
                new ReportingMessage(this, ReportingMessage.MessageType.SCREEN_VIEW, System.currentTimeMillis(), eventAttributes)
                        .setScreenName(screenName)
        );
        return messageList;
    }

    @Override
    public List<ReportingMessage> logLtvIncrease(BigDecimal valueIncreased, BigDecimal totalValue, String eventName, Map<String, String> contextInfo) {
        int multiplier = trackAsRawLtv ? 1 : 100;
        Localytics.tagEvent(eventName, contextInfo, (long) valueIncreased.doubleValue() * multiplier);
        List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
        messageList.add(ReportingMessage.fromEvent(this, new MPEvent.Builder(eventName, MParticle.EventType.Transaction).info(contextInfo).build()));
        return messageList;
    }

    @Override
    public List<ReportingMessage> logEvent(CommerceEvent event) {
        List<ReportingMessage> messages = new LinkedList<ReportingMessage>();

        //This sends an event to Localytics using their more generic API's. This provids us with
        //backwards compatibility for clients who may have gathered data with older versions of our
        //sdk
        if (!KitUtils.isEmpty(event.getProductAction()) &&
                Product.PURCHASE.equalsIgnoreCase(event.getProductAction()) ||
                Product.REFUND.equalsIgnoreCase(event.getProductAction())) {
            Map<String, String> eventAttributes = new HashMap<String, String>();
            CommerceEventUtils.extractActionAttributes(event, eventAttributes);
            int multiplier = trackAsRawLtv ? 1 : 100;
            if (event.getProductAction().equalsIgnoreCase(Product.REFUND)) {
                multiplier *= -1;
            }
            double total = event.getTransactionAttributes().getRevenue() * multiplier;
            Localytics.tagEvent(String.format("eCommerce - %s", event.getProductAction(), eventAttributes, (long) total));
            messages.add(ReportingMessage.fromEvent(this, event));
        } else {
            List<MPEvent> eventList = CommerceEventUtils.expand(event);
            if (eventList != null) {
                for (int i = 0; i < eventList.size(); i++) {
                    try {
                        logEvent(eventList.get(i));
                        messages.add(ReportingMessage.fromEvent(this, event));
                    } catch (Exception e) {
                        Logger.warning("Failed to call tagEvent to Localytics kit: " + e.toString());
                    }
                }
            }
        }

        //Handle each type of commerce event with a specific Localytics reporting API, introduced 4.0
        if (!KitUtils.isEmpty(event.getProductAction())) {
            switch (event.getProductAction()) {
                case Product.PURCHASE:
                    messages.addAll(purchase(event));
                    break;
                case Product.CHECKOUT:
                    messages.addAll(checkout(event));
                    break;
                case Product.ADD_TO_CART:
                    messages.addAll(addToCart(event.getProducts()));
                    break;
                case Product.DETAIL:
                    messages.addAll(contentViewed(event));
            }
        }
        if (event.getImpressions() != null && event.getImpressions().size() > 0) {
            messages.addAll(contentViewed(event));
        }
        return messages;
    }

    private List<ReportingMessage> contentViewed(CommerceEvent event) {
        List<ReportingMessage> reportingMessages = new ArrayList<>();
        if (!KitUtils.isEmpty(event.getImpressions())) {
            for (Impression impression : event.getImpressions()) {
                for (Product product : impression.getProducts()) {
                    Localytics.tagContentViewed(impression.getListName(), product.getSku(), product.toString(), product.getCustomAttributes());
                }
                reportingMessages.add(ReportingMessage.fromEvent(this, new CommerceEvent.Builder(impression).build()));
            }
        } else if (!KitUtils.isEmpty(event.getProducts())) {

        }
        return reportingMessages;
    }

    private List<ReportingMessage> addToCart(List<Product> products) {
        int multiplier = trackAsRawLtv ? 1 : 100;

        List<ReportingMessage> reportingMessages = new ArrayList<>();
        for (Product product : products) {
            Localytics.tagAddedToCart(product.getName(), product.getSku(), product.getCategory(), multiplier * (long)product.getUnitPrice(), product.getCustomAttributes());

            //Build a new event for information that we actually reported, since we only report a portion of the Object.
            // this way we have a clean record on the server of exactly what we did report
            Product productCopy = new Product.Builder(product.getName(), product.getSku(), multiplier * product.getUnitPrice())
                    .category(product.getCategory())
                    .customAttributes(product.getCustomAttributes())
                    .build();
            CommerceEvent event = new CommerceEvent.Builder(Product.ADD_TO_CART, productCopy).build();
            reportingMessages.add(ReportingMessage.fromEvent(this, event));
        }
        return reportingMessages;
    }

    private List<ReportingMessage> refund(CommerceEvent event) {
        int multiplier = -1 * (trackAsRawLtv ? 1 : 100);

        if (event.getTransactionAttributes() == null || event.getTransactionAttributes().getRevenue() == null) { return null; }
        long quantity = event.getProducts() != null ? -1 * Math.abs((long)event.getProducts().size()) : 0;
        long revenue = multiplier * event.getTransactionAttributes().getRevenue().longValue();
        Localytics.tagCompletedCheckout(revenue, quantity, event.getCustomAttributes());

        //Build a new event for information that we actually reported, since we only report a portion of the Object.
        // this way we have a clean record on the server of exactly what we did report
        CommerceEvent.Builder builder = null;
        if (event.getProducts() != null) {
            for (Product product: event.getProducts()) {
                if (builder == null) {
                    builder = new CommerceEvent.Builder(event.getProductAction(), product);
                } else {
                    builder.addProduct(product);
                }
            }
        }
        CommerceEvent commerceEvent = null;
        if (builder != null) {
            builder.transactionAttributes(new TransactionAttributes().setRevenue(multiplier * event.getTransactionAttributes().getRevenue()));
            commerceEvent = builder.build();
        }

        return builder == null ? null : Collections.singletonList(ReportingMessage.fromEvent(this, commerceEvent));
    }

    private List<ReportingMessage> checkout(CommerceEvent event) {
        int multiplier = trackAsRawLtv ? 1 : 100;

        if (event.getTransactionAttributes() == null || event.getTransactionAttributes().getRevenue() == null) { return null; }
        long quantity = event.getProducts() != null ? (long)event.getProducts().size() : 0;
        long revenue = multiplier * event.getTransactionAttributes().getRevenue().longValue();
        Localytics.tagCompletedCheckout(revenue, quantity, event.getCustomAttributes());

        //Build a new event for information that we actually reported, since we only report a portion of the Object.
        // this way we have a clean record on the server of exactly what we did report
        CommerceEvent.Builder builder = null;
        if (event.getProducts() != null) {
            for (Product product: event.getProducts()) {
                if (builder == null) {
                    builder = new CommerceEvent.Builder(event.getProductAction(), product);
                } else {
                    builder.addProduct(product);
                }
            }
        }
        CommerceEvent commerceEvent = null;
        if (builder != null) {
            builder.transactionAttributes(new TransactionAttributes().setRevenue(multiplier * event.getTransactionAttributes().getRevenue()));
            commerceEvent = builder.build();
        }

        return builder == null ? null : Collections.singletonList(ReportingMessage.fromEvent(this, commerceEvent));
    }

    private List<ReportingMessage> purchase(CommerceEvent event) {
        int multiplier = trackAsRawLtv ? 1 : 100;

        List<ReportingMessage> reportingMessages = new ArrayList<>();
        for (Product product: event.getProducts()) {

            //Include transaction Id with each purchased product, so we can
            // 1) report the CommerceEvent legally (CommerceEvents with action=Purchase need a transactionId
            // 2) it makes sense for the client to have access to the transactionId for a purchased product,
            //      as a kind of key
            Map<String, String> customAttributes = product.getCustomAttributes() != null ? product.getCustomAttributes() : new HashMap<String, String>();
            customAttributes.put("transactionId", event.getTransactionAttributes().getId());
            Localytics.tagPurchased(product.getName(), product.getSku(), product.getCategory(), multiplier * (long)product.getUnitPrice(), customAttributes);

            //Build a new event for information that we actually reported, since we only report a portion of the Object.
            // this way we have a clean record on the server of exactly what we did report
            Product productCopy = new Product.Builder(product.getName(), product.getSku(), multiplier * product.getUnitPrice())
                    .category(product.getCategory())
                    .customAttributes(product.getCustomAttributes())
                    .build();
            CommerceEvent eventCopy = new CommerceEvent.Builder(Product.PURCHASE, productCopy).transactionAttributes(new TransactionAttributes().setId(event.getTransactionAttributes().getId())).build();
            reportingMessages.add(ReportingMessage.fromEvent(this, eventCopy));
        }
        return reportingMessages;
    }

    @Override
    public boolean willHandlePushMessage(Intent intent) {
        return intent.getExtras().containsKey("ll") &&
                MPUtility.isInstanceIdAvailable() &&
                KitUtils.isServiceAvailable(getContext(), GcmListenerService.class);
    }

    @Override
    public void onPushMessageReceived(Context context, Intent extras) {
        Intent service = new Intent(context, com.localytics.android.GcmListenerService.class);
        service.setAction("com.google.android.c2dm.intent.RECEIVE");
        service.putExtras(extras);
        context.startService(service);
    }

    @Override
    public boolean onPushRegistration(String token, String senderId) {
        Localytics.setPushRegistrationId(token);
        return true;
    }

    private Customer getCurrentCustomer() {
        Customer.Builder builder = new Customer.Builder();
        Map<MParticle.IdentityType, String> ids = MParticle.getInstance().getUserIdentities();
        if (ids.containsKey(MParticle.IdentityType.CustomerId)) {
            builder.setCustomerId(ids.get(MParticle.IdentityType.CustomerId));
        }
        if (ids.containsKey(MParticle.IdentityType.Email)) {
            builder.setEmailAddress(ids.get(MParticle.IdentityType.Email));
        }
        Map<String, String> attributes = MParticle.getInstance().getUserAttributes();
        if (attributes.containsKey(MParticle.UserAttributes.FIRSTNAME)) {
            builder.setFirstName(MParticle.UserAttributes.FIRSTNAME);
        }
        if (attributes.containsKey(MParticle.UserAttributes.LASTNAME)) {
            builder.setLastName(MParticle.UserAttributes.LASTNAME);
        }
        return builder.build();
    }
}
