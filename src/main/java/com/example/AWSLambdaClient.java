package com.example;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AWSLambdaClient {
    private static final String API_URL = "https://ea4mluybog.execute-api.us-east-2.amazonaws.com/Prod/item";
    private static final String API_KEY = "zRvjKa6FzY9dSUSMxQBpbEIW2MMkGVl2G56V7000";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    public String callLambda(Map<String, Object> payload) throws IOException {
        long startTime = System.nanoTime();
        
        // Convert payload to JSON string
        String jsonPayload = gson.toJson(payload);
        
        // Create request body
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        
        // Build the request
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", API_KEY)
                .addHeader("User-Agent", "Your-User-Agent")  // Update user-agent as needed
                .post(body)
                .build();

        // Execute the request
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Error: {} - {}", response.code(), response.body().string());
                return null;
            }

            String responseJson = response.body().string();
            long endTime = System.nanoTime();
            System.out.println("Lambda call duration: " + (endTime - startTime) / 1_000_000 + " ms");

            // Parse response
            Map<String, Object> responseMap = gson.fromJson(responseJson, new TypeToken<Map<String, Object>>() {}.getType());
            return gson.toJson(responseMap);
        }
    }

    public CompletableFuture<String> callLambdaAsync(Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callLambda(payload);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}
