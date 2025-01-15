package com.twoweeksmc.dms.console;

import com.twoweeksmc.dms.common.server.ServerManager;
import com.twoweeksmc.dms.common.server.ServerState;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class JLineConsole {
    private final Terminal terminal;
    private final LineReaderImpl reader;

    private ServerManager serverManager;

    private boolean isRunning;

    public JLineConsole() throws IOException {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .dumb(true)
                .encoding(StandardCharsets.UTF_8)
                .build();
        this.terminal.enterRawMode();
        this.reader = (LineReaderImpl) LineReaderBuilder.builder()
                .terminal(this.terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.AUTO_PARAM_SLASH, false)
                .build();
        AttributedString coloredPrefix = new AttributedString(this.userPrefix());
        this.reader.setPrompt(coloredPrefix.toAnsi());
        this.isRunning = true;
        this.clear();
        this.sendWelcomeMessage();
    }

    public void start() {
        while (this.isRunning) {
            try {
                AttributedString coloredPrefix = new AttributedString(this.userPrefix());
                String input = this.reader.readLine(coloredPrefix.toAnsi()).trim();
                if (input.isEmpty()) {
                    this.print("[FF3333]The input field can not be empty");
                    continue;
                }
                String[] inputParts = input.split(" ");
                String command = inputParts[0];
                String[] args = Arrays.copyOfRange(inputParts, 1, inputParts.length);
                switch (command) {
                    case "clear" -> this.clear();
                    case "create-container" -> {
                        if (args.length < 2) {
                            this.print("[FF3333]Need platform and version argument");
                            continue;
                        }
                        this.serverManager.createServerContainer(args[0].toLowerCase(), args[1].toLowerCase());
                    }
                    case "start-container" -> {
                        if (args.length < 1) {
                            this.print("[FF3333]Need container name");
                            continue;
                        }
                        this.serverManager.startServerContainer(args[0]);
                    }
                    case "recreate-container" -> {
                        if (args.length < 3) {
                            this.print("[FF3333]Need uniqueId, platform and version argument");
                            continue;
                        }
                        this.serverManager.recreateServerContainer(args[1].toLowerCase(), args[2].toLowerCase(),
                                args[0]);
                    }
                    case "restart-container" -> {
                        if (args.length < 1) {
                            this.print("[FF3333]Need container name");
                            continue;
                        }
                        this.serverManager.restartServerContainer(args[0]);
                    }
                    case "stop-container" -> {
                        if (args.length < 1) {
                            this.print("[FF3333]Need container name");
                            continue;
                        }
                        this.serverManager.stopServerContainer(args[0]);
                    }
                    case "remove-container" -> {
                        if (args.length < 1) {
                            this.print("[FF3333]Need container name");
                            continue;
                        }
                        if (this.serverManager.getServerStateByName(args[0]).equals(ServerState.ONLINE)) {
                            this.serverManager.stopServerContainer(args[0]);
                        }
                        this.serverManager.removeServerContainer(args[0]);
                    }
                    case "list-containers" -> {
                        this.serverManager.getContainers()
                                .forEach(container -> this.print("&e" + container.getNames()[0] + " - "
                                        + this.serverManager.getServerStateById(container.getId())));
                    }
                    case "exit", "stop", "shutdown" -> {
                        this.isRunning = false;
                        this.print("Stopping server...");
                    }
                    case "help" -> {
                        this.print("&7-------------------------------&eHelp&7-------------------------------");
                        this.print(" &ecreate-container <platform> <version> &7- &f.");
                        this.print(" &estart-container <name> &7- &f.");
                        this.print(" &erecreate-container <uniqueId> <platform> <version> &7- &f.");
                        this.print(" &erestart-container <name> &7- &f.");
                        this.print(" &estop-container <name> &7- &f.");
                        this.print(" &eremove-container <name> &7- &f.");
                        this.print(" &elist-containers &7- &f.");
                        this.print(" &eclear &7- &fClear the console.");
                        this.print(" &eexit, shutdown, stop &7- &fShutdown the cloud.");
                        this.print(" &ehelp &7- &fShow this help menu.");
                        this.print("&7-------------------------------&eHelp&7-------------------------------");
                    }
                    default -> this.print("Unknown command: " + command);
                }
            } catch (EndOfFileException e) {
                throw new RuntimeException(e);
            }
        }
        this.print("Stopped server.");
        System.exit(0);

    }

    public String prefix() {
        String prefix = "[FB1364-F9A608]2weeksmc - dockermanager &7» &f";
        return ConsoleColor.apply("\r" + prefix);
    }

    public String userPrefix() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String prefix = "[FB1364-F9A608]%hostname &7» &f".replace("%hostname", hostname);
            return ConsoleColor.apply("\r" + prefix);

        } catch (UnknownHostException e) {
            return ConsoleColor
                    .apply("\r" + "[FB1364-F9A608]2weeksmc&7@&e%hostname &7» &f".replace("%hostname", "unknown"));
        }
    }

    public void sendWelcomeMessage() {
        System.out.print("\n");
        System.out.print("\n");
        System.out
                .println(ConsoleColor
                        .apply("             [FB1364-F9A608]2weeksmc - dockermanager &7- &e1.0.0&7@&edevelopment"));
        System.out.println(ConsoleColor.apply("                     &fby &eezTxmMC&7 & &eDragonRex"));
        System.out.print("\n");
        System.out.println(ConsoleColor.apply("                     &fType &ehelp &fto list all commands."));
        System.out.print("\n");
        System.out.print("\n");
    }

    public void print(String message) {
        this.print(message, true);
    }

    public void print(String message, boolean newLine) {
        String coloredMessage = ConsoleColor.apply(this.prefix() + message);
        if (newLine) {
            System.out.println(coloredMessage);
            return;
        }
        System.out.print(coloredMessage);
    }

    public void clear() {
        this.terminal.puts(InfoCmp.Capability.clear_screen);
        this.terminal.flush();
    }

    public void setServerManager(ServerManager serverManager) {
        this.serverManager = serverManager;
    }
}
