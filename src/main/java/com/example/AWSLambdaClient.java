package com.example;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AWSLambdaClient {
    private static final String API_URL = "https://ea4mluybog.execute-api.us-east-2.amazonaws.com/Prod/item";
    private static final String API_KEY = "zRvjKa6FzY9dSUSMxQBpbEIW2MMkGVl2G56V7000";

    @Inject
    private Gson gson;

    public String callLambda(Map<String, Object> payload) throws Exception {
        long startTime = System.nanoTime();
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("x-api-key", API_KEY); // Add the API key header
        conn.setDoOutput(true);

        String jsonInputString = gson.toJson(payload);
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            // Capture detailed error information
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    errorResponse.append(responseLine.trim());
                }
            }
            System.err.println("Error: " + status + " - " + errorResponse.toString());
            return null;
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }

        long endTime = System.nanoTime();
        System.out.println("Lambda call duration: " + (endTime - startTime) / 1_000_000 + " ms");

        String responseJson = response.toString();
        Map<String, Object> responseMap = gson.fromJson(responseJson, new TypeToken<Map<String, Object>>() {}.getType());
        return gson.toJson(responseMap);
    }

    public CompletableFuture<String> callLambdaAsync(Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callLambda(payload);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}
