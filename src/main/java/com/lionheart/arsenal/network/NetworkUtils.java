package com.lionheart.arsenal.network;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;


/**
 * Created by wangyiguang on 17/7/28.
 */
public class NetworkUtils {

    public static Map<String, String> parseParameters(HttpExchange exchange) throws UnsupportedEncodingException {
        @SuppressWarnings("unchecked")
        Map<String, String> parameters = new HashMap<>();
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(),"utf-8");
        BufferedReader br = new BufferedReader(isr);
        try {
            String query = br.readLine();
            parseQuery(query, parameters);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return parameters;
    }

    public static void sendOKReseponse(HttpExchange exchange) {
        try {
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, 0);
            OutputStream responseBody = exchange.getResponseBody();
            Headers requestHeaders = exchange.getRequestHeaders();
            Set<String> keySet = requestHeaders.keySet();
            Iterator<String> iter = keySet.iterator();
            while (iter.hasNext()) {
                String key = iter.next();
                List values = requestHeaders.get(key);
                String s = key + " = " + values.toString() + "\n";
                responseBody.write(s.getBytes());
            }
            responseBody.close();
        } catch (IOException e) {
            System.out.println("Error sending response to client.");
        }
    }

    private static void parseQuery(String query, Map<String, String> parameters) throws UnsupportedEncodingException {
        if (query != null) {
            String pairs[] = query.split("[&]");
            for (String pair : pairs) {
                String param[] = pair.split("[=]");
                String key = null;
                String value = null;
                if (param.length > 0) {
                    key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
                }

                if (param.length > 1) {
                    value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));
                }
                parameters.put(key, value);
            }
        }
    }
}
