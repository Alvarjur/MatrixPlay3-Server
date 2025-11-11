package com.server;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.ClientData;


/**
 * Servidor WebSocket amb routing simple de missatges, sense REPL.
 *
 * El servidor arrenca, registra un shutdown hook i es queda a l'espera
 * fins que el procés rep un senyal de terminació (SIGINT, SIGTERM).
 *
 * Missatges suportats:
 *  - bounce: eco del missatge a l’emissor
 *  - broadcast: envia a tots excepte l’emissor
 *  - private: envia a un destinatari pel seu nom
 *  - clients: llista de clients connectats
 *  - error / confirmation: missatges de control
 */
public class Main extends WebSocketServer {

    /** Port per defecte on escolta el servidor. */
    public static final int DEFAULT_PORT = 3000;

    public static ClientRegistry clients;
    public static Map<String, ClientData> clientsData = new HashMap<>();

    public ControllerCountdown controllerCountdown = new ControllerCountdown(this);

    public static double res = 576;
    public static double playerWidth = 4 * 9;
    public static double playerHeight = 20 * 9;

    private static final String K_TYPE = "type";
    private static final String K_MESSAGE = "message";
    private static final String K_ORIGIN = "origin";
    private static final String K_DESTINATION = "destination";
    private static final String K_ID = "id";
    private static final String K_LIST = "list";
    private static final String K_CLIENT_NAME = "clientName";
    private static final String K_CLIENT_TYPE = "clientType";

    // Tipus de missatges
    private static final String T_SALUTATION = "salutation";
    private static final String T_REGISTER = "register";
    private static final String T_CLIENTS_LIST = "clientsList";
    private static final String T_CONFIGURATION = "configuration";
    private static final String T_COUNTDOWN = "countdown";
    private static final String T_INITIAL_POSITION = "initialPosition";
    private static final String T_SERVER_DATA = "serverData";
    private static final String T_GOAL_SCORED = "goalScored";
    private static final String T_RANKING = "ranking";
    private static final String T_CHANGE_BANNER = "changeBanner";


    /**
     * Crea un servidor WebSocket que escolta a l'adreça indicada.
     *
     * @param address adreça i port d'escolta del servidor
     */
    public Main(InetSocketAddress address) {
        super(address);

    }

    /**
     * Crea un objecte JSON amb el camp type inicialitzat.
     *
     * @param type valor per a type
     * @return instància de JSONObject amb el tipus establert
     */
    private static JSONObject msg(String type) {
        return new JSONObject().put(K_TYPE, type);
    }

    /**
     * Afegeix clau-valor al JSONObject si el valor no és null.
     *
     * @param o objecte JSON destí
     * @param k clau
     * @param v valor (ignorat si és null)
     */
    private static void put(JSONObject o, String k, Object v) {
        if (v != null) o.put(k, v);
    }

    /**
     * Envia de forma segura un payload i, si el socket no està connectat,
     * el neteja del registre.
     *
     * @param to socket destinatari
     * @param payload cadena JSON a enviar
     */
    private static void sendSafe(WebSocket to, String payload) {
        if (to == null) return;
        try {
            to.send(payload);
        } catch (WebsocketNotConnectedException e) {
            //String name = clients.cleanupDisconnected(to);
            //log("Client desconectado durante sendSafe() -> " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    

    // ----------------- WebSocketServer overrides -----------------

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log("New client connected");
        salutation();
    }

    /**
     * Broadcast a message to all clients except the sender.
     * 
     * @param sender
     * @param payload
     */
    private void broadcastExcept(WebSocket sender, String payload) {
        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            WebSocket conn = e.getKey();
            if (!clientsData.containsKey(e.getValue()))
                continue;
            if (!Objects.equals(conn, sender)) {
                sendSafe(conn, payload);
                log("Send Clients to " + clients.nameBySocket(conn));
            }
        }
    }    

    //private void sendClientsListToAll() {
    //    JSONArray list = clients.currentAvaliblePlayersNames();
    //    for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
    //        JSONObject rst = msg(T_CLIENTS_LIST);
    //        put(rst, K_ID, e.getValue());
    //        put(rst, K_LIST, list);
    //        sendSafe(e.getKey(), rst.toString());
    //        log("ClientsList send to " + e.getKey());
    //    }
    //}

