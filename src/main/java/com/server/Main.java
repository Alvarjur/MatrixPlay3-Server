package com.server;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import com.shared.Directions;


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

    public static final int MAX_GOALS = 5;

    

    public ControllerCountdown controllerCountdown = new ControllerCountdown(this);

    public static double res = 576;
    public static double playerWidth = 4 * 9;
    public static double playerHeight = 16 * 9;
    public static double ballRadius = 3 * 9;
    public static double SPEED = 0.5f;
    public final static double ANDROIDSPEED = 0.1f;
    public final static double BALLSPEED = 0.5f;
    public static double ballSpeed = 1.05f;

    public Ball ball = new Ball(res/2, res/2, 0, 0);

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

    /* Variables per a controlar el moviment de les pales i l'actualització de la UI */
    private static String[] playersArray = new String[2];
    private static boolean isPlaying = false;
    private static boolean player1Desktop = false;
    private static boolean player2Desktop = false;

    private static Directions player1Direction = Directions.STATIC;
    private static Directions player2Direction = Directions.STATIC;

    private final static float PAD_MOVEMENT_PADDING = 0.16f;
    private final static double PAD_MIN_MOVMENT = res * PAD_MOVEMENT_PADDING;
    private final static double PAD_MAX_MOVMENT = res * (1 - PAD_MOVEMENT_PADDING);

    private static boolean isBallReset = true;
    private final static double BALL_RESET_TIME = 2000.0;
    private static double ballResetTime = 0;

    Runnable ctrlUIElements = new Runnable() {
        @Override
        public void run() {
            double deltaTime = 1;
            long currentTime;
            long pastTime = System.nanoTime() / 1000;
            resetBall();

            while (isPlaying) {
                currentTime = System.nanoTime() / 1000;
                deltaTime = (currentTime - pastTime) / 1000;
                pastTime = currentTime;

                ballMovement(deltaTime);
                sendBallPos();

                if (player1Desktop) {
                    updatePad1(deltaTime);
                }

                if (player2Desktop) {
                    updatePad2(deltaTime);
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException ex) {
                }
            }
        }
        
    };

    public void resetBall() {
        ball.posX = res / 2;
        ball.posY = Math.random() < 0.5 ? ballRadius : res - ballRadius;
        ball.velX = 0.5 * (Math.random() < 0.5 ? BALLSPEED : -1 * BALLSPEED);
        ball.velY = 0.5 * (Math.random() < 0.5 ? BALLSPEED : -1 * BALLSPEED);
    }
    
    public static double[] ballIntersectsPaddle(
        double x1, double y1,
        double x2, double y2,
        double x3, double y3,
        double x4, double y4
    ) {
        double denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);

        if (denom == 0) return null;

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom;
        double u = ((x1 - x3) * (y1 - y2) - (y1 - y3) * (x1 - x2)) / denom;

        if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
            double ix = x1 + t * (x2 - x1);
            double iy = y1 + t * (y2 - y1);
            return new double[]{ix, iy};
        }
        return null;
    }


    public void ballMovement(double dt) {

    double nextX = ball.posX + ball.velX * dt;
    double nextY = ball.posY + ball.velY * dt;

    boolean isReset = false;

    if (isBallReset) {
        ballResetTime += dt;

        if (ballResetTime >= BALL_RESET_TIME) {
            ballResetTime = 0;
            isBallReset = false;
        }
        else {
            return;
        }
    }

    // Rebotes con paredes
    if (nextX <= ballRadius || nextX >= res - ballRadius) {
        isReset = true;
        isBallReset = true;
        resetBall();

        if (nextX <= ballRadius) {
            clientsData.get(playersArray[1]).goalScored += 1;
            sendGoalScored(playersArray[1]);
        }
        else {
            clientsData.get(playersArray[0]).goalScored += 1;
            sendGoalScored(playersArray[0]);
        }
        checkGameEnd();
    }
    if (nextY <= ballRadius || nextY >= res - ballRadius) {
        ball.velY = -ball.velY;
        nextY = ball.posY + ball.velY;
        // resetBall();
    }

    // Paddles
    ArrayList<ClientData> players = new ArrayList<>();
    for (ClientData cd : clientsData.values()) {
        if (!cd.clientType.equals("Raspberry")) {
            players.add(cd);
        }
        
    }

    for (int i = 0; i < players.size(); i++) {

        ClientData player = players.get(i);

        double paddleX = (i == 0) ? 27 + playerWidth : (res - 27 - playerWidth);
        double paddleTopY = player.posY - playerHeight/2;
        double paddleBottomY = paddleTopY + playerHeight;

        double[] hit = ballIntersectsPaddle(
            ball.posX, ball.posY,
            nextX,     nextY,
            paddleX, paddleTopY,
            paddleX, paddleBottomY
        );

        if (hit != null) {
            ball.velX *= ballSpeed;
            ball.velY *= ballSpeed;
            // Rebote horizontal
            ball.velX = -ball.velX;

            // Ajustar el siguiente frame
            nextX = ball.posX + ball.velX;
        }
    }

    // Aplicar movimiento final
    if (!isReset) {
        ball.posX = nextX;
        ball.posY = nextY;
    }
}

    public void updatePad1(double dt) {

        int dir = 0;

        if (player1Direction == Directions.UP) {
            dir = 1;
        }
        else if (player1Direction == Directions.DOWN) {
            dir = -1;
        }

        if (player1Direction == Directions.UP) {
            double posY = clientsData.get(playersArray[0]).posY - SPEED * dt;
            clientsData.get(playersArray[0]).posY = Math.clamp(posY, PAD_MIN_MOVMENT, PAD_MAX_MOVMENT);
        }
        else if (player1Direction == Directions.DOWN) {
            double posY = clientsData.get(playersArray[0]).posY + SPEED * dt;
            clientsData.get(playersArray[0]).posY = Math.clamp(posY, PAD_MIN_MOVMENT, PAD_MAX_MOVMENT);
        }

        if (dir != 0) {
            sendPlayersPos(playersArray[0]);
        }  
    }

    public void updatePad2(double dt) {

        int dir = 0;

        if (player2Direction == Directions.UP) {
            dir = 1;
        }
        else if (player2Direction == Directions.DOWN) {
            dir = -1;
        }

        if (player2Direction == Directions.UP) {
            double posY = clientsData.get(playersArray[1]).posY - SPEED * dt;
            clientsData.get(playersArray[1]).posY = Math.clamp(posY, PAD_MIN_MOVMENT, PAD_MAX_MOVMENT);
        }
        else if (player2Direction == Directions.DOWN) {
            double posY = clientsData.get(playersArray[1]).posY + SPEED * dt;
            clientsData.get(playersArray[1]).posY = Math.clamp(posY, PAD_MIN_MOVMENT, PAD_MAX_MOVMENT);
        }

        if (dir != 0) {
            sendPlayersPos(playersArray[1]);
        }  
    }


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

    public void startCountdown() {

        new Thread(() -> {
            try {

                ArrayList<ClientData> players = new ArrayList<>();
                for (ClientData cd : clientsData.values()) {
                    if (!cd.clientType.equals("Raspberry")) {
                        players.add(cd);
                    }
                }

                // Comprobación de seguridad
                if (players.size() < 2) {
                    log("ERROR: No hay 2 jugadores válidos para iniciar el countdown.");
                    return;
                }

                ClientData p1 = players.get(0);
                ClientData p2 = players.get(1);

                LoggerService.saveLog(p1.name, p1.clientType, "Go to countdown.");
                LoggerService.saveLog(p2.name, p2.clientType, "Go to countdown.");
                log("[DB] Saving event countdown: "+ "Player 1:"+ p1.name+"|" + "Player 2:"+ p2.name);
                
                for (int i = 3; i >= 0; i--) {
                    // Este es el que se usa
                    JSONObject json = msg(T_COUNTDOWN);
                    json.put("value", i);

                    String player1Name = "";
                    for (ClientData cd : clientsData.values()) {
                        log("Player in countdown: " + cd.name);
                        if(!cd.clientType.equals("Raspberry")) {
                            log("Skipping Raspberry client in countdown");
                            
                            if (player1Name.isEmpty()) {
                                player1Name = cd.name;
                            } else {
                                json.put("player1Name", player1Name);
                                json.put("player2Name", cd.name);
                                break;
                            }

                        }
                        
                    }
                    
                    sendBroadCast(json.toString());
                    log("Sending Countdown to players; " + json.toString());
                    

                    if (i == 0) {
                        sendInitialPos(); 
                        resetBall();

                    }

                    if (i > 0)
                        Thread.sleep(1500);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                
            }
        }, "CountdownThread").start();

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

    public static void sendBroadCast(String payload) {

        for (Map.Entry<WebSocket, String> e : clients.snapshot().entrySet()) {
            sendSafe(e.getKey(), payload);
        }

    }

    /** Elimina el client del registre i notifica la llista actualitzada. */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clientsData.remove(clients.nameBySocket(conn));
        clients.remove(conn);
        log("Client disconnected");
        long playerCount = clientsData.values().stream()
                .filter(cd -> !cd.clientType.equals("Raspberry"))
                .count();
        
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

                    if (clientsData.size() == 2) {
                        log("Two players connected, starting countdown");
                        //ControllerCountdown.start(3);
                        startCountdown();
                    }
                    LoggerService.saveLog(name, clientType, "has connected to the server."); 
                
                    log("[DB] Saving event: name=" + name + " clientType=" + clientType);

                    break;

                case T_CLIENTS_LIST:
                    // Clients list
                
                    break;

                case T_CONFIGURATION:
                    // Configuration
                    // Send configuration json with url of connection
                    System.out.println("Sending configuration to client");
                    clients.add(conn, "Raspberri");

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

                case "movement":
                    String player = json.getString("clientName");
                    String direction = json.getString("message");
                    System.out.println("Player " + player + " moved " + direction);
                    // Aquí puedes actualizar la posición del jugador en la interfaz de usuario

                    Directions dir = Directions.STATIC;

                    // Set direction
                    if (direction.equals("UP")) { 
                        dir = Directions.UP;
                         
                    }
                    else if (direction.equals("DOWN")) { 
                        dir = Directions.DOWN;
                    }


                    // Set direction to player
                    if (player.equals(playersArray[0])) {
                        player1Desktop = true;
                        player1Direction = dir;
                    }
                    else {
                        player2Desktop = true;
                        player2Direction = dir;
                    }

                    break;

                case "movement_android":
                    String pl = json.getString("clientName");
                    float amount = Float.parseFloat(json.getString("message"));
                    System.out.println("Player " + pl + " moved " + amount);
                    // Aquí puedes actualizar la posición del jugador en la interfaz de usuario
                    amount *= res;
                    
                    double posY = Math.round(getDenormalizedPosition(1, getNormalizedPosition(1, (clientsData.get(pl).posY - ANDROIDSPEED*amount))[1])[1] * 100)/100;
                    clientsData.get(pl).posY = Math.clamp(posY, PAD_MIN_MOVMENT, PAD_MAX_MOVMENT);

                    sendPlayersPos(pl);
                    break;
                

                case T_INITIAL_POSITION:
                    // Initial position
                    break;

                case T_SERVER_DATA:
                    // Server data
                    break;

                case T_GOAL_SCORED:
                   


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

        if (conn != null) {
            try {
                conn.send("error: " + ex.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            log("Error occurred, but connection is null. Exception: " + ex.getMessage());
        }
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

        LogDataBase.createTable(); //creo la tabla
        
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

    public double[] getNormalizedPosition(double x, double y) {
        double normX = x / res;
        double normY = y / res;
        return new double[] {normX, normY};
    }

    public double[] getDenormalizedPosition(double normX, double normY) {
        double x = normX * res;
        double y = normY * res;
        return new double[] {x, y};
    }

    public void sendBallPos() {
        JSONObject json = new JSONObject();
        json.put(K_TYPE, "ballPosition");
        json.put("position", getNormalizedPosition(ball.posX, ball.posY)[0] + " " + getNormalizedPosition(ball.posX, ball.posY)[1]);

        sendBroadCast(json.toString());
    }
    public void sendInitialPos() {

        // coloco en variables las posiciones para poder guardar en base de datos 
        String p1pos = "27 " + (res / 2 - playerHeight);
        String p2pos = (res - 27 - playerWidth) + " " + (res / 2 - playerHeight);

        //obtengo el tipo de cliente con el nombre del player ya asignado arriba en registered
        ArrayList<ClientData> players = new ArrayList<>();
        for (ClientData cd : clientsData.values()) {
            if (!cd.clientType.equals("Raspberry")) {
                players.add(cd);
            }
        }
        String player1 = players.get(0).name;
        String player2 = players.get(1).name;
        String p1Type = players.get(0).clientType;
        String p2Type = players.get(1).clientType;

        playersArray[0] = player1;
        playersArray[1] = player2;

        LoggerService.saveLog(player1, p1Type, "Initial position; " + p1pos);
        LoggerService.saveLog(player2, p2Type, "Initial position; " + p2pos);
        log("[DB] Saving initial position: Player= " + player1 + ", Pos= " + p1pos);
        log("[DB] Saving initial position: Player= " + player2 + ", Pos= " + p2pos);

        JSONObject json = new JSONObject();
        json.put(K_TYPE, T_INITIAL_POSITION);
        // json.put("p1", "27 " + String.valueOf(res/2 - playerHeight));
        json.put("playersSize", playerWidth / res + " " + playerHeight / res);
        json.put("p1", getNormalizedPosition(27, res/2)[0] + " " + getNormalizedPosition(27, res/2)[1]);
        // json.put("p2", String.valueOf(res - 27 - playerWidth) + " " + String.valueOf(res/2 - playerHeight));
        json.put("p2", getNormalizedPosition(res - 27, res/2)[0] + " " + getNormalizedPosition(res - 27, res/2)[1]);

        json.put("ball", getNormalizedPosition(res/2, res/2)[0] + " " + getNormalizedPosition(res/2, res/2)[1]);
        json.put("ballRadius", (double)ballRadius / res);
        sendBroadCast(json.toString());

        isPlaying = true;
        new Thread(ctrlUIElements).start();
    }

    public void sendPlayersPos(String playerName) {
        JSONObject json = new JSONObject();
        json.put(K_TYPE, "playerPosition");
        // json.put("p1", "27 " + String.valueOf(res/2 - playerHeight));
        json.put("playerName", playerName);
        json.put("position", getNormalizedPosition(27, clientsData.get(playerName).posY)[0] + " " + getNormalizedPosition(27, clientsData.get(playerName).posY)[1]);

        sendBroadCast(json.toString());
    }

    public void sendGoalScored(String playerName) {
        JSONObject json = new JSONObject();
        json.put(K_TYPE, "goalScored");
        json.put("playerName", playerName);

        sendBroadCast(json.toString());
        checkGameEnd();
        
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
    

   
    public void checkGameEnd() {

        int g1 = clientsData.get(playersArray[0]).goalScored;
        int g2 = clientsData.get(playersArray[1]).goalScored;

        if (g1 >= MAX_GOALS || g2 >= MAX_GOALS) {
            isPlaying = false; 
            announceWinner();
        }
        
    }

   
    public void announceWinner() {
        String winner = null;
        String loser = null;

        int g1 = clientsData.get(playersArray[0]).goalScored;
        int g2 = clientsData.get(playersArray[1]).goalScored;

        if (g1> g2) {
            winner = playersArray[0];
            loser = playersArray[1];
        } else {
            winner = playersArray[1];
            loser = playersArray[0];
        }

        JSONObject json = new JSONObject();
        json.put(K_TYPE, "gameOver");
        json.put("winner", winner);
        json.put("loser", loser);
        json.put("scoreP1", g1);
        json.put("scoreP2", g2);

        sendBroadCast(json.toString());

        log("Game finished. Winner: " + winner);
    }
}

class Ball {
    double posX;
    double posY;
    double velX;
    double velY;

    public Ball(double posX, double posY, double velX, double velY) {
        this.posX = posX;
        this.posY = posY;
        this.velX = velX;
        this.velY = velY;
    }
}
