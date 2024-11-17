package com.devpool.thothBot.model.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Setter
@Getter
public class Body {
    private Object givenName;

    public Object getGivenName() {
        if (givenName instanceof HashMap) {
            return ((HashMap<?, ?>) givenName).get("@value");
        }
        // We assume it's a string
        return givenName;
    }

    public void setGivenName(Object givenName) {
        this.givenName = givenName;
    }

    @Override
    public String toString() {
        return "Body{" +
                "givenName='" + givenName + '\'' +
                '}';
    }
}
