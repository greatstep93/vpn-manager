package ru.greatstep.vpnmanager.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SSHClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private Session session;

    public SSHClient(String host, int port, String username, String password) {
        this.host = isBlank(host) ? "192.168.2.1" : host;
        this.port = 22;
        this.username = isBlank(username) ? "root" : username;
        this.password = isBlank(password) ? "djljgfL!@50" : password;
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public void connect() throws JSchException {
        JSch jsch = new JSch();
        session = jsch.getSession(username, host, port);
        session.setPassword(password);

        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password");
        session.setConfig(config);

        session.connect((int) Duration.ofSeconds(30).toMillis());
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    public String executeCommand(String command) throws Exception {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to SSH server");
        }

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(channel.getInputStream()));
             BufferedReader err = new BufferedReader(new InputStreamReader(channel.getErrStream()))) {

            channel.connect();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var stdoutFuture = executor.submit(() -> {
                    in.lines().forEach(line -> output.append(line).append('\n'));
                });
                var stderrFuture = executor.submit(() -> {
                    err.lines().forEach(line -> errorOutput.append(line).append('\n'));
                });

                stdoutFuture.get();
                stderrFuture.get();
            }

            int exitStatus = channel.getExitStatus();
            if (exitStatus != 0 && !errorOutput.isEmpty()) {
                throw new RuntimeException("Command failed with exit code " + exitStatus +
                        ". Error: " + errorOutput);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error executing command: " + command, e);
        } finally {
            channel.disconnect();
        }

        return output.toString();
    }

    public List<String> executeCommandList(String command) throws Exception {
        String output = executeCommand(command);
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public void close() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}