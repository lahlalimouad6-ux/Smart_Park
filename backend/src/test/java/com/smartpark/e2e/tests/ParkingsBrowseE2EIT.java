package com.smartpark.e2e.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.e2e.BaseE2ETest;
import com.smartpark.e2e.E2EConfig;
import com.smartpark.e2e.pages.ParkingsPage;
import com.smartpark.e2e.pages.ParkingDetailPage;
import com.smartpark.e2e.support.ApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParkingsBrowseE2EIT extends BaseE2ETest {
    private static volatile String conducteurUserJson;
    private static volatile String parkingName;

    @BeforeAll
    static void seedData() {
        ObjectMapper om = new ObjectMapper();
        ApiClient api = new ApiClient(om, E2EConfig.BACKEND_BASE_URI);

        String adminEmail = ApiClient.uniqueEmail();
        String adminPassword = "Password123!";
        api.signup("Admin", "E2E", adminEmail, adminPassword, "ADMIN");
        ApiClient.AuthSession adminSession = api.signin(adminEmail, adminPassword);

        parkingName = "E2E Browse Parking " + System.currentTimeMillis();
        api.createParking(
                adminSession.token(),
                parkingName,
                "E2E City",
                "3 Rue E2E",
                "48.8566,2.3522",
                "3.50",
                8
        );

        String email = ApiClient.uniqueEmail();
        String password = "Password123!";
        api.signup("User", "E2E", email, password, "CONDUCTEUR");
        conducteurUserJson = api.signin(email, password).asFrontendLocalStorageUserJson();
    }

    @Test
    void searchAndOpenParking() {
        clearLocalStorage();
        setLocalStorageUserJson(conducteurUserJson);

        go("/parkings");
        ParkingsPage page = new ParkingsPage(driver, wait);
        page.waitLoaded();

        page.search(parkingName);
        wait.until(d -> page.hasParkingCard(parkingName));
        assertTrue(page.hasParkingCard(parkingName), "Expected to see parking card: " + parkingName);

        page.openParkingByName(parkingName);
        ParkingDetailPage detail = new ParkingDetailPage(driver, wait);
        detail.waitLoaded();
    }
}
