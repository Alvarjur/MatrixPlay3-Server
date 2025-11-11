package com.server;

import java.util.Timer;
import java.util.TimerTask;

public class ControllerCountdown {

    private static ControllerCountdown instance;

    private final Main main;


    public ControllerCountdown(Main main) {
        instance = this;
        this.main = main;
    }

    public static void start(int seconds) {
        if (instance != null) {
            instance.startCountdown(seconds);
        }
    }

    public void startCountdown(int seconds) {
    Timer timer = new Timer();
    final int[] current = {seconds};

    TimerTask task = new TimerTask() {
        @Override
        public void run() {
            System.out.println(current[0]);
            current[0]--;
            main.sendCountdown(current[0] + 1); // Value offset so it ends at 0

            if (current[0] < 0) {
                main.sendInitialPos();
                timer.cancel(); // detener el temporizador
            }
        }
    };

    timer.scheduleAtFixedRate(task, 0, 1000);
}
}