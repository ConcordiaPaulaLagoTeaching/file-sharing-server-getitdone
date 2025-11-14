package ca.concordia;

import java.io.*;
import java.net.Socket;


//test client C
public class ClientC {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("ClientC connected to server");
            System.out.println(reader.readLine());

            String filename = "testA.txt"; 

            for (int i = 1; i <= 5; i++) {
                writer.println("READ " + filename);
                String responseLine;

                System.out.println("\n[ClientC] Reading attempt #" + i);
                while ((responseLine = reader.readLine()) != null) {
                    System.out.println(responseLine);
                    if (responseLine.startsWith("SUCCESS:") || responseLine.startsWith("ERROR:"))
                        break;
                }

                Thread.sleep(3000);
            }

            writer.println("QUIT");
            System.out.println("ClientC finished monitoring and disconnected.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
