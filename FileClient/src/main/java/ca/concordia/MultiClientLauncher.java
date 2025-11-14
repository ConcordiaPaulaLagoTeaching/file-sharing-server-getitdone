package ca.concordia;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//test launcher for multiple clients
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


                System.out.println(reader.readLine());

                for (String cmd : commands) {
                    writer.println(cmd);
                    System.out.println("[" + clientName + "] Sent: " + cmd);

                    // Read server response
                    String response = reader.readLine();
                    System.out.println("[" + clientName + "] Response: " + response);


                    Thread.sleep(200);
                }

                writer.println("QUIT");
                System.out.println(clientName + " finished and disconnected.");

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // Create a thread pool large enough for 20 clients
        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Generate 20 clients
        for (int i = 1; i <= 20; i++) {
            String clientName = "Client" + i;

            //each client creates its own file and writes to it
            String fileName = "file" + i + ".txt";
            String[] commands = {
                    "CREATE " + fileName,
                    "WRITE " + fileName + " Hello from " + clientName,
                    "READ " + fileName
            };

            executor.submit(new TestClient(clientName, commands));
        }

        executor.shutdown();
    }
}
