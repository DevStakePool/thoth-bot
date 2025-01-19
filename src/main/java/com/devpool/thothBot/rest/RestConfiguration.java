package com.devpool.thothBot.rest;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Arrays;

@Configuration
public class RestConfiguration {

    @Bean
    public RestTemplate restTemplate() {

        RestTemplate restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        // support "text/plain" and application/json
        converter.setSupportedMediaTypes(Arrays.asList(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));

        restTemplate.getMessageConverters().add(converter);

        return restTemplate;
    }
}
