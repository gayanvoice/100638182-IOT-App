package com.uod.uodiotapp;

import com.robinhood.spark.SparkAdapter;

public class IotSparkAdapter extends SparkAdapter {
    private double[] yData;
    public IotSparkAdapter(double[] yData) {
        this.yData = yData;
    }

    @Override
    public int getCount() {
        return yData.length;
    }

    @Override
    public Object getItem(int index) {
        return yData[index];
    }

    @Override
    public float getY(int index) {
//        return yData[index];
        return (float) yData[index];
    }
}
