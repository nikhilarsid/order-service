package com.example.demo.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        logResponse(response);
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        System.out.println("=========================== [REQUEST START] ===========================");
        System.out.println("Method      : " + request.getMethod());
        System.out.println("URI         : " + request.getURI());
        System.out.println("Headers     : " + request.getHeaders());
        System.out.println("Request Body: " + new String(body, StandardCharsets.UTF_8));
        System.out.println("=======================================================================");
    }

    private void logResponse(ClientHttpResponse response) throws IOException {
        System.out.println("=========================== [RESPONSE START] ===========================");
        System.out.println("Status code  : " + response.getStatusCode());
        System.out.println("Status text  : " + response.getStatusText());
        System.out.println("Headers      : " + response.getHeaders());

        // Caution: Reading the body stream might consume it.
        // For debugging 403s, the headers and status are usually enough.
        System.out.println("========================================================================");
    }
}