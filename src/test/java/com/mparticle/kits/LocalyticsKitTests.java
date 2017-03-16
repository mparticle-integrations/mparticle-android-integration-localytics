package com.mparticle.kits;


import android.content.Context;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalyticsKitTests {

    private KitIntegration getKit() {
        return new LocalyticsKit();
    }

    @Test
    public void testGetName() throws Exception {
        String name = getKit().getName();
        assertTrue(name != null && name.length() > 0);
    }

    /**
     * Kit *should* throw an exception when they're initialized with the wrong settings.
     *
     */
    @Test
    public void testOnKitCreate() throws Exception{
        Exception e = null;
        try {
            KitIntegration kit = getKit();
            Map settings = new HashMap<>();
            settings.put("fake setting", "fake");
            kit.onKitCreate(settings, Mockito.mock(Context.class));
        }catch (Exception ex) {
            e = ex;
        }
        assertNotNull(e);
    }

    @Test
    public void testClassName() throws Exception {
        KitIntegrationFactory factory = new KitIntegrationFactory();
        Map<Integer, String> integrations = factory.getKnownIntegrations();
        String className = getKit().getClass().getName();
        for (Map.Entry<Integer, String> entry : integrations.entrySet()) {
            if (entry.getValue().equals(className)) {
                return;
            }
        }
        fail(className + " not found as a known integration.");
    }

    @Test
    public void testGetCustomDimensionIndex() throws Exception {
        LocalyticsKit kit = (LocalyticsKit) getKit();
        Map<String, String> settings = new HashMap<>();
        kit.setConfiguration(Mockito.mock(KitConfiguration.class));
        settings.put(LocalyticsKit.API_KEY, "test");
        settings.put(LocalyticsKit.CUSTOM_DIMENSIONS, "[ { \"maptype\":\"UserAttributeClass.Name\", \"value\":\"Dimension 0\", \"map\":\"foo-key-0\" }, { \"maptype\":\"UserAttributeClass.Name\", \"value\":\"Dimension 1\", \"map\":\"foo-key-1\" }, ]");
        Mockito.when(kit.getSettings()).thenReturn(settings);
        int index = kit.getDimensionIndexForAttribute(null);
        assertEquals(-1, index);
        index = kit.getDimensionIndexForAttribute("foo-key-0");
        assertEquals(0, index);
        index = kit.getDimensionIndexForAttribute("fOo-Key-0");
        assertEquals(0, index);
        index = kit.getDimensionIndexForAttribute("fOo-Key-1");
        assertEquals(1, index);
    }

}