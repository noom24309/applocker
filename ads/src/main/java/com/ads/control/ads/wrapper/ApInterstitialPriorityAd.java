package com.ads.control.ads.wrapper;

public class ApInterstitialPriorityAd {
    private String highPriorityId;
    private String mediumPriorityId;
    private String normalPriorityId;
    private ApInterstitialAd highPriorityInterstitialAd;
    private ApInterstitialAd mediumPriorityInterstitialAd;
    private ApInterstitialAd normalPriorityInterstitialAd;

    public ApInterstitialPriorityAd(String highPriorityId, String mediumPriorityId, String normalPriorityId) {
        this.highPriorityId = highPriorityId;
        this.mediumPriorityId = mediumPriorityId;
        this.normalPriorityId = normalPriorityId;
        if (!this.highPriorityId.isEmpty() && this.highPriorityInterstitialAd == null) {
            this.highPriorityInterstitialAd = new ApInterstitialAd();
        }
        if (!this.mediumPriorityId.isEmpty() && this.mediumPriorityInterstitialAd == null) {
            this.mediumPriorityInterstitialAd = new ApInterstitialAd();
        }
        if (!this.normalPriorityId.isEmpty() && this.normalPriorityInterstitialAd == null) {
            this.normalPriorityInterstitialAd = new ApInterstitialAd();
        }
    }

    public void setHighPriorityId(String highPriorityId) {
        this.highPriorityId = highPriorityId;
        if (!this.highPriorityId.isEmpty() && this.highPriorityInterstitialAd == null) {
            this.highPriorityInterstitialAd = new ApInterstitialAd();
        }
    }

    public void setMediumPriorityId(String mediumPriorityId) {
        this.mediumPriorityId = mediumPriorityId;
        if (!this.mediumPriorityId.isEmpty() && this.mediumPriorityInterstitialAd == null) {
            this.mediumPriorityInterstitialAd = new ApInterstitialAd();
        }
    }

    public void setNormalPriorityId(String normalPriorityId) {
        this.normalPriorityId = normalPriorityId;
        if (!this.normalPriorityId.isEmpty() && this.normalPriorityInterstitialAd == null) {
            this.normalPriorityInterstitialAd = new ApInterstitialAd();
        }
    }

    public String getHighPriorityId() {
        return highPriorityId;
    }

    public ApInterstitialAd getHighPriorityInterstitialAd() {
        return highPriorityInterstitialAd;
    }

    public String getMediumPriorityId() {
        return mediumPriorityId;
    }

    public ApInterstitialAd getMediumPriorityInterstitialAd() {
        return mediumPriorityInterstitialAd;
    }

    public String getNormalPriorityId() {
        return normalPriorityId;
    }

    public ApInterstitialAd getNormalPriorityInterstitialAd() {
        return normalPriorityInterstitialAd;
    }
}
