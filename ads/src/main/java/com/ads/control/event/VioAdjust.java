package com.ads.control.event;

import android.content.Context;

import com.ads.control.ads.VioAdmob;

import com.google.android.gms.ads.AdValue;

public class VioAdjust {
    private static String eventNamePurchase = "";

    public static void setEventNamePurchase(String eventNamePurchase) {
        VioAdjust.eventNamePurchase = eventNamePurchase;
    }

    public static void trackAdRevenue(String id) {

    }

    public static void onTrackEvent(String eventName) {

    }

    public static void onTrackEvent(String eventName, String id) {

    }

    public static void onTrackRevenue(String eventName, float revenue, String currency) {

    }

    public static void onTrackRevenuePurchase(float revenue, String currency) {
        onTrackRevenue(eventNamePurchase, revenue, currency);
    }

    public static void pushTrackEventAdmob(AdValue adValue) {

    }


    static void logPaidAdImpressionValue(double revenue, String currency) {

    }
}
