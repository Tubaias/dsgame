import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Game {
    private Scanner input;
    ServerSocket serverSocket;
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

    public void stop() throws IOException {
        serverSocket.close();
    }

    private void sendMode() throws IOException {
        System.out.println("Insert ip address to connect to:");
        String ip = input.nextLine();
        Socket clientSocket;
        try {
            clientSocket = new Socket(ip, PORT);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
        
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        System.out.println("Connected. Insert message:");

        String line = "";
        while (!line.equals("quit")) {
            System.out.println("Insert message:");
            line = input.nextLine();
            out.println(line);
        }

        in.close();
        out.close();
        clientSocket.close();
    }

    private void receiveMode() throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);
        
        while (true) {
            new ClientThread(serverSocket.accept()).start();
        }
    }

    private static class ClientThread extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
    
        public ClientThread(Socket socket) {
            this.socket = socket;
        }
    
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                String input;
                while ((input = in.readLine()) != null) {
                    System.out.println("got input: " + input);

                    if (input.equals("quit")) {
                        break;
                    }
                }
        
                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                return;
            }
        }
    }
}
