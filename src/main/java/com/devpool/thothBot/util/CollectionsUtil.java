package com.devpool.thothBot.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CollectionsUtil {
    private CollectionsUtil() {
        // private
    }

    public static <T> Stream<List<T>> batchesList(List<T> source, int batchSize) {
        if (batchSize <= 0)
            throw new IllegalArgumentException("batchSize cannot be negative, batchSize=" + batchSize);
        int size = source.size();
        if (size == 0)
            return Stream.empty();

        int fullChunks = (size - 1) / batchSize;
        return IntStream.range(0, fullChunks + 1).mapToObj(
                n -> source.subList(n * batchSize, n == fullChunks ? size : (n + 1) * batchSize));
    }

    public static <K, V> List<Map<K, V>> batchesMap(Map<K, V> inputMap, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be greater than 0. batchSize=" + batchSize);
        }

        List<Map<K, V>> batches = new ArrayList<>();
        List<K> keys = new ArrayList<>(inputMap.keySet());

        for (int i = 0; i < keys.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, keys.size());
            Map<K, V> batchMap = new HashMap<>();

            for (int j = i; j < endIndex; j++) {
                K key = keys.get(j);
                batchMap.put(key, inputMap.get(key));
            }

            batches.add(batchMap);
        }

        return batches;
    }

    public static void main(String[] args) {
        // Example usage
        Map<Integer, String> exampleMap = new HashMap<>();
        exampleMap.put(1, "One");
        exampleMap.put(2, "Two");
        exampleMap.put(3, "Three");
        exampleMap.put(4, "Four");
        exampleMap.put(5, "Five");
        exampleMap.put(6, "Six");

        int batchSize = 2;
        List<Map<Integer, String>> result = batchesMap(exampleMap, batchSize);

        // Printing the batches
        for (int i = 0; i < result.size(); i++) {
            System.out.println("Batch " + (i + 1) + ": " + result.get(i));
        }
    }
}
