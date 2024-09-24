package com.devpool.thothBot.telegram.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Body {
    private String givenName;

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    @Override
    public String toString() {
        return "Body{" +
                "givenName='" + givenName + '\'' +
                '}';
    }
}
