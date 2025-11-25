package com.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;



//En esta clase guardamos las acciones en la base de datos


public class LoggerService {
    private static final String URL = "jdbc:sqlite:pong_logs.db";

    public static void saveLog(String player, String clientType, String action) {
        String sql = "INSERT INTO game_logs(player, clientType ,action) VALUES(?, ?, ?)";
        System.out.println("");
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, clientType);
            ps.setString(3, action);
            ps.executeUpdate();
            System.out.println("Log guardado: " + player + " ( " + clientType + " )  -> " + action);
        

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
