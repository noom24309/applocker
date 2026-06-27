package com.ads.control.config;


public class AdjustConfig {

    /**
     * adjustToken enable adjust and setup adjust token
     */
    private String adjustToken = "";

    /**
     * eventNamePurchase push event to adjust when user purchased
     */
    private String eventNamePurchase = "";

    /**
     * eventNamePurchase push event to adjust when ad impression
     */
    private String eventAdImpression = "";

    public AdjustConfig(String adjustToken) {
        this.adjustToken = adjustToken;
    }

    public String getAdjustToken() {
        return adjustToken;
    }

    public void setAdjustToken(String adjustToken) {
        this.adjustToken = adjustToken;
    }

    public String getEventNamePurchase() {
        return eventNamePurchase;
    }

    public void setEventNamePurchase(String eventNamePurchase) {
        this.eventNamePurchase = eventNamePurchase;
    }

    public String getEventAdImpression() {
        return eventAdImpression;
    }

    public void setEventAdImpression(String eventAdImpression) {
        this.eventAdImpression = eventAdImpression;
    }
}
