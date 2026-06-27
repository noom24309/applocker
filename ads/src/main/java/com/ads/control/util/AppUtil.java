package com.ads.control.util;

import androidx.lifecycle.MutableLiveData;

public class AppUtil {
    public static Boolean VARIANT_DEV = true;

    /**
     * current total revenue for paid_ad_impression_value_0.01 event
     */
    public static float currentTotalRevenue001Ad;

    public static MutableLiveData<String> messageInit = new MutableLiveData<>();
}
