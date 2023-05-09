package com.devpool.thothBot.dao.data;

public class Asset {
    private Long id;
    private String policyId;
    private String assetName;
    private Integer decimals;

    public Asset(Long id, String policyId, String assetName, Integer decimals) {
        this.id = id;
        this.policyId = policyId;
        this.assetName = assetName;
        this.decimals = decimals;
    }

    public Asset() {
    }

    @Override
    public String toString() {
        return "Asset{" +
                "id=" + id +
                ", policyId='" + policyId + '\'' +
                ", assetName='" + assetName + '\'' +
                ", decimals=" + decimals +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
