package com.lionheart.arsenal.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Created by wangyiguang on 17/7/29.
 */
public class BaseHttpHandler implements HttpHandler {
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        if (requestMethod.equalsIgnoreCase("GET")) {
            processGetRequest(exchange);
        }
        if (requestMethod.equalsIgnoreCase("POST")) {
            processPostRequest(exchange);
        }
    }

    protected void processGetRequest(HttpExchange exchange) throws IOException {}

    protected void processPostRequest(HttpExchange exchange) throws IOException {}
}
