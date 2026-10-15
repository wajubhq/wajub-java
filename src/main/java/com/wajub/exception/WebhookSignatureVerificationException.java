package com.wajub.exception;

/** Returned when webhook verification fails. */
public class WebhookSignatureVerificationException extends RuntimeException {

    public WebhookSignatureVerificationException(String message) {
        super(message);
    }
}
