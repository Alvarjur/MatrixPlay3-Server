package com.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class LogServer {//revisar si funciona con el Puerto 3000, y si cambia la IP si es Proxmox 

    public static void main(String[] args) throws Exception {
       //aqui recuerda mirar esto si funciona 
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);
        System.out.println("Servidor iniciado");
        server.createContext("/log", (HttpExchange exchange) -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Parsear JSON recibido
                JSONObject json = new JSONObject(body);
                String player = json.getString("player");
                String action = json.getString("action");

                // Guardar en la base de datos
                LoggerService.saveLog(player, action);

                // Respuesta al cliente
                String response = "Log saving";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Método no permitido
            }
        });

        server.start();
    }
}
