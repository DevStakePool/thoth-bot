package com.devpool.thothBot.koios;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rest.koios.client.backend.api.base.exception.ApiException;
import rest.koios.client.backend.factory.BackendFactory;
import rest.koios.client.backend.factory.BackendService;

import javax.annotation.PostConstruct;

@Component
public class KoiosFacade {
    private static final Logger LOG = LoggerFactory.getLogger(KoiosFacade.class);
    private BackendService koiosService;

    @PostConstruct
    public void post() throws ApiException {
        this.koiosService = BackendFactory.getKoiosMainnetService();
        LOG.info("KOIOS Facade initialised");
    }

    public BackendService getKoiosService() {
        return koiosService;
    }
}
