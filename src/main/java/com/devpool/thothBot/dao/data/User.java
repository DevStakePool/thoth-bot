package com.devpool.thothBot.dao.data;

import rest.koios.client.backend.api.account.model.AccountAddress;

import java.util.ArrayList;
import java.util.List;

public class User {
    private Long id;
    private Long chatId;
    private String stakeAddr;
    private Integer lastBlockHeight;
    private List<String> accountAddresses;

    public User(Long chatId, String stakeAddr, Integer blockNumber) {
        this.chatId = chatId;
        this.stakeAddr = stakeAddr;
        this.lastBlockHeight = blockNumber;
    }

    public User() {
    }

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", stakeAddr='" + stakeAddr + '\'' +
                ", accountAddresses.size()=" + (accountAddresses != null ? accountAddresses.size() : "null") +
                ", lastBlockHeight=" + lastBlockHeight +
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

    public String getStakeAddr() {
        return stakeAddr;
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

    public void setStakeAddr(String stakeAddr) {
        this.stakeAddr = stakeAddr;
    }

    public List<String> getAccountAddresses() {
        return accountAddresses;
    }

    public void setAccountAddresses(List<String> accountAddresses) {
        this.accountAddresses = accountAddresses;
    }
}
