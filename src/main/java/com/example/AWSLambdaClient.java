/*
 * Copyright (c) 2024, Mati Zuckerman <Mati.Zuckerman@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
