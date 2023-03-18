package com.devpool.thothBot.oracle;

/**
 * Generic Cardano Oracle interface
 */
public interface ICardanoOracle {
    /**
     * Get the latest Cardano price in USD. It can return null if the price is too old (implementation dependent)
     *
     * @return the USD price or null
     */
    Double getPriceUsd();
}