    private String sendAllClients() {
        JSONObject response = msg(T_CLIENTS_LIST);
        JSONArray clientsDataArray = new JSONArray();

        for (ClientData cd : clientsData.values()) {
            JSONObject clientData = new JSONObject();
            clientData.put("clientName", cd.name);
            clientData.put("clientType", cd.clientType);
            clientsDataArray.put(clientData);
        }

        response.put(T_CLIENTS_LIST, clientsDataArray);

        return response.toString();
    }

    /** Elimina el client del registre i notifica la llista actualitzada. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        clientsData.remove(clients.nameBySocket(conn));
        log("Client disconnected");
    }

    /***** Procesa el mensaje recibido y actúa según el tipo de mensaje. *****/
    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            // Getting JSON
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                
                case T_REGISTER:
                    String name = json.getString(K_CLIENT_NAME);
                    String clientType = json.getString(K_CLIENT_TYPE);

                    clients.add(conn, name);

                    if (!clientType.equals("Raspberry")) {
                        ClientData clientData = new ClientData(name, clientType);
                        clientsData.put(name, clientData);
                        log("Client registered: " + name);
                    }

                    broadcastExcept(null, sendAllClients()); 

                    // if (clientsData.values().size() == 2) {
                    //     log("Two players connected, starting countdown");
                    //     ControllerCountdown.start(3);
                    // }
                    break;

                case T_CLIENTS_LIST:
                    // Clients list
                
                    break;

                case T_CONFIGURATION:
                    // Configuration
                    // Send configuration json with url of connection
                    System.out.println("Sending configuration to client");

                    InputStream inputStream = Main.class
                    .getClassLoader()
                    .getResourceAsStream("assets/configuration.json");

                    if (inputStream == null) {
                        throw new RuntimeException("No se encontró el archivo configuration.json");
                    }

                    // Lee el contenido del archivo
                    String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                    // Crea el objeto JSON (opcional)
                    JSONObject configJson = new JSONObject(jsonContent);
                    configJson.put("type", T_CONFIGURATION);

                    // Envía el JSON (ejemplo)
                    sendSafe(conn, configJson.toString());

                    break;

                case T_COUNTDOWN:
                    // Countdown
                    System.out.println("Starting countdown from 3 seconds");
                    ControllerCountdown.start(3);

                    break;

                case T_INITIAL_POSITION:
                    // Initial position

                    break;

                case T_SERVER_DATA:
                    // Server data

                    break;

                case T_GOAL_SCORED:
                    // Goal scored

                    break;

                case T_RANKING:
                    // Ranking

                    break;
                    
                case T_CHANGE_BANNER:
                    // Change banner

                    break;

                default:
                    log("Message type not recognized: " + type);
                    conn.send(new JSONObject()
                        .put("type", "error")
                        .put("message", "Message type not recognized: " + type)
                        .toString()
                    );
                    break;
            }

            

        } catch (Exception e) {
            conn.send(new JSONObject()
                .put("type", "error")
                .put("message", "Invalid JSON")
                .toString()
            );
            e.printStackTrace();
        }
    }

    /** Log d'error global o de socket concret. */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        conn.send("error");
    }

    

    /** Arrencada: log i configuració del timeout de connexió perduda. */
    @Override
    public void onStart() {
        log("WebSocket server opened on port: " + getPort() + ". Ctrl+C to stop it.");
    }

    /**
     * Punt d'entrada: arrenca el servidor al port per defecte i espera senyals.
     *
     * @param args arguments de línia d'ordres (no utilitzats)
     */
    public static void main(String[] args) {
        Main server = new Main(new InetSocketAddress(DEFAULT_PORT));
        clients = new ClientRegistry();
        server.start();
    }

    public void sendCountdown(int seconds) {
        JSONObject json = new JSONObject();
        json.put(K_TYPE, T_COUNTDOWN);
        json.put("seconds", seconds);
        broadcast(json.toString());
    }

    public void sendInitialPos() {
        JSONObject json = new JSONObject();
        json.put(K_TYPE, T_INITIAL_POSITION);
        json.put("p1", "27 " + String.valueOf(res/2 - playerHeight));
        json.put("p2", String.valueOf(res - 27 - playerWidth) + " " + String.valueOf(res/2 - playerHeight));
        broadcast(json.toString());

    }

    public static void log(String message) {
        System.out.println(message);
    }

    public void salutation() {
        JSONObject payload = new JSONObject()
                        .put("type", "salutation")
                        .put("message", "Hola");
        
        broadcast(payload.toString());
    }


}
