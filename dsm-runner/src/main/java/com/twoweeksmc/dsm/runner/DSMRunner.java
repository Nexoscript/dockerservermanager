package com.twoweeksmc.dsm.runner;

import java.io.IOException;

import com.twoweeksmc.dsm.common.server.ServerManager;
import com.twoweeksmc.dsm.console.JLineConsole;

public class DSMRunner {
    private static DSMRunner instance;
    private final ServerManager serverManager;
    private final JLineConsole console;
    private Thread runnerThread;

    public static void main(String[] args) throws IOException {
        instance = new DSMRunner();
        instance.start();
    }

    public DSMRunner() throws IOException {
        this.console = new JLineConsole();
        this.serverManager = new ServerManager(10000, "/home/eztxmmc/.dsm-containers");
    }

    public void start() {
        this.runnerThread = this.serverManager;
        this.runnerThread.start();
        this.console.setServerManager(this.serverManager);
        this.console.start();
        this.serverManager.close();
    }

    public Thread getRunnerThread() {
        return runnerThread;
    }

    public JLineConsole getConsole() {
        return console;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public static DSMRunner getInstance() {
        return instance;
    }
}
