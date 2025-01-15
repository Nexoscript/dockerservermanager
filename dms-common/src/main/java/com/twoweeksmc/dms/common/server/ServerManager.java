package com.twoweeksmc.dms.common.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

public class ServerManager extends Thread {
    private final int startPort;
    private final String basePath;
    private DockerClient dockerClient;
    private HashMap<String, ServerContainer> serverContainers;

    public ServerManager(int startPort, String basePath) {
        this.startPort = startPort;
        this.basePath = basePath;
    }

    @Override
    public void run() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        this.serverContainers = new HashMap<>();
        this.getContainerNamesAndIds().forEach((containerName, containerId) -> {
            this.serverContainers.put(containerName, new ServerContainer(this.dockerClient, containerId));
        });
    }

    public void createServerContainer(String platform, String version) {
        ServerContainer container = new ServerContainer(this.dockerClient, this.basePath, platform, version,
                this.getFreePort());
        String containerId = container.createAndStart();
        this.serverContainers.put(containerId, container);
    }

    public void recreateServerContainer(String platform, String version, String uniqueId) {
        ServerContainer container = new ServerContainer(this.dockerClient, this.basePath, platform, version,
                this.getFreePort());
        String containerId = container.recreateAndStartFromDirectory(uniqueId);
        this.serverContainers.put(containerId, container);
    }

    public void startServerContainer(String containerName) {
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.start();
    }

    public void restartServerContainer(String containerName) {
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.restart();
    }

    public void stopServerContainer(String containerName) {
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.stop();
    }

    public void removeServerContainer(String containerName) {
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.remove();
    }

    public int getFreePort() {
        int port = this.startPort;
        while (true) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setReuseAddress(true);
                if (!isPortUsedByContainer(port)) {
                    return port;
                }
            } catch (IOException e) {
                port++;
            }
        }
    }

    private boolean isPortUsedByContainer(int port) {
        System.out.println("TEST");
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();
        System.out.println("TEST2");
        for (Container container : containers) {
            System.out.println("TEST3");
            for (ContainerPort containerPort : container.getPorts()) {
                System.out.println("TEST4");
                if (containerPort.getPublicPort() == port) {
                    System.out.println("TEST5");
                    return true;
                }
            }
        }
        System.out.println("TEST6");
        return false;
    }

    public ServerState getServerStateByName(String containerName) throws NotFoundException {
        String state = this.getContainerByName(containerName).getState();
        return state.equalsIgnoreCase("running") ? ServerState.ONLINE : ServerState.OFFLINE;
    }

    public ServerState getServerStateById(String containerId) throws NotFoundException {
        String state = this.getContainerById(containerId).getState();
        return state.equalsIgnoreCase("running") ? ServerState.ONLINE : ServerState.OFFLINE;
    }

    public Container getContainerById(String containerId) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec()
                .stream()
                .filter(container -> container.getId().equalsIgnoreCase(containerId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Container " + containerId + " not found"));
    }

    public Container getContainerByName(String containerName) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec()
                .stream()
                .filter(container -> container.getNames()[0].equalsIgnoreCase(containerName))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Container " + containerName + " not found"));
    }

    public List<Container> getContainers() throws NotFoundException {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();
        if (containers.isEmpty()) {
            return new ArrayList<>();
        }
        List<Container> ids = containers.stream()
                .filter(container -> {
                    String[] names = container.getNames();
                    for (String name : names) {
                        if (name.startsWith("/2weeksmc-server-")) {
                            return true;
                        }
                    }
                    return false;
                }).toList();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return ids;
    }

    public Map<String, String> getContainerNamesAndIds() throws NotFoundException {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();
        if (containers.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> ids = containers.stream()
                .filter(container -> {
                    String[] names = container.getNames();
                    for (String name : names) {
                        if (name.startsWith("/2weeksmc-server-")) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toMap(
                        container -> container.getNames()[0],
                        Container::getId));
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return ids;
    }

    public void close() {
        try {
            this.dockerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
