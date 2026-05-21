package com.smartpark.e2e;

import java.net.URI;

public final class E2EConfig {
    public static final URI FRONTEND_BASE_URI = URI.create(System.getProperty("e2e.frontendBaseUrl", "http://localhost:3001"));
    public static final URI BACKEND_BASE_URI = URI.create(System.getProperty("e2e.backendBaseUrl", "http://localhost:8080"));
    public static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("e2e.headless", "true"));
    public static final int DEFAULT_TIMEOUT_SECONDS = Integer.parseInt(System.getProperty("e2e.timeoutSeconds", "15"));

    private E2EConfig() {}
}
