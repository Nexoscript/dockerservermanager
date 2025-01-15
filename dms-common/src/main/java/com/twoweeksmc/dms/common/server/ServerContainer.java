package com.twoweeksmc.dms.common.server;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;

import java.io.Closeable;
import java.io.File;
import java.util.UUID;

import org.json.JSONObject;

public class ServerContainer {
    private final DockerClient dockerClient;
    private final String basePath;
    private String platform;
    private String version;
    private String containerId;
    private UUID uniqueId;
    private String serverPath;
    private int port;

    public ServerContainer(DockerClient dockerClient, String basePath, String platform, String version, int port) {
        this.dockerClient = dockerClient;
        this.basePath = basePath;
        this.platform = platform;
        this.version = version;
        this.port = port;
    }

    public ServerContainer(DockerClient dockerClient, String basePath, String containerId, String uniqueId) {
        this.dockerClient = dockerClient;
        this.basePath = basePath;
        this.containerId = containerId;
        this.uniqueId = UUID.fromString(uniqueId);
        this.serverPath = this.basePath + "/" + this.uniqueId + "/server";
    }

    public String createAndStart() {
        this.uniqueId = UUID.randomUUID();
        this.serverPath = this.basePath + "/" + this.uniqueId + "/server";
        String imageName = "itzg/minecraft-server";
        String containerName = "2weeksmc-server-" + this.uniqueId;
        File serverDir = new File(this.serverPath);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
        Volume serverVolume = new Volume("/data");
        ExposedPort containerPort = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(this.port));
        CreateContainerResponse container = this.dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostConfig(HostConfig.newHostConfig()
                        .withBinds(new Bind(this.serverPath, serverVolume))
                        .withPortBindings(portBindings))
                .withEnv("EULA=TRUE", "TYPE=" + this.platform.toUpperCase(), "VERSION=" + this.version)
                .exec();
        this.containerId = container.getId();
        System.out.println(this.containerId);
        this.dockerClient.startContainerCmd(this.containerId).exec();
        this.printContainerLogs(this.containerId);
        JSONObject serverInfoObject = new JSONObject();
        serverInfoObject.put("containerName", containerName);
        serverInfoObject.put("containerId", this.containerId);
        serverInfoObject.put("path", this.serverPath);
        serverInfoObject.put("port", this.port);
        String[] command = { "sh", "-c",
                "echo '" + serverInfoObject
                        + "' > /data/server-info.json" };
        try {
            ExecCreateCmdResponse execCreateCmdResponse = this.dockerClient.execCreateCmd(this.containerId)
                    .withCmd(command).withAttachStdout(true).withAttachStderr(true).exec();

            ExecStartCmd execStartCmd = this.dockerClient.execStartCmd(execCreateCmdResponse.getId())
                    .withDetach(false).withTty(true);
            execStartCmd.exec(new ResultCallback<>() {
                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onComplete() {
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onNext(Frame frame) {
                }

                @Override
                public void close() {
                }
            });

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return "/" + containerName;
    }

    public String recreateAndStartFromDirectory(String uniqueId) {
        this.uniqueId = UUID.fromString(uniqueId);
        this.serverPath = this.basePath + "/" + this.uniqueId + "/server";
        String containerName = "2weeksmc-server-" + this.uniqueId;
        File serverDir = new File(serverPath);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
        Volume serverVolume = new Volume("/data");
        ExposedPort containerPort = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(this.port));
        CreateContainerResponse container = this.dockerClient.createContainerCmd("itzg/minecraft-server")
                .withName(containerName)
                .withHostConfig(HostConfig.newHostConfig()
                        .withBinds(new Bind(serverPath, serverVolume))
                        .withPortBindings(portBindings))
                .withEnv("EULA=TRUE", "TYPE=" + this.platform.toUpperCase(), "VERSION=" + this.version)
                .exec();
        this.containerId = container.getId();
        this.dockerClient.startContainerCmd(this.containerId).exec();
        this.printContainerLogs(this.containerId);
        return "/" + containerName;
    }

    public void printContainerLogs(String containerId) {
        try {
            this.dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                        }
                    }).awaitCompletion();
        } catch (InterruptedException e) {
            System.err.println("Fehler beim Abrufen der Logs: " + e.getMessage());
        }
    }

    public void start() {
        if (this.containerId == null) {
            return;
        }
        this.dockerClient.startContainerCmd(this.containerId).exec();
    }

    public void restart() {
        if (this.containerId == null) {
            return;
        }
        this.dockerClient.restartContainerCmd(this.containerId).exec();
    }

    public void stop() {
        if (this.containerId == null) {
            return;
        }
        this.dockerClient.stopContainerCmd(this.containerId).exec();
    }

    public void remove() {
        if (this.containerId == null) {
            return;
        }
        this.dockerClient.removeContainerCmd(this.containerId).exec();
    }

    public String getServerPath() {
        return this.serverPath;
    }
}
