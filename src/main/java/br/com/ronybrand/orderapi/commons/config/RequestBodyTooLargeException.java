package br.com.ronybrand.orderapi.commons.config;

import java.io.IOException;

/** Thrown from the request stream when a body without a Content-Length passes the configured limit. */
public class RequestBodyTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    public RequestBodyTooLargeException(final long limitBytes) {
        super("Request body exceeds the maximum allowed size of " + limitBytes + " bytes");
    }
}
