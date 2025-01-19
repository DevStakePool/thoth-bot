package com.devpool.thothBot.unit;

import com.devpool.thothBot.model.model.DrepMetadata;
import com.devpool.thothBot.rest.RestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateTest {
    @Test
    void testRestTemplate() throws URISyntaxException {
        var restTemplate = new RestConfiguration().restTemplate();
        //String url1 = "https://ipfs.io/ipfs/QmRin6EhtanViF7g7XbL1ZkE477zicy2Sh1N6snQFfgWvK";
        String url2 = "https://raw.githubusercontent.com/colll78/drep-metadata-storage/main/Phil%20UPLC.jsonld";

        //ResponseEntity<DrepMetadata> resp1 = restTemplate.getForEntity(new URI(url1), DrepMetadata.class);
        //assertEquals(200, resp1.getStatusCodeValue());
        ResponseEntity<DrepMetadata> resp2 = restTemplate.getForEntity(new URI(url2), DrepMetadata.class);
        assertTrue(resp2.getStatusCode().is2xxSuccessful());

    }
}
