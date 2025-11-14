package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class FileServer {

    private final FileSystemManager fsManager;
    private final int port;
    private final ExecutorService threadPool;

    // Constructor for FileServer
    public FileServer(int port, String fileSystemName, int totalSize) {
        this.port = port;
        try {
            this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize FileSystemManager", e);
        }

        // Thread pool 
        int poolSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r);
            t.setName("ClientHandler-" + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
        this.threadPool = Executors.newFixedThreadPool(poolSize, tf);
    }

    //initialize server
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("FileServer started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(120_000);
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                threadPool.submit(new ClientHandler(clientSocket, fsManager));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            shutdownPool();
        }
    }

    private void shutdownPool() {
        threadPool.shutdown();
        System.out.println("Server thread pool shutting down.");
    }


    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final FileSystemManager fsManager;

        public ClientHandler(Socket socket, FileSystemManager fsManager) {
            this.clientSocket = socket;
            this.fsManager = fsManager;
        }

        @Override
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
            ) {
                writer.println(" Connected to FileServer. Type commands:");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    System.out.println("[" + Thread.currentThread().getName() + " - " + clientSocket.getRemoteSocketAddress() + "] " + line);
                    String[] parts = line.split(" ", 3);
                    String command = parts[0].toUpperCase();

                    //handles client commands 
                    try {
                        switch (command) {
                            case "CREATE":
                                if (parts.length < 2) throw new Exception("Usage: CREATE <filename>");
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                break;

                            case "WRITE":
                                if (parts.length < 3) throw new Exception("Usage: WRITE <filename> <content>");
                                fsManager.writeFile(parts[1], parts[2]);
                                writer.println("SUCCESS: File '" + parts[1] + "' written.");
                                break;

                            case "READ":
                                if (parts.length < 2) throw new Exception("Usage: READ <filename>");
                                String data = fsManager.readFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' contents: " + data);
                                break;

                            case "DELETE":
                                if (parts.length < 2) throw new Exception("Usage: DELETE <filename>");
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                break;

                            case "LIST":
                                String[] files = fsManager.listFiles();
                                writer.println("FILES: " + (files.length == 0 ? "(empty)" : String.join(", ", files)));
                                break;

                            case "QUIT":
                                writer.println("Goodbye!");
                                clientSocket.close();
                                return;

                            default:
                                writer.println("ERROR: Unknown command '" + command + "'");
                                break;
                        }
                    } catch (Exception e) {
                        writer.println("ERROR: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
            } finally {
                try { clientSocket.close(); } catch (Exception ignored) {}
            }
        }
    }
}
