package com.devpool.thothBot.model.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Value {
    @JsonProperty("@value")
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Value{" +
                "value='" + value + '\'' +
                '}';
    }
}
