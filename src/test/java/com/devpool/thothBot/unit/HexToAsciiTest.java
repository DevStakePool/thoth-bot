package com.devpool.thothBot.unit;

import com.devpool.thothBot.scheduler.AbstractCheckerTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HexToAsciiTest {

    // Returns the correct ASCII string for a given hexadecimal string with even length
    @Test
    public void test_behaviour_aaa() {
        String hexStr = "616161";
        String expected = "aaa";
        String result = AbstractCheckerTask.hexToAscii(hexStr);
        assertEquals(expected, result);
    }

    // Returns an empty string for an empty hexadecimal string
    @Test
    public void test_behaviour_empty_hex_string() {
        String hexStr = "";
        String expected = "";
        String result = AbstractCheckerTask.hexToAscii(hexStr);
        assertEquals(expected, result);
    }

    // Returns the input hexadecimal string if the output ASCII string cannot be encoded in US-ASCII
    @Test
    public void test_behaviour_non_us_ascii_encoding() {
        String hexStr = "c3a4c3b6c3bc";
        String expected = "...a4c3b6c3bc";
        String result = AbstractCheckerTask.hexToAscii(hexStr);
        assertEquals(expected, result);
    }

}
