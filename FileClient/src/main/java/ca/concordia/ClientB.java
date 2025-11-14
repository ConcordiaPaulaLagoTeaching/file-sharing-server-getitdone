package ca.concordia;

import java.io.*;
import java.net.Socket;

//test client B
public class ClientB {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("ClientB connected to server");
            System.out.println(reader.readLine());

            Thread.sleep(5000);

            writer.println("WRITE testA.txt Added text from ClientB");
            System.out.println(reader.readLine());

            writer.println("READ testA.txt");
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println(line);
                if (line.contains("Goodbye")) break;
            }

            writer.println("QUIT");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
