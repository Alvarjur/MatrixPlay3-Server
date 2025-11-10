package com.shared;

import org.json.JSONObject;

public class ClientData {
    public String name;
    public double posY;
    

    public ClientData(String name) {
        this.name = name;
        this.posY = 0;
    }

    public ClientData(String name, double posY) {
        this.name = name;
        this.posY = posY;
        
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }

    // Converteix l'objecte a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("posY", posY);
        return obj;
    }

    // Crea un ClientData a partir de JSON
    public static ClientData fromJSON(JSONObject obj) {
        String name = obj.optString("name", null);

        ClientData cd = new ClientData(name);
        cd.posY = obj.optDouble("posY", 0);
        return cd;
    }
}
