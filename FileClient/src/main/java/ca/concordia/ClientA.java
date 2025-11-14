package ca.concordia;

import java.io.*;
import java.net.Socket;


//test client A
public class ClientA {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("ClientA connected to server");


            System.out.println(reader.readLine());


            writer.println("CREATE testA.txt");
            System.out.println(reader.readLine());

            writer.println("WRITE testA.txt Hello from ClientA");
            System.out.println(reader.readLine());

            writer.println("READ testA.txt");
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                System.out.println(line);
                if (line.contains("Goodbye")) break;
            }

            writer.println("QUIT");
            System.out.println("ClientA done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
