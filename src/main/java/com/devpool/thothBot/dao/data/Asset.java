package com.devpool.thothBot.dao.data;

public class Asset {
    private String policyId;
    private String assetName;
    private Integer decimals;

    public Asset(String policyId, String assetName, Integer decimals) {
        this.policyId = policyId;
        this.assetName = assetName;
        this.decimals = decimals;
    }

    public Asset() {
    }

    @Override
    public String toString() {
        return "Asset{" +
                "policyId='" + policyId + '\'' +
                ", assetName='" + assetName + '\'' +
                ", quantity=" + decimals +
                '}';
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
    }
}
