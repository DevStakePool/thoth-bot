package com.devpool.thothBot.telegram.model;

public class DrepMetadata {
    private Body body;

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "DrepMetadata{" +
                "body=" + body +
                '}';
    }
}