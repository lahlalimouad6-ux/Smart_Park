package com.smartpark.e2e.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.e2e.BaseE2ETest;
import com.smartpark.e2e.E2EConfig;
import com.smartpark.e2e.pages.ParkingDetailPage;
import com.smartpark.e2e.pages.ReservationsPage;
import com.smartpark.e2e.support.ApiClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReservationFlowE2EIT extends BaseE2ETest {
    private static volatile long parkingId;
    private static volatile String conducteurUserJson;

    @BeforeAll
    static void seedData() {
        ObjectMapper om = new ObjectMapper();
        ApiClient api = new ApiClient(om, E2EConfig.BACKEND_BASE_URI);

        String adminEmail = ApiClient.uniqueEmail();
        String adminPassword = "Password123!";
        api.signup("Admin", "E2E", adminEmail, adminPassword, "ADMIN");
        ApiClient.AuthSession adminSession = api.signin(adminEmail, adminPassword);

        String parkingName = "E2E Parking " + System.currentTimeMillis();
        parkingId = api.createParking(
                adminSession.token(),
                parkingName,
                "E2E City",
                "1 Rue E2E",
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
    void createThenCancelReservation() {
        clearLocalStorage();
        setLocalStorageUserJson(conducteurUserJson);

        go("/parkings/" + parkingId);
        ParkingDetailPage detail = new ParkingDetailPage(driver, wait);
        detail.waitLoaded();

        detail.selectSpotByTitle("Place A01");

        LocalDateTime start = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        detail.setDates(start, end);

        detail.confirmReservation();
        wait.until(d -> d.getCurrentUrl().contains("/reservations"));

        ReservationsPage reservations = new ReservationsPage(driver, wait);
        reservations.waitLoaded();
        assertTrue(reservations.hasReservationForSpot("A01"), "Expected reservation for spot A01 to be visible");
        assertTrue(reservations.canCancelVisible(), "Expected cancel button to be visible for a future reservation");

        reservations.cancelSelectedReservation();
    }
}
