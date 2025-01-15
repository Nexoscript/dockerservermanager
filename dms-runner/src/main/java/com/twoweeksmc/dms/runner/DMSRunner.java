package com.twoweeksmc.dms.runner;

import java.io.IOException;

import com.twoweeksmc.dms.common.server.ServerManager;
import com.twoweeksmc.dms.console.JLineConsole;

public class DMSRunner {
    private static DMSRunner instance;
    private final ServerManager serverManager;
    private final JLineConsole console;
    private Thread runnerThread;

    public static void main(String[] args) throws IOException {
        instance = new DMSRunner();
        instance.start();
    }

    public DMSRunner() throws IOException {
        this.console = new JLineConsole();
        this.serverManager = new ServerManager(10000, "E://Desktop/2weeksmc/dms-containers");
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

    public static DMSRunner getInstance() {
        return instance;
    }
}
