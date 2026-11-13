package com.smartkneeoa.frontend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkneeoa.frontend.MainApp;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client service for all communication with the Spring Boot backend.
 * All calls are synchronous (executed on background threads by controllers).
 */
public class ApiService {

    private static final ApiService INSTANCE = new ApiService();
    public  static ApiService getInstance() { return INSTANCE; }

    private final OkHttpClient http;
    private final ObjectMapper mapper;

    private ApiService() {
        http   = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        mapper = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────────────
    //  Auth
    // ─────────────────────────────────────────────────────────────
    public JsonNode register(String name, String email, String password,
                              Integer age, String gender,
                              Double heightCm, Double weightKg) throws IOException {
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("name",      name);
            put("email",     email);
            put("password",  password);
            put("age",       age);
            put("gender",    gender);
            put("heightCm",  heightCm);
            put("weightKg",  weightKg);
        }});
        return post("/api/auth/register", json, false);
    }

    public JsonNode login(String email, String password) throws IOException {
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("email",    email);
            put("password", password);
        }});
        return post("/api/auth/login", json, false);
    }

    // ─────────────────────────────────────────────────────────────
    //  User profile
    // ─────────────────────────────────────────────────────────────
    public JsonNode getProfile() throws IOException {
        return get("/api/users/profile");
    }

    public JsonNode updateProfile(java.util.Map<String, Object> fields) throws IOException {
        return put("/api/users/profile", mapper.writeValueAsString(fields));
    }

    // ─────────────────────────────────────────────────────────────
    //  Assessments
    // ─────────────────────────────────────────────────────────────
    public JsonNode createAssessment(String kneeSide, int painScore,
                                      String symptoms) throws IOException {
        String json = mapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
            put("kneeSide",  kneeSide);
            put("painScore", painScore);
            put("symptoms",  symptoms);
        }});
        return post("/api/assessments", json, true);
    }

    public JsonNode listAssessments(int page, int size) throws IOException {
        return get("/api/assessments?page=" + page + "&size=" + size);
    }

    public JsonNode getAssessment(long id) throws IOException {
        return get("/api/assessments/" + id);
    }

    public JsonNode submitSensorData(long assessmentId,
                                      java.util.Map<String, Object> reading) throws IOException {
        return post("/api/assessments/" + assessmentId + "/sensor-data",
                    mapper.writeValueAsString(reading), true);
    }

    public JsonNode triggerPrediction(long assessmentId) throws IOException {
        return post("/api/assessments/" + assessmentId + "/predict", "{}", true);
    }

    public JsonNode getResult(long assessmentId) throws IOException {
        return get("/api/assessments/" + assessmentId + "/result");
    }

    // ─────────────────────────────────────────────────────────────
    //  HTTP helpers
    // ─────────────────────────────────────────────────────────────
    private static final MediaType JSON_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private JsonNode get(String path) throws IOException {
        Request req = new Request.Builder()
                .url(MainApp.API_BASE_URL + path)
                .addHeader("Authorization", "Bearer " + MainApp.getAuthToken())
                .get().build();
        return execute(req);
    }

    private JsonNode post(String path, String body, boolean auth) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(MainApp.API_BASE_URL + path)
                .post(RequestBody.create(body, JSON_TYPE));
        if (auth) builder.addHeader("Authorization", "Bearer " + MainApp.getAuthToken());
        return execute(builder.build());
    }

    private JsonNode put(String path, String body) throws IOException {
        Request req = new Request.Builder()
                .url(MainApp.API_BASE_URL + path)
                .addHeader("Authorization", "Bearer " + MainApp.getAuthToken())
                .put(RequestBody.create(body, JSON_TYPE))
                .build();
        return execute(req);
    }

    private JsonNode execute(Request req) throws IOException {
        try (Response resp = http.newCall(req).execute()) {
            String responseBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + ": " + responseBody);
            }
            return mapper.readTree(responseBody);
        }
    }
}
