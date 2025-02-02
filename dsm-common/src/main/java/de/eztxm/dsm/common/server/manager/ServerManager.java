package de.eztxm.dsm.common.server.manager;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.eztxm.dsm.common.server.ServerState;
import de.eztxm.dsm.common.server.container.ServerContainer;
import org.json.JSONException;
import org.json.JSONObject;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

public class ServerManager extends Thread {
    private final String prefix;
    private final int startPort;
    private final String basePath;
    private DockerClient dockerClient;
    private HashMap<String, ServerContainer> serverContainers;
    private HashMap<String, Path> containerPaths;

    public ServerManager(String prefix, int startPort, String basePath) {
        this.prefix = prefix;
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
        this.mapping();
    }

    public void createServerContainer(String platform, String version) {
        this.mapping();
        ServerContainer container = new ServerContainer(this.prefix, this.dockerClient, this.basePath, platform, version,
                this.getFreePort());
        container.start();
        String containerId = container.createAndStartContainer();
        this.serverContainers.put(containerId, container);
    }

    public void recreateServerContainer(String platform, String version, String uniqueId) {
        this.mapping();
        ServerContainer container = new ServerContainer(this.prefix, this.dockerClient, this.basePath, platform, version,
                this.getFreePort());
        container.start();
        String containerId = container.recreateAndStartContainerFromDirectory(uniqueId);
        this.serverContainers.put(containerId, container);
    }

    public void startServerContainer(String containerName) {
        this.mapping();
        if (containerName.equalsIgnoreCase("*")) {
            for (ServerContainer container : this.serverContainers.values()) {
                container.start();
            }
            return;
        }
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.start();
    }

    public void restartServerContainer(String containerName) {
        this.mapping();
        if (containerName.equalsIgnoreCase("*")) {
            for (ServerContainer container : this.serverContainers.values()) {
                container.restartContainer();
            }
            return;
        }
        if (!this.serverContainers.containsKey(containerName)) {
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.restartContainer();
    }

    public void stopServerContainer(String containerName) {
        this.mapping();
        if (containerName.equalsIgnoreCase("*")) {
            for (ServerContainer container : this.serverContainers.values()) {
                container.stopContainer();
            }
            return;
        }
        if (!this.serverContainers.containsKey(containerName)) {
            System.out.println("Container " + containerName + " not found");
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.stopContainer();
    }

    public void removeServerContainer(String containerName) {
        this.mapping();
        if (containerName.equalsIgnoreCase("*")) {
            for (ServerContainer container : this.serverContainers.values()) {
                container.removeContainer();
            }
            return;
        }
        if (!this.serverContainers.containsKey(containerName)) {
            System.out.println("Container " + containerName + " not found");
            return;
        }
        ServerContainer serverContainer = this.serverContainers.get(containerName);
        serverContainer.removeContainer();
        this.serverContainers.remove(containerName);
        this.containerPaths.remove(containerName);
    }

    public int getFreePort() {
        int port = this.startPort;
        while (true) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setReuseAddress(true);
                if (!isPortUsedByContainer(port)) {
                    return port;
                }
                port++;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private boolean isPortUsedByContainer(int port) {
        for (Path path : containerPaths.values()) {
            File serverInfoFile = new File(path.toFile(), "server-info.json");
            if (!serverInfoFile.exists()) {
                System.out.println("File " + serverInfoFile.getAbsolutePath() + " not found");
                continue;
            }
            try {
                String content = new String(Files.readAllBytes(serverInfoFile.toPath()));
                JSONObject jsonObject = new JSONObject(content);
                int containerPort = jsonObject.getInt("port");
                if (containerPort == port) {
                    return true;
                }
            } catch (IOException e) {
                System.err.println(
                        "Error while reading file " + serverInfoFile.getAbsolutePath() + ": " + e.getMessage());
            } catch (JSONException e) {
                System.err.println("Error while parse file content to json " + serverInfoFile.getAbsolutePath() + ": "
                        + e.getMessage());
            }
        }
        return false;
    }

    public ServerState getServerStateByName(String containerName) {
        String state = "";
        try {
            state = this.getContainerByName(containerName).getState();
        } catch (NullPointerException ignored) {
        }
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
                .filter(container -> container.getNames()[0].equalsIgnoreCase("/" + containerName.replace("/", "")))
                .findFirst()
                .orElse(null);
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
                        if (name.startsWith("/" + this.prefix +  "-")) {
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
                        if (name.startsWith("/" + prefix +  "-")) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toMap(
                        container -> container.getNames()[0].replace("/", ""),
                        Container::getId));
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return ids;
    }

    public void mapping() {
        this.serverContainers = new HashMap<>();
        this.containerPaths = new HashMap<>();
        this.getContainerNamesAndIds().forEach((containerName, containerId) -> {
            ServerContainer container = new ServerContainer(this.prefix, this.dockerClient, this.basePath, containerId,
                    containerName.split(prefix + "-")[1]);
            container.start();
            this.serverContainers.put(containerName, container);
            this.containerPaths.put(containerName, Path.of(container.getServerPath()));
        });
    }

    public void close() {
        try {
            this.dockerClient.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
