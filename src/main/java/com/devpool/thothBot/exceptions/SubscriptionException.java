package com.devpool.thothBot.exceptions;

public class SubscriptionException extends Exception {
    public enum ExceptionCause {
        FREE_SLOTS_EXCEEDED,
        ADDRESS_ALREADY_OWNED_BY_OTHERS,
    }

    private ExceptionCause exceptionCause;

    private long numberOfOwnedNfts;
    private long numberOfCurrentSubscriptions;

    private String address;

    public SubscriptionException(ExceptionCause cause, String message) {
        super(message);
        this.exceptionCause = cause;
    }

    public ExceptionCause getExceptionCause() {
        return exceptionCause;
    }

    public void setExceptionCause(ExceptionCause exceptionCause) {
        this.exceptionCause = exceptionCause;
    }

    public long getNumberOfOwnedNfts() {
        return numberOfOwnedNfts;
    }

    public void setNumberOfOwnedNfts(long numberOfOwnedNfts) {
        this.numberOfOwnedNfts = numberOfOwnedNfts;
    }

    public long getNumberOfCurrentSubscriptions() {
        return numberOfCurrentSubscriptions;
    }

    public void setNumberOfCurrentSubscriptions(long numberOfCurrentSubscriptions) {
        this.numberOfCurrentSubscriptions = numberOfCurrentSubscriptions;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
