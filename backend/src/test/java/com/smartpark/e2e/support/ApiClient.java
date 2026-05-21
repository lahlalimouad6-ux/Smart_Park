package com.smartpark.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ApiClient {
    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final URI backendBaseUri;

    public ApiClient(ObjectMapper objectMapper, URI backendBaseUri) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.backendBaseUri = backendBaseUri;
    }

    public void signup(String nom, String prenom, String email, String password, String role) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nom", nom);
        payload.put("prenom", prenom);
        payload.put("email", email);
        payload.put("password", password);
        payload.put("role", role);
        HttpResponse<String> res = sendJsonPost("/api/auth/signup", payload, null);
        if (res.statusCode() == 200) return;
        if (res.statusCode() == 400 && res.body() != null && res.body().contains("already")) return;
        throw new IllegalStateException("Signup failed: HTTP " + res.statusCode() + " - " + res.body());
    }

    public AuthSession signin(String email, String password) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        HttpResponse<String> res = sendJsonPost("/api/auth/signin", payload, null);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Signin failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        try {
            JsonNode root = objectMapper.readTree(res.body());
            String token = root.path("token").asText(null);
            if (token == null || token.isBlank()) throw new IllegalStateException("Missing token in signin response");
            return new AuthSession(token, root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse signin response: " + res.body(), e);
        }
    }

    public long createParking(String bearerToken, String nom, String ville, String adresse, String coordGps, String tarifHeure, int nombrePlaces) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nom", nom);
        payload.put("ville", ville);
        payload.put("adresse", adresse);
        payload.put("coordGps", coordGps);
        payload.put("tarifHeure", tarifHeure);
        payload.put("nombrePlaces", nombrePlaces);
        HttpResponse<String> res = sendJsonPost("/api/parkings", payload, bearerToken);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Create parking failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        try {
            JsonNode root = objectMapper.readTree(res.body());
            long id = root.path("id").asLong(0);
            if (id <= 0) throw new IllegalStateException("Missing id in createParking response: " + res.body());
            return id;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse createParking response: " + res.body(), e);
        }
    }

    public long getSpotIdByNumeroPlace(long parkingId, String numeroPlace) {
        HttpResponse<String> res = sendGet("/api/parkings/" + parkingId + "/zones", null);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Get zones failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        try {
            JsonNode root = objectMapper.readTree(res.body());
            if (!root.isArray()) {
                throw new IllegalStateException("Unexpected zones response: " + res.body());
            }
            for (JsonNode zone : root) {
                JsonNode spots = zone.path("spots");
                if (!spots.isArray()) continue;
                for (JsonNode spot : spots) {
                    if (numeroPlace.equals(spot.path("numeroPlace").asText(null))) {
                        long id = spot.path("id").asLong(0);
                        if (id > 0) return id;
                    }
                }
            }
            throw new IllegalStateException("Spot not found: " + numeroPlace);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse zones response: " + res.body(), e);
        }
    }

    public long createReservation(String bearerToken, long spotId, LocalDateTime dateDebut, LocalDateTime dateFin) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spotId", spotId);
        payload.put("dateDebut", dateDebut.toString());
        payload.put("dateFin", dateFin.toString());
        payload.put("useSubscription", false);
        HttpResponse<String> res = sendJsonPost("/api/reservations", payload, bearerToken);
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Create reservation failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        try {
            JsonNode root = objectMapper.readTree(res.body());
            long id = root.path("id").asLong(0);
            if (id <= 0) throw new IllegalStateException("Missing reservation id: " + res.body());
            return id;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse createReservation response: " + res.body(), e);
        }
    }

    public static String uniqueEmail() {
        return "e2e+" + UUID.randomUUID().toString().replace("-", "") + "@smartpark.local";
    }

    public record AuthSession(String token, JsonNode rawJson) {
        public String asFrontendLocalStorageUserJson() {
            return rawJson.toString();
        }
    }

    private HttpResponse<String> sendJsonPost(String path, Map<String, Object> payload, String bearerToken) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize request payload", e);
        }

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(backendBaseUri.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (bearerToken != null && !bearerToken.isBlank()) {
            req.header("Authorization", "Bearer " + bearerToken);
        }

        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request failed: POST " + path, e);
        }
    }

    private HttpResponse<String> sendGet(String path, String bearerToken) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(backendBaseUri.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (bearerToken != null && !bearerToken.isBlank()) {
            req.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request failed: GET " + path, e);
        }
    }
}
