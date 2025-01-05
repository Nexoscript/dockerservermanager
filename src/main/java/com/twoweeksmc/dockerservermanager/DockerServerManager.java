package com.twoweeksmc.dockerservermanager;


import com.twoweeksmc.dockerservermanager.console.JLineConsole;
import com.twoweeksmc.dockerservermanager.server.ServerManager;

import java.io.IOException;

public class DockerServerManager {
    private static DockerServerManager instance;
    private final ServerManager serverManager;
    private final JLineConsole console;

    public static void main(String[] args) throws IOException {
        new DockerServerManager();
    }

    public DockerServerManager() throws IOException {
        instance = this;
        this.console = new JLineConsole();
        this.serverManager = new ServerManager();
        this.serverManager.start();
        this.console.setServerManager(this.serverManager);
        this.console.start();
        this.serverManager.close();
    }

    public JLineConsole getConsole() {
        return console;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public static DockerServerManager getInstance() {
        return instance;
    }
}
