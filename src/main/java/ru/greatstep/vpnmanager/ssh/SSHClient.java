package ru.greatstep.vpnmanager.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
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

        Properties config = new Properties();

        // Базовые настройки
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");

        // ---- РЕШЕНИЕ ПРОБЛЕМЫ "Algorithm negotiation fail" ----
        // Ваш роутер использует ssh-ed25519, но проблема в Kex или Cipher

        // 1. Алгоритмы обмена ключами (Kex) - добавляем все возможные
        config.put("kex", "diffie-hellman-group-exchange-sha256," +
                "diffie-hellman-group-exchange-sha1," +
                "diffie-hellman-group14-sha1," +
                "diffie-hellman-group1-sha1," +
                "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521");

        // 2. Алгоритмы шифрования (Cipher) - добавляем все возможные
        config.put("cipher.s2c", "aes256-ctr,aes192-ctr,aes128-ctr," +
                "aes256-cbc,aes192-cbc,aes128-cbc," +
                "blowfish-cbc,3des-cbc,arcfour128,arcfour256,arcfour");
        config.put("cipher.c2s", "aes256-ctr,aes192-ctr,aes128-ctr," +
                "aes256-cbc,aes192-cbc,aes128-cbc," +
                "blowfish-cbc,3des-cbc,arcfour128,arcfour256,arcfour");

        // 3. Алгоритмы MAC
        config.put("mac.s2c", "hmac-sha2-256,hmac-sha2-512," +
                "hmac-sha1,hmac-sha1-96,hmac-md5,hmac-md5-96");
        config.put("mac.c2s", "hmac-sha2-256,hmac-sha2-512," +
                "hmac-sha1,hmac-sha1-96,hmac-md5,hmac-md5-96");

        // 4. Алгоритмы ключа хоста - оставляем все, включая ваш ed25519
        config.put("server_host_key", "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,ssh-rsa,ssh-dss");

        session.setConfig(config);

        // Увеличиваем таймаут
        session.connect((int) Duration.ofSeconds(60).toMillis());
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