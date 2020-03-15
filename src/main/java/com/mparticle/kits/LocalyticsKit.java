package com.mparticle.kits;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.NonNull;

import com.localytics.android.CallToActionListener;
import com.localytics.android.Campaign;
import com.localytics.android.Localytics;
import com.localytics.android.ReferralReceiver;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.internal.Logger;
import com.mparticle.internal.MPUtility;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;



public class LocalyticsKit extends KitIntegration implements KitIntegration.EventListener, KitIntegration.CommerceListener, KitIntegration.AttributeListener, KitIntegration.PushListener, CallToActionListener {
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
    public List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        try {
            customDimensionJson = new JSONArray(settings.get(CUSTOM_DIMENSIONS));
        } catch (Exception jse) {

        }
        trackAsRawLtv = Boolean.parseBoolean(settings.get(RAW_LTV));
        Localytics.setOption("ll_app_key", settings.get(API_KEY));
        Localytics.autoIntegrate((Application)context.getApplicationContext());

        //after a reset() call, we need to set Provivacy OptedOut back to false, so the kit can run normally
        Localytics.setCallToActionListener(this);
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
        Double duration = event.getLength();
        Map<String, String> info = event.getInfo();

        if (duration != null) {
            if (info == null) {
                info = new HashMap<String, String>();
            }
            info.put("event_duration", Double.toString(duration));
        }

        Localytics.tagEvent(event.getEventName(), info);
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
        if (!KitUtils.isEmpty(event.getProductAction()) && (
                event.getProductAction().equalsIgnoreCase(Product.PURCHASE)) ||
                event.getProductAction().equalsIgnoreCase(Product.REFUND)) {
            Map<String, String> eventAttributes = new HashMap<String, String>();
            CommerceEventUtils.extractActionAttributes(event, eventAttributes);
            int multiplier = trackAsRawLtv ? 1 : 100;
            if (event.getProductAction().equalsIgnoreCase(Product.REFUND)) {
                multiplier *= -1;
            }
            double total = event.getTransactionAttributes().getRevenue() * multiplier;
            Localytics.tagEvent(String.format("eCommerce - %s", event.getProductAction()), eventAttributes, (long) total);
            messages.add(ReportingMessage.fromEvent(this, event));
            return messages;
        }
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
        return messages;
    }

    @Override
    public boolean willHandlePushMessage(Intent intent) {
        return (intent.getExtras().containsKey("ll") || intent.getExtras().containsKey("localyticsUninstallTrackingPush")) &&
                MPUtility.isFirebaseAvailable();
    }

    @Override
    public void onPushMessageReceived(Context context, Intent extras) {
        Intent service;
        if (MPUtility.isFirebaseAvailable()) {
            service = new Intent(context, com.localytics.android.FirebaseService.class);
            service.setAction("com.google.firebase.MESSAGING_EVENT");
            service.putExtras(extras);
            context.startService(service);
        }

    }

    @Override
    public boolean onPushRegistration(String token, String senderId) {
        Localytics.setPushRegistrationId(token);
        return true;
    }

    @Override
    public void reset() {
        Localytics.setPrivacyOptedOut(true);
    }

    @Override
    public boolean localyticsShouldDeeplink(@NonNull String s, @NonNull Campaign campaign) {
        return false;
    }

    @Override
    public void localyticsDidOptOut(boolean b, @NonNull Campaign campaign) {

    }

    @Override
    public void localyticsDidPrivacyOptOut(boolean optedOut, @NonNull Campaign campaign) {
        if (optedOut && !getKitManager().isOptedOut()) {
            Localytics.setPrivacyOptedOut(false);
        }
    }

    @Override
    public boolean localyticsShouldPromptForLocationPermissions(@NonNull Campaign campaign) {
        return false;
    }
}
