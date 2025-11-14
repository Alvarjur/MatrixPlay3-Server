package com.client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class LogDataBase {
    public static void createTable() {// no creo que haga falta pero lo tego porsi acaso timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        String url = "jdbc:sqlite:pong_logs.db";
        String sql = """
                CREATE TABLE IF NOT EXISTS game_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player TEXT,
                        action TEXT);           
                """;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
                
            stmt.execute(sql); // Ejecuta la creación de la tabla
            System.out.println("Tabla 'game_logs' creada o ya existente.");
                    
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        createTable();
    }
}
