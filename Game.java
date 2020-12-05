import java.net.*;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Scanner;
import java.util.Set;

public class Game {
    private Scanner keyboard;
    private final int PORT = 25565;

    private ServerSocket serverSocket;
    private Socket rightSocket;
    private Socket leftSocket;
    private SocketHandler rightHandler;
    private SocketHandler leftHandler;

    private ArrayDeque<String> rightIn;
    private ArrayDeque<String> rightOut;
    private ArrayDeque<String> leftIn;
    private ArrayDeque<String> leftOut;

    public Game(Scanner input) {
        this.keyboard = input;
        rightIn = new ArrayDeque<>();
        rightOut = new ArrayDeque<>();
        leftIn = new ArrayDeque<>();
        leftOut = new ArrayDeque<>();
    }

    public void start() throws IOException {
        System.out.println("Create or join?");
        String command = keyboard.nextLine();
        if (command.startsWith("c")) {
            createGame();
            gameLoop();
            stop();
        } else if (command.startsWith("j")) {
            joinGame();
            gameLoop();
            stop();
        } else {
            System.out.println("Unknown command.");
            stop();
        }
    }

    public void stop() throws IOException {
        serverSocket.close();
        rightSocket.close();
        leftSocket.close();
    }

    public void createGame() throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);

        System.out.println("Waiting for right.");
        rightSocket = serverSocket.accept();
        rightHandler = new SocketHandler(rightSocket, rightIn, rightOut);
        rightHandler.start();
        rightOut.add("waitForConnection");
        System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());

        System.out.println("Waiting for left.");
        leftSocket = serverSocket.accept();
        leftHandler = new SocketHandler(leftSocket, leftIn, leftOut);
        leftHandler.start();
        System.out.println("Left client connected from " + leftSocket.getInetAddress().getHostAddress());

        System.out.println("Telling left to connect to right.");
        leftOut.add("connectTo " + rightSocket.getInetAddress().getHostAddress());
        while(leftIn.isEmpty()) {}

        System.out.println("Left response: " + leftIn.poll());
    }

    public void joinGame() throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);

        System.out.print("Insert IP to connect to: ");
        leftSocket = new Socket(keyboard.nextLine(), PORT);
        leftHandler = new SocketHandler(leftSocket, leftIn, leftOut);
        leftHandler.start();
        System.out.println("Connected to left at " + leftSocket.getInetAddress().getHostAddress());
        while(leftIn.isEmpty()) {}

        String response = leftIn.poll();
        if (response.startsWith("connectTo")) {
            String rightIP = response.substring(10);
            System.out.println("Connecting to " + rightIP);
            rightSocket = new Socket(rightIP, PORT);
            rightHandler = new SocketHandler(rightSocket, rightIn, rightOut);
            rightHandler.start();
            System.out.println("Connected to right at " + leftSocket.getInetAddress().getHostAddress());
        } else if (response.equals("waitForConnection")) {
            System.out.println("Waiting for right.");
            rightSocket = serverSocket.accept();
            rightHandler = new SocketHandler(rightSocket, rightIn, rightOut);
            rightHandler.start();
            System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());
        } else {
            System.out.println("Response makes no sense.");
        }
    }

    private void gameLoop() {
        while (true) {
            System.out.println("Gaming time started. Exit with 'exit'.");
            String command = keyboard.nextLine();
            if (command.equals("exit")) {
                return;
            }

            if (command.startsWith("left")) {
                leftOut.add(command.substring(5));
            } else if (command.startsWith("right")) {
                rightOut.add(command.substring(5));
            } 
        }
    }
}
