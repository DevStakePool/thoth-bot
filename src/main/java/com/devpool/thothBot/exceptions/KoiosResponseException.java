package com.devpool.thothBot.exceptions;

public class KoiosResponseException extends Exception {
    public KoiosResponseException() {
    }

    public KoiosResponseException(String message) {
        super(message);
    }

    public KoiosResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
