package com.storex.order.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint tao loi co chu dich de kiem tra log co stack trace.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final Logger log = LoggerFactory.getLogger(DebugController.class);

    @GetMapping("/error")
    public ResponseEntity<Map<String, String>> triggerError() {
        try {
            processPayment();
        } catch (Exception e) {
            log.error("Payment processing failed for orderId=123", e);
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "simulated failure"));
    }

    private void processPayment() {
        try {
            Integer.parseInt("abc");
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid payment amount", e);
        }
    }
}
