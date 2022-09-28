package com.devpool.thothBot.dao.data;

import java.util.List;

public class User {
    private Long id;
    private Long chatId;
    private String stakeAddr;
    private Integer lastBlockHeight;

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
}
