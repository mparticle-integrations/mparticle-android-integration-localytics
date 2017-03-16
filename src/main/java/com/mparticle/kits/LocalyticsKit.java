package com.mparticle.kits;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;

import com.localytics.android.GcmListenerService;
import com.localytics.android.Localytics;
import com.localytics.android.LocalyticsActivityLifecycleCallbacks;
import com.localytics.android.ReferralReceiver;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.internal.ConfigManager;
import com.mparticle.internal.MPUtility;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class LocalyticsKit extends KitIntegration implements KitIntegration.EventListener, KitIntegration.CommerceListener, KitIntegration.AttributeListener, KitIntegration.PushListener, KitIntegration.ActivityListener{
    static final String API_KEY = "appKey";
    static final String CUSTOM_DIMENSIONS = "customDimensions";
    static final String RAW_LTV = "trackClvAsRawValue";
    private JSONArray customDimensionJson = null;
    private boolean trackAsRawLtv = false;
    private LocalyticsActivityLifecycleCallbacks callbacks;

    @Override
    public String getName() {
        return "Localytics";
    }

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
        if (callbacks == null) {
            callbacks = new LocalyticsActivityLifecycleCallbacks(getContext(), getSettings().get(API_KEY));
        }
        trackAsRawLtv = Boolean.parseBoolean(getSettings().get(RAW_LTV));
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
                ConfigManager.log(MParticle.LogLevel.DEBUG, "Exception while mapping mParticle user attributes to Localytics custom dimensions: " + e.toString());
            }
        }
        return -1;
    }

    @Override
    public void setUserAttribute(String key, String value) {
        int dimensionIndex = getDimensionIndexForAttribute(key);
        if (dimensionIndex >= 0) {
            Localytics.setCustomDimension(dimensionIndex, value);
        } else {
            if (key.equalsIgnoreCase(MParticle.UserAttributes.FIRSTNAME)) {
                Localytics.setCustomerFirstName(value);
            } else if (key.equalsIgnoreCase(MParticle.UserAttributes.LASTNAME)) {
                Localytics.setCustomerLastName(value);
            } else {
                Localytics.setProfileAttribute(KitUtils.sanitizeAttributeKey(key), value);
            }
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
            Localytics.tagEvent(String.format("eCommerce - %s", event.getProductAction(), eventAttributes, (long) total));
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
                    ConfigManager.log(MParticle.LogLevel.WARNING, "Failed to call tagEvent to Localytics kit: " + e.toString());
                }
            }
        }
        return messages;
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

    @Override
    public List<ReportingMessage> onActivityCreated(Activity activity, Bundle bundle) {
        callbacks.onActivityCreated(activity, bundle);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivityStarted(Activity activity) {
        callbacks.onActivityStarted(activity);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivityResumed(Activity activity) {
        callbacks.onActivityResumed(activity);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivityPaused(Activity activity) {
        callbacks.onActivityPaused(activity);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivityStopped(Activity activity) {
        callbacks.onActivityStopped(activity);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        callbacks.onActivitySaveInstanceState(activity, bundle);
        return null;
    }

    @Override
    public List<ReportingMessage> onActivityDestroyed(Activity activity) {
        callbacks.onActivityDestroyed(activity);
        return null;
    }
}
