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
    private String basePath;
    private String platform;
    private String version;
    private String containerId;
    private int port;

    public ServerContainer(DockerClient dockerClient, String basePath, String platform, String version, int port) {
        this.dockerClient = dockerClient;
        this.basePath = basePath;
        this.platform = platform;
        this.version = version;
        this.port = port;
    }

    public ServerContainer(DockerClient dockerClient, String containerId) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
    }

    public String createAndStart() {
        String imageName = "itzg/minecraft-server";
        String uuid = UUID.randomUUID().toString();
        String serverPath = basePath + "/" + uuid + "/server";
        String containerName = "2weeksmc-server-" + uuid;
        File serverDir = new File(serverPath);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
        Volume serverVolume = new Volume("/data");
        ExposedPort containerPort = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(port));
        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .withHostConfig(HostConfig.newHostConfig()
                        .withBinds(new Bind(serverPath, serverVolume))
                        .withPortBindings(portBindings))
                .withEnv("EULA=TRUE", "TYPE=" + platform.toUpperCase(), "VERSION=" + version)
                .exec();
        this.containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();
        this.printContainerLogs(containerId);
        JSONObject serverInfoObject = new JSONObject();
        serverInfoObject.put("containerName", containerName);
        serverInfoObject.put("containerId", containerId);
        serverInfoObject.put("path", serverPath);
        serverInfoObject.put("port", port);
        String[] command = { "sh", "-c",
                "echo '" + serverInfoObject.toString()
                        + "' > /data/server-info.json" };
        try {
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(command).withAttachStdout(true).withAttachStderr(true).exec();

            ExecStartCmd execStartCmd = dockerClient.execStartCmd(execCreateCmdResponse.getId())
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
        String serverPath = basePath + "/" + uniqueId + "/server";
        String containerName = "2weeksmc-server-" + uniqueId;
        File serverDir = new File(serverPath);
        if (!serverDir.exists()) {
            serverDir.mkdirs();
        }
        Volume serverVolume = new Volume("/data");
        ExposedPort containerPort = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(port));
        CreateContainerResponse container = dockerClient.createContainerCmd("itzg/minecraft-server")
                .withName(containerName)
                .withHostConfig(HostConfig.newHostConfig()
                        .withBinds(new Bind(serverPath, serverVolume))
                        .withPortBindings(portBindings))
                .withEnv("EULA=TRUE", "TYPE=" + platform.toUpperCase(), "VERSION=" + version)
                .exec();
        this.containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();
        this.printContainerLogs(containerId);
        return "/" + containerName;
    }

    public void printContainerLogs(String containerId) {
        try {
            dockerClient.logContainerCmd(containerId)
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
        if (containerId == null) {
            return;
        }
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void restart() {
        if (containerId == null) {
            return;
        }
        dockerClient.restartContainerCmd(containerId).exec();
    }

    public void stop() {
        System.out.println("containerId = " + containerId);
        if (containerId == null) {
            return;
        }
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public void remove() {
        if (containerId == null) {
            return;
        }
        dockerClient.removeContainerCmd(containerId).exec();
    }
}
