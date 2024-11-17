package com.devpool.thothBot.unit;

import com.devpool.thothBot.model.model.DrepMetadata;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrepMetadataTest {
    private static final String METADATA_FOLDER = "test-data/gov/metadata";


    @Test
    void testMetadataDeserialization() throws IOException {
        var metadata = readJsonMetadata(METADATA_FOLDER + "/metadata1.json");
        assertEquals("Alfred Moesker", metadata.getBody().getGivenName().toString());


        metadata = readJsonMetadata(METADATA_FOLDER + "/metadata2.json");
        assertEquals("PRIDE", metadata.getBody().getGivenName().toString());

        metadata = readJsonMetadata(METADATA_FOLDER + "/CardanoYoda.json");
        assertEquals("CardanoYoda", metadata.getBody().getGivenName().toString());

        metadata = readJsonMetadata(METADATA_FOLDER + "/metadata3.json");
        assertEquals("LloydDuhon", metadata.getBody().getGivenName().toString());
    }


    private DrepMetadata readJsonMetadata(String filePath) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        String f = Objects.requireNonNull(classLoader.getResource(filePath)).getFile();
        var fileContent = Files.readAllBytes(Path.of(f));
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(fileContent, DrepMetadata.class);
    }

}
