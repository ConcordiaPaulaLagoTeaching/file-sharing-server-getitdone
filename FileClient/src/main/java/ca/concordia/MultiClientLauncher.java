package ca.concordia;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiClientLauncher {

    private static class TestClient implements Runnable {
        private final String clientName;
        private final String[] commands;

        public TestClient(String clientName, String[] commands) {
            this.clientName = clientName;
            this.commands = commands;
        }

        @Override
        public void run() {
            try (Socket socket = new Socket("localhost", 12345);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println(clientName + " connected to server");

                // Read initial server message
                System.out.println(reader.readLine());

                for (String cmd : commands) {
                    writer.println(cmd);
                    System.out.println("[" + clientName + "] Sent: " + cmd);

                    // Read server response
                    String response = reader.readLine();
                    System.out.println("[" + clientName + "] Response: " + response);

                    // Optional: small delay to simulate real interaction
                    Thread.sleep(500);
                }

                writer.println("QUIT");
                System.out.println(clientName + " finished and disconnected.");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // Create a thread pool for clients
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Define commands for each client
        String[] clientACommands = {"CREATE testA.txt", "WRITE testA.txt Hello from ClientA", "READ testA.txt"};
        String[] clientBCommands = {"READ testA.txt", "WRITE testA.txt Added text from ClientB", "READ testA.txt"};
        String[] clientCCommands = {"READ testA.txt", "READ testA.txt", "READ testA.txt"};

        // Submit clients to the executor
        executor.submit(new TestClient("ClientA", clientACommands));
        executor.submit(new TestClient("ClientB", clientBCommands));
        executor.submit(new TestClient("ClientC", clientCCommands));

        // Shutdown executor after all clients finish
        executor.shutdown();
    }
}
