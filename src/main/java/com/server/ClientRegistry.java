package com.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.json.JSONArray;

/**
 * Registre de clients connectats amb gestió interna del pool de noms.
 *
 * Manté dos mapes bidireccionals:
 * - WebSocket a nom de client
 * - Nom de client a WebSocket
 *
 * També integra la lògica d'un pool de noms disponibles. Quan un client es connecta,
 * se li assigna un nom lliure. Quan es desconnecta, el nom torna al pool per ser reutilitzat.
 *
 * Aquesta classe és segura per a ús concurrent gràcies a l'ús de ConcurrentHashMap
 * i ConcurrentLinkedQueue. Els mètodes que modifiquen el pool utilitzen sincronització
 * per garantir la coherència durant reinicialitzacions.
 */
final class ClientRegistry {

    /** Mapa de sockets a noms de client. */
    private final Map<WebSocket, String> bySocket = new ConcurrentHashMap<>();

    /** Mapa de noms de client a sockets. */
    private final Map<String, WebSocket> byName = new ConcurrentHashMap<>();

    private Map<String, WebSocket> avaliblePlayers = new ConcurrentHashMap<>();

    /**
     * Afegeix un client nou.
     *
     * @param socket socket del client connectat
     * @param name nombre del client
     * @return el nom assignat al client
     */
    String add(WebSocket socket, String name) {
        bySocket.put(socket, name);
        byName.put(name, socket);
        return name;
    }

    /**
     * Elimina un client del registre.
     *
     * @param socket socket del client a eliminar
     * @return el nom que estava assignat, o null si no existia
     */
    String remove(WebSocket socket) {
        String name = bySocket.remove(socket);
        if (name != null) {
            byName.remove(name);
            removeClientFromAvaliblePlayers(socket);
        }
        return name;
    }

    /**
     * Obté el socket associat a un nom de client.
     *
     * @param name nom del client
     * @return socket associat o null si no existeix
     */
    WebSocket socketByName(String name) {
        return byName.get(name);
    }

    /**
     * Obté el nom associat a un socket.
     *
     * @param socket socket del client
     * @return nom del client o null si no existeix
     */
    String nameBySocket(WebSocket socket) {
        return bySocket.get(socket);
    }

    /**
     * Retorna la llista actual de noms de clients connectats en format JSONArray.
     *
     * @return JSONArray amb els noms dels clients actius
     */
    JSONArray currentNames() {
        JSONArray arr = new JSONArray();
        for (String n : byName.keySet()) {
            arr.put(n);
        }
        return arr;
    }

    /**
     * Neteja el registre per a un socket desconnectat.
     * Equivalent a remove(socket).
     *
     * @param socket socket desconnectat
     * @return nom del client eliminat o null si no existia
     */
    String cleanupDisconnected(WebSocket socket) {
        return remove(socket);
    }

    /**
     * Retorna una còpia immutable de l'estat actual del mapa socket a nom.
     * Útil per iteracions fora del lock intern sense risc de ConcurrentModification.
     *
     * @return mapa immutable de WebSocket a nom
     */
    Map<WebSocket, String> snapshot() {
        return Map.copyOf(bySocket);
    }

    String addClientToAvaliblePlayers(WebSocket socket, String name) {
        avaliblePlayers.put(name, socket);
        return name;
    }

    String removeClientFromAvaliblePlayers(WebSocket socket) {
        for (String name : avaliblePlayers.keySet()) {
            if (avaliblePlayers.get(name) == socket) {
                avaliblePlayers.remove(name);
                return name;
            }
        }
        return null;
    }

    JSONArray currentAvaliblePlayersNames() {
        JSONArray arr = new JSONArray();
        for (String n: avaliblePlayers.keySet()) {
            arr.put(n);
        }
        return arr;
    }

    public boolean nameExists(String name) {
        return byName.containsKey(name);
    }
}
