package com.shared;

import org.json.JSONObject;

public class ClientData {
    public String name;
    public String clientType;
    public double posY;
<<<<<<< HEAD
    public int goals;
=======
    public int goalScored;
>>>>>>> dev
    

    public ClientData(String name, String clientType) {
        this.name = name;
        this.clientType = clientType;
        this.posY = 576/2f;
<<<<<<< HEAD
        this .goals = 0;
=======
        this.goalScored = 0;
>>>>>>> dev
    }

    public ClientData(String name, String clientType, double posY) {
        this.name = name;
        this.clientType = clientType;
        this.posY = posY;
<<<<<<< HEAD
        this.goals = goals;
        
=======
        this.goalScored = 0;
>>>>>>> dev
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
        String clType = obj.optString("clientType", null);
        int goals = obj.optInt("goals", 0);

        ClientData cd = new ClientData(name, clType);
        cd.posY = obj.optDouble("posY", 0);
        cd.goalScored = obj.optInt("goalScored", 0);
        return cd;
    }
}
