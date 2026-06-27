package com.ads.control.ads.nativeAds;

import androidx.recyclerview.widget.RecyclerView;



public class VioAdAdapter {
    private AdmobRecyclerAdapter admobRecyclerAdapter;

    public VioAdAdapter(AdmobRecyclerAdapter admobRecyclerAdapter) {
        this.admobRecyclerAdapter = admobRecyclerAdapter;
    }


    public RecyclerView.Adapter getAdapter() {

        return admobRecyclerAdapter;

    }



    public int getOriginalPosition(int pos) {

        if (admobRecyclerAdapter !=null){
           return admobRecyclerAdapter.getOriginalPosition(pos);
        }
        return 0;
    }





    public void setCanRecyclable(boolean canRecyclable){
        if(admobRecyclerAdapter != null){
            admobRecyclerAdapter.setCanRecyclable(canRecyclable);
        }
    }

    public void setNativeFullScreen(boolean nativeFullScreen){
        if(admobRecyclerAdapter != null){
            admobRecyclerAdapter.setNativeFullScreen(nativeFullScreen);
        }
    }
}
