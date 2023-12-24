package com.devpool.thothBot.unit;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import rest.koios.client.backend.api.account.model.AccountAsset;
import rest.koios.client.backend.api.base.common.Asset;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class HexToAsciiTest {

    // Returns the correct ASCII string for a given hexadecimal string with even length
    @Test
    public void test_behaviour_aaa() throws JsonProcessingException {
        String hexStr = "616161";
        String expected = "aaa";
        String result = AbstractCheckerTask.hexToAscii(createAsset(hexStr, "whatever"));
        assertEquals(expected, result);
    }

    // Returns an empty string for an empty hexadecimal string
    @Test
    public void test_behaviour_empty_hex_string() throws JsonProcessingException {
        String hexStr = "";
        String expected = "";
        String result = AbstractCheckerTask.hexToAscii(createAsset(hexStr, "whatever"));
        assertEquals(expected, result);
    }

    // Returns the input hexadecimal string if the output ASCII string cannot be encoded in US-ASCII
    @Test
    public void test_behaviour_non_us_ascii_encoding() throws JsonProcessingException {
        String hexStr = "c3a4c3b6c3bc";
        String expected = "...a4c3b6c3bc";
        String result = AbstractCheckerTask.hexToAscii(createAsset(hexStr, "whatever"));
        assertEquals(expected, result);
    }

    @Test
    public void test_behaviour_non_us_ascii_encoding_with_handle() throws JsonProcessingException {
        String hexStr = "c3a4c3b6c3bc";
        String expected = "...a4c3b6c3bc";
        String result = AbstractCheckerTask.hexToAscii(createAsset(hexStr, AbstractCheckerTask.ADA_HANDLE_POLICY_ID));
        assertNotEquals(expected, result);
    }

    private Asset createAsset(String assetNameHex, String policyId) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        StringBuilder assetJson = new StringBuilder();
        /**
         *   {
         *     "policy_id": "4e22095c8ffb8113206e788af55986eb70577f4e7b32f6ded0b0a855",
         *     "asset_name": "54686f7468506c75734f6e653230323441313037",
         *     "fingerprint": "asset1e60w4wdrkv77zr3m5zzyhf03r4lwuau6uyh7lm",
         *     "decimals": 0,
         *     "quantity": "1"
         *   }
         */
        assetJson.append("{");
        assetJson.append("\"policy_id\":").append('"').append(policyId).append("\",");
        assetJson.append("\"asset_name\":").append('"').append(assetNameHex).append("\"");
        assetJson.append("}");

        return mapper.readValue(assetJson.toString(), new TypeReference<>() {
        });
    }
}
