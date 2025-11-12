package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private final FileSystemManager fsManager;
    private final int port;

    public FileServer(int port, String fileSystemName, int totalSize) {
        this.port = port;
        try {
            this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize FileSystemManager");
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("✅ FileServer started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("➡️ New client connected: " + clientSocket.getInetAddress());
                // Handle each client in a separate thread
                new Thread(new ClientHandler(clientSocket, fsManager)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Could not start server on port " + port);
        }
    }

    // ============================================================
    // Inner Class: Handles one client connection per thread
    // ============================================================
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
                String line;
                writer.println("✅ Connected to FileServer. Type commands:");

                while ((line = reader.readLine()) != null) {
                    System.out.println("[Client " + clientSocket.getInetAddress() + "] " + line);
                    String[] parts = line.split(" ", 3); // Allow space in content for WRITE

                    if (parts.length == 0) continue;
                    String command = parts[0].toUpperCase();

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
                                writer.println("SUCCESS: File '" + parts[1] + "' contents:\n" + data);
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

                    writer.flush();
                }

            } catch (IOException e) {
                System.err.println("Client disconnected: " + clientSocket.getInetAddress());
            }
        }
    }
}