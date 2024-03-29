package com.devpool.thothBot.dao.data;

import java.util.ArrayList;
import java.util.List;

public class User {
    private static final String STAKE_ADDR_PREFIX = "stake1";
    private Long id;
    private Long chatId;
    private String address;
    private Integer lastBlockHeight;
    private Integer lastEpochNumber;

    public User(Long chatId, String address, Integer blockNumber, Integer lastEpochNumber) {
        this.chatId = chatId;
        this.address = address;
        this.lastBlockHeight = blockNumber;
        this.lastEpochNumber = lastEpochNumber;
    }

    public User() {
    }

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", address='" + address + '\'' +
                ", lastBlockHeight=" + lastBlockHeight +
                ", lastEpochNumber=" + lastEpochNumber +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getAddress() {
        return address;
    }

    public boolean isStakeAddress() {
        return isStakingAddress(this.address);
    }

    public boolean isNormalAddress() {
        return isNormalAddress(this.address);
    }

    public Integer getLastBlockHeight() {
        return this.lastBlockHeight;
    }

    public void setLastBlockHeight(Integer lastBlockHeight) {
        this.lastBlockHeight = lastBlockHeight;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getLastEpochNumber() {
        return lastEpochNumber;
    }

    public void setLastEpochNumber(Integer lastEpochNumber) {
        this.lastEpochNumber = lastEpochNumber;
    }

    public static boolean isStakingAddress(String address) {
        return address.startsWith(STAKE_ADDR_PREFIX);
    }

    public static boolean isNormalAddress(String address) {
        return !isStakingAddress(address);
    }
}
