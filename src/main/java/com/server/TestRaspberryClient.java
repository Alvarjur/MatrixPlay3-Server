package com.server;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;

public class TestRaspberryClient {

    public static void main(String[] args) throws Exception {
        URI serverUri = new URI("ws://localhost:3000"); // Cambia al puerto de tu servidor

        WebSocketClient client = new WebSocketClient(serverUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Conectado al servidor!");

                // Registrarse como Raspberry
                JSONObject register = new JSONObject();
                register.put("type", "register");
                register.put("clientName", "RaspberrySim");
                register.put("clientType", "Raspberry");
                send(register.toString());
            }

            @Override
            public void onMessage(String message) {
                System.out.println("Mensaje recibido: " + message);

                try {
                    JSONObject json = new JSONObject(message);
                    String type = json.getString("type");

                    if (type.equals("qrMatrix")) {
                        System.out.println("QR recibido, dibujando en consola...");
                        drawQR(json.getJSONArray("matrix"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Conexión cerrada: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };

        client.connectBlocking();
    }

    // Dibuja la matriz del QR en la consola usando ASCII
    private static void drawQR(JSONArray matrix) {
        for (int y = 0; y < matrix.length(); y++) {
            JSONArray row = matrix.getJSONArray(y);
            for (int x = 0; x < row.length(); x++) {
                System.out.print(row.getInt(x) == 1 ? "██" : "  ");
            }
            System.out.println();
        }
        System.out.println("\nEscanea el QR con tu móvil!");
    }
}
