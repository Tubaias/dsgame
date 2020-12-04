import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Game {
    private Scanner input;
    private final int PORT = 25565;

    public Game(Scanner input) {
        this.input = input;
    }

    public void start() throws IOException {
        System.out.println("Send or receive?");
        String command = input.nextLine();
        if (command.startsWith("s")) {
            sendMode();
        } else if (command.startsWith("r")) {
            receiveMode();
        } else {
            System.out.println("Unknown command.");
        }
    }

    private void sendMode() throws IOException {
        System.out.println("Insert ip address to connect to:");
        String ip = input.nextLine();
        Socket clientSocket = new Socket(ip, PORT);
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        System.out.println("Connected. Insert message:");

        out.println(input.nextLine());
        System.out.println(in.readLine());

        in.close();
        out.close();
        clientSocket.close();
    }

    private void receiveMode() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);
        Socket clientSocket = serverSocket.accept();
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        System.out.println("Connected.");

        System.out.println(in.readLine());
        out.println(input.nextLine());

        in.close();
        out.close();
        serverSocket.close();
        clientSocket.close();
    }
}
