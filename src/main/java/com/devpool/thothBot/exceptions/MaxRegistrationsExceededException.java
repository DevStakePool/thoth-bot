package com.devpool.thothBot.exceptions;

public class MaxRegistrationsExceededException extends Exception {
    private Integer maxRegistrationsAllowed;

    public MaxRegistrationsExceededException() {
    }

    public MaxRegistrationsExceededException(String message) {
        super(message);
    }

    public MaxRegistrationsExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    public MaxRegistrationsExceededException(Throwable cause) {
        super(cause);
    }

    public Integer getMaxRegistrationsAllowed() {
        return maxRegistrationsAllowed;
    }

    public void setMaxRegistrationsAllowed(Integer maxRegistrationsAllowed) {
        this.maxRegistrationsAllowed = maxRegistrationsAllowed;
    }
}
