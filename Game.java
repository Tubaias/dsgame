import java.net.*;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Game {
    private Scanner keyboard;
    private final int PORT = 25565;
    private final String DEFIP = "192.168.56.1";
    private final String LOGFILE = "log.log";

    private ServerSocket serverSocket;
    private Socket rightSocket;
    private Socket leftSocket;
    private SocketHandler rightHandler;
    private SocketHandler leftHandler;
    private ConcurrentLinkedDeque<String> rightIn;
    private ConcurrentLinkedDeque<String> rightOut;
    private ConcurrentLinkedDeque<String> leftIn;
    private ConcurrentLinkedDeque<String> leftOut;
    private Logger logger;

    public Game(Scanner input) throws Exception {
        this.keyboard = input;
        rightIn = new ConcurrentLinkedDeque<>();
        rightOut = new ConcurrentLinkedDeque<>();
        leftIn = new ConcurrentLinkedDeque<>();
        leftOut = new ConcurrentLinkedDeque<>();

        FileHandler logHandler = new FileHandler(LOGFILE, true);
        logger = Logger.getLogger("gameLogger");
        logger.setUseParentHandlers(false);
        logger.addHandler(logHandler);
    }

    public void start() throws Exception {
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
        leftOut.add("killThread");
        rightOut.add("killThread");

        serverSocket.close();
        rightSocket.close();
        leftSocket.close();

        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
            h.close();
        }
    }

    public void createGame() throws Exception {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);

        System.out.println("Waiting for right.");
        rightSocket = serverSocket.accept();
        rightHandler = new SocketHandler(rightSocket, rightIn, rightOut, logger);
        rightHandler.start();
        rightOut.add("waitForConnection");
        System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());

        System.out.println("Waiting for left.");
        leftSocket = serverSocket.accept();
        leftHandler = new SocketHandler(leftSocket, leftIn, leftOut, logger);
        leftHandler.start();
        System.out.println("Left client connected from " + leftSocket.getInetAddress().getHostAddress());

        System.out.println("Telling left to connect to right.");
        leftOut.add("connectTo " + rightSocket.getInetAddress().getHostAddress());
        while(leftIn.isEmpty()) {
            Thread.sleep(100);
        }

        System.out.println("Left response: " + leftIn.poll());
    }

    public void joinGame() throws Exception {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);

        System.out.print("Insert IP to connect to: ");
        String ip = keyboard.nextLine();
        ip = ip.isEmpty() ? DEFIP : ip; // default IP to speed up testing during development

        leftSocket = new Socket(ip, PORT);
        leftHandler = new SocketHandler(leftSocket, leftIn, leftOut, logger);
        leftHandler.start();
        System.out.println("Connected at " + leftSocket.getInetAddress().getHostAddress());
        while(leftIn.isEmpty()) {
            Thread.sleep(100);
        }

        String response = leftIn.poll();
        if (response.equals("waitForConnection")) {
            System.out.println("Waiting for right.");
            rightSocket = serverSocket.accept();
            rightHandler = new SocketHandler(rightSocket, rightIn, rightOut, logger);
            rightHandler.start();
            System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());
        } else if (response.startsWith("connectTo")) {
            // swapping pointers for consistency
            rightSocket = leftSocket;
            rightHandler = leftHandler;
            rightIn = leftIn;
            rightOut = leftOut;
            leftIn = new ConcurrentLinkedDeque<>();
            leftOut = new ConcurrentLinkedDeque<>();

            String leftIP = response.substring(10);
            System.out.println("Connecting to " + leftIP);
            leftSocket = new Socket(leftIP, PORT);
            leftHandler = new SocketHandler(leftSocket, leftIn, leftOut, logger);
            leftHandler.start();
            System.out.println("Connected to left at " + leftSocket.getInetAddress().getHostAddress());
            rightOut.add("ok");
        } else {
            System.out.println("Response makes no sense.");
        }
    }

    private void gameLoop() throws Exception {
        while (true) {
            System.out.println("Gaming time started. Exit with 'exit'.");

            while(!leftIn.isEmpty()) {
                System.out.println("LEFT: " + leftIn.poll());
            }

            while(!rightIn.isEmpty()) {
                System.out.println("RIGHT: " + rightIn.poll());
            }

            String command = keyboard.nextLine();
            if (command.equals("exit")) {
                return;
            }

            if (command.startsWith("left")) {
                leftOut.add(command.substring(5));
            } else if (command.startsWith("right")) {
                rightOut.add(command.substring(6));
            }
        }
    }
}
