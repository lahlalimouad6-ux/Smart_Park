package com.smartpark.e2e.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.e2e.BaseE2ETest;
import com.smartpark.e2e.E2EConfig;
import com.smartpark.e2e.pages.AdminReservationsPage;
import com.smartpark.e2e.support.ApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminReservationsE2EIT extends BaseE2ETest {
    private static volatile String adminUserJson;
    private static volatile String conducteurEmail;

    @BeforeAll
    static void seedData() {
        ObjectMapper om = new ObjectMapper();
        ApiClient api = new ApiClient(om, E2EConfig.BACKEND_BASE_URI);

        String adminEmail = ApiClient.uniqueEmail();
        String adminPassword = "Password123!";
        api.signup("Admin", "E2E", adminEmail, adminPassword, "ADMIN");
        ApiClient.AuthSession adminSession = api.signin(adminEmail, adminPassword);
        adminUserJson = adminSession.asFrontendLocalStorageUserJson();

        String parkingName = "E2E Admin Parking " + System.currentTimeMillis();
        long parkingId = api.createParking(
                adminSession.token(),
                parkingName,
                "E2E City",
                "2 Rue E2E",
                "48.8566,2.3522",
                "3.50",
                8
        );

        String userEmail = ApiClient.uniqueEmail();
        String userPassword = "Password123!";
        api.signup("User", "E2E", userEmail, userPassword, "CONDUCTEUR");
        ApiClient.AuthSession userSession = api.signin(userEmail, userPassword);
        conducteurEmail = userSession.rawJson().path("email").asText(userEmail);

        long spotId = api.getSpotIdByNumeroPlace(parkingId, "A01");
        LocalDateTime start = LocalDateTime.now().plusHours(5).withSecond(0).withNano(0);
        api.createReservation(userSession.token(), spotId, start, start.plusHours(1));
    }

    @Test
    void adminCanSearchReservations() {
        clearLocalStorage();
        setLocalStorageUserJson(adminUserJson);

        go("/admin/reservations");
        AdminReservationsPage page = new AdminReservationsPage(driver, wait);
        page.waitLoaded();

        page.search(conducteurEmail);
        wait.until(d -> page.hasRowContaining(conducteurEmail));
        assertTrue(page.hasRowContaining(conducteurEmail), "Expected to find reservation row containing: " + conducteurEmail);
    }
}
