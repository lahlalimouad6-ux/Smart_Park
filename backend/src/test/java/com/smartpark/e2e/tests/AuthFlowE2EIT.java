package com.smartpark.e2e.tests;

import com.smartpark.e2e.BaseE2ETest;
import com.smartpark.e2e.pages.LoginPage;
import com.smartpark.e2e.pages.RegisterPage;
import com.smartpark.e2e.support.ApiClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthFlowE2EIT extends BaseE2ETest {
    @Test
    void registerThenLogin() {
        clearLocalStorage();

        String email = ApiClient.uniqueEmail();
        String password = "Password123!";

        go("/register");
        RegisterPage register = new RegisterPage(driver, wait);
        register.register("Test", "E2E", email, password);
        register.waitForSuccess();
        register.waitRedirectToLogin();

        LoginPage login = new LoginPage(driver, wait);
        login.login(email, password);
        login.waitForRedirectToParkingsOrAdmin();

        String url = driver.getCurrentUrl();
        assertTrue(url.contains("/parkings"), "Expected redirect to /parkings, got: " + url);
    }
}
