package com.storex.order.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Goi sang user-service de kiem tra user ton tai truoc khi tao don hang.
 */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder,
                      @Value("${storex.user-service.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * @return true neu user ton tai, false neu user-service tra 404.
     * Cac loi khac (mat ket noi, 5xx...) duoc nem ra duoi dang RestClientException.
     */
    public boolean userExists(Long userId) {
        log.debug("Calling user-service for userId={}", userId);
        try {
            restClient.get().uri("/api/users/{id}", userId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
