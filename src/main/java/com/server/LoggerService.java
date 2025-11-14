package com.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class LoggerService {
    private static final String URL = "jdbc:sqlite:pong_logs.db";

    public static void saveLog(String player, String action) {
        String sql = "INSERT INTO game_logs(player, action) VALUES(?, ?)";
        System.out.println("");
        try (Connection conn = DriverManager.getConnection(URL);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, action);
            ps.executeUpdate();
            System.out.println("Log guardado: " + player + " -> " + action);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
