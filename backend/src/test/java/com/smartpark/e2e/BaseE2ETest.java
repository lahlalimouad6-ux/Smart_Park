package com.smartpark.e2e;

import com.smartpark.e2e.support.ServerHealth;
import com.smartpark.e2e.support.WebDriverFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Assumptions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public abstract class BaseE2ETest {
    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void requireServers() {
        boolean frontendOk = ServerHealth.isOk(E2EConfig.FRONTEND_BASE_URI);
        boolean backendOk = ServerHealth.isOk(E2EConfig.BACKEND_BASE_URI.resolve("/api/parkings"));
        Assumptions.assumeTrue(frontendOk && backendOk, () ->
                "E2E skipped: start servers first. Frontend=" + E2EConfig.FRONTEND_BASE_URI + " Backend=" + E2EConfig.BACKEND_BASE_URI);
    }

    @BeforeEach
    void setUpDriver() {
        driver = WebDriverFactory.create();
        wait = new WebDriverWait(driver, Duration.ofSeconds(E2EConfig.DEFAULT_TIMEOUT_SECONDS));
    }

    @AfterEach
    void tearDownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void go(String path) {
        String base = E2EConfig.FRONTEND_BASE_URI.toString().replaceAll("/$", "");
        String p = path.startsWith("/") ? path : "/" + path;
        driver.get(base + p);
    }

    protected void setLocalStorageUserJson(String userJson) {
        go("/");
        ((JavascriptExecutor) driver).executeScript("localStorage.setItem('user', arguments[0]);", userJson);
    }

    protected void clearLocalStorage() {
        go("/");
        ((JavascriptExecutor) driver).executeScript("localStorage.clear();");
    }
}
