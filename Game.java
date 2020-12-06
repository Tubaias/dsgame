import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;
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
    private Logger logger;

    private String name;
    private ArrayList<String> playerList;

    public Game(Scanner scanner) throws Exception {
        this.keyboard = scanner;

        FileHandler logHandler = new FileHandler(LOGFILE, true);
        this.logger = Logger.getLogger("gameLogger");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(logHandler);
        this.name = "tempname";
        playerList = new ArrayList<>();
    }

    public void start() throws Exception {
        System.out.print("Insert name: ");
        name = keyboard.nextLine();
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

    public void stop() throws Exception {
        leftHandler.out.add("killThread");
        rightHandler.out.add("killThread");
        Thread.sleep(300);

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
        rightHandler = new SocketHandler(rightSocket, logger);
        rightHandler.start();
        rightHandler.out.add("waitForConnection");
        System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());

        System.out.println("Waiting for left.");
        leftSocket = serverSocket.accept();
        leftHandler = new SocketHandler(leftSocket, logger);
        leftHandler.start();
        System.out.println("Left client connected from " + leftSocket.getInetAddress().getHostAddress());

        System.out.println("Telling left to connect to right.");
        leftHandler.out.add("connectTo " + rightSocket.getInetAddress().getHostAddress());
        while(leftHandler.in.isEmpty()) {
            Thread.sleep(100);
        }

        System.out.println("Left response: " + leftHandler.in.poll());
    }

    public void joinGame() throws Exception {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server socket hosted on " + InetAddress.getLocalHost() + ", port " + PORT);

        System.out.print("Insert IP to connect to: ");
        String ip = keyboard.nextLine();
        ip = ip.isEmpty() ? DEFIP : ip; // default IP to speed up testing during development

        leftSocket = new Socket(ip, PORT);
        leftHandler = new SocketHandler(leftSocket, logger);
        leftHandler.start();
        System.out.println("Connected at " + leftSocket.getInetAddress().getHostAddress());
        while(leftHandler.in.isEmpty()) {
            Thread.sleep(100);
        }

        String response = leftHandler.in.poll();
        if (response.equals("waitForConnection")) {
            System.out.println("Waiting for right.");
            rightSocket = serverSocket.accept();
            rightHandler = new SocketHandler(rightSocket, logger);
            rightHandler.start();
            System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());
        } else if (response.startsWith("connectTo")) {
            // swapping pointers for consistency
            rightSocket = leftSocket;
            rightHandler = leftHandler;

            String leftIP = response.substring(10);
            System.out.println("Connecting to " + leftIP);
            leftSocket = new Socket(leftIP, PORT);
            leftHandler = new SocketHandler(leftSocket, logger);
            leftHandler.start();
            System.out.println("Connected to left at " + leftSocket.getInetAddress().getHostAddress());
            rightHandler.out.add("ok");
        } else {
            System.out.println("Response makes no sense.");
        }
    }

    private void gameLoop() throws Exception {
        System.out.println("Gaming time started. Exit with 'exit'.");
        while (true) {
            while(!leftHandler.in.isEmpty()) {
                String input = leftHandler.in.poll();
                handleInput(input);
                System.out.println("LEFT: " + input);
            }

            while(!rightHandler.in.isEmpty()) {
                String input = rightHandler.in.poll();
                handleInput(input);
                System.out.println("RIGHT: " + input);
            }

            if (System.in.available() > 0) {
                String command = keyboard.nextLine();
                if (command.equals("exit")) {
                    return;
                }

                if (command.equals("playerlist")) {
                    makePlayerList("");
                }

                if (command.startsWith("left")) {
                    leftHandler.out.add(command.substring(5));
                } else if (command.startsWith("right")) {
                    rightHandler.out.add(command.substring(6));
                }
            }
        }
    }

    private void handleInput(String input) {
        if (input.startsWith("playerList")) {
            makePlayerList(input);
        }
    }

    private void makePlayerList(String input) {
        if (input.isEmpty()) {
            playerList.clear();
            playerList.add("firstroundindicator");
            leftHandler.out.add("playerList," + name);
        } else if (!input.contains(name)) {
            playerList.clear();
            playerList.add("firstroundindicator");
            leftHandler.out.add(input + "," + name);
        } else {
            if (!playerList.isEmpty()) {
                leftHandler.out.add(input);
                playerList.clear();
            } else {
                for (String str : input.split(",")) {
                    if (!str.equals("playerList")) {
                        playerList.add(str);
                    }
                }

                System.out.println("Player list: ");
                for (int i = 0; i < playerList.size(); i++) {
                    System.out.println(i + ": " + playerList.get(i));
                }
            }
        }
    }
}
