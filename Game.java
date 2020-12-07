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
    private final int BIGBLIND = 50;
    private final int STARTINGCHIPS = 1000;

    private ServerSocket serverSocket;
    private Socket rightSocket;
    private Socket leftSocket;
    private SocketHandler rightHandler;
    private SocketHandler leftHandler;
    private Logger logger;

    private String name;
    private int chips;
    private ArrayList<String> playerList;
    private int pot;

    public Game(Scanner scanner) throws Exception {
        this.keyboard = scanner;

        FileHandler logHandler = new FileHandler(LOGFILE, true);
        this.logger = Logger.getLogger("gameLogger");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(logHandler);
        this.name = "tempname";
        this.chips = STARTINGCHIPS;
        this.playerList = new ArrayList<>();
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

        System.out.println("How many players (including self)?");
        int playercount = Integer.parseInt(keyboard.nextLine());
        if (playercount < 3) { return; }

        System.out.println("Waiting for right.");
        rightSocket = serverSocket.accept();
        rightHandler = new SocketHandler(rightSocket, logger);
        rightHandler.start();
        rightHandler.out.add("WAITFORCONNECTION");
        System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());

        System.out.println("Waiting for left.");
        leftSocket = serverSocket.accept();
        leftHandler = new SocketHandler(leftSocket, logger);
        leftHandler.start();
        System.out.println("Left client connected from " + leftSocket.getInetAddress().getHostAddress());

        System.out.println("Telling left to connect to right.");
        leftHandler.out.add("CONNECTTO " + rightSocket.getInetAddress().getHostAddress());
        while(leftHandler.in.isEmpty()) {
            Thread.sleep(100);
        }

        System.out.println("Left response: " + leftHandler.in.poll());

        int connectedPlayers = 3;
        while (connectedPlayers < playercount) {
            System.out.println("Current playercount: " + connectedPlayers + "/" + playercount);
            Socket newPlayerSocket = serverSocket.accept();
            System.out.println("Got new connection.");
            String rightIP = rightSocket.getInetAddress().getHostAddress();
            rightHandler.out.add("BREAKANDAWAIT");
            rightHandler.out.add("killThread");

            rightSocket = newPlayerSocket;
            rightHandler = new SocketHandler(rightSocket, logger);
            rightHandler.start();
            System.out.println("Completing circle.");
            rightHandler.out.add("ALTCONNECTTO " + rightIP);

            while(rightHandler.in.isEmpty()) {
                Thread.sleep(100);
            }

            System.out.println("Response: " + rightHandler.in.poll());
            connectedPlayers++;
        }
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
        if (response.equals("WAITFORCONNECTION")) {
            System.out.println("Waiting for right.");
            rightSocket = serverSocket.accept();
            rightHandler = new SocketHandler(rightSocket, logger);
            rightHandler.start();
            System.out.println("Right client connected from " + rightSocket.getInetAddress().getHostAddress());
        } else if (response.startsWith("CONNECTTO")) {
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
        } else if (response.startsWith("ALTCONNECTTO")) {
            String rightIP = response.substring(13);
            System.out.println("Connecting to " + rightIP);
            rightSocket = new Socket(rightIP, PORT);
            rightHandler = new SocketHandler(rightSocket, logger);
            rightHandler.start();
            System.out.println("Connected to right at " + rightSocket.getInetAddress().getHostAddress());
            leftHandler.out.add("ok");
        } else {
            System.out.println("Response makes no sense.");
        }
    }

    private void gameLoop() throws Exception {
        System.out.println("Gaming time started. Exit with 'exit'.");
        while (true) {
            while(!leftHandler.in.isEmpty()) {
                String msg = leftHandler.in.poll();
                handleMessage(msg, "LEFT");
            }

            while(!rightHandler.in.isEmpty()) {
                String msg = rightHandler.in.poll();
                handleMessage(msg, "RIGHT");
            }

            if (System.in.available() > 0) {
                String command = keyboard.nextLine();
                if (command.equals("exit")) { return; }
                handleCommand(command);
            }
        }
    }

    private void dealRound() throws Exception {
        //Random rng = new Random();
        handleCommand("playerlist");
        leftHandler.out.add("BIGBLIND," + BIGBLIND);
        pot = BIGBLIND + (BIGBLIND / 2);
        broadcast("TELLCHIPS");
        System.out.println("POT: " + pot);
        broadcast("POT: " + pot);

        //String flop = "" + rng.nextInt(13);
        System.out.println("insert flop: ");
        String flop = keyboard.nextLine();
        System.out.println("FLOP: " + flop);
        broadcast("FLOP: " + flop);
    }

    private void broadcast(String message) {
        for (String playerName : playerList) {
            if (!playerName.equals(name)) {
                leftHandler.out.add("FWD," + playerName + "," + message);
            }
        }
    }

    private void handleMessage(String msg, String source) throws Exception {
        if (msg.startsWith("FWD")) {
            String[] parts = msg.split(",");

            if (!parts[1].equals(name)) {
                leftHandler.out.add(msg);
                return;
            } else {
                msg = "";
                for (int i = 2; i < parts.length; i++) {
                    msg = msg.concat(parts[i]);
                }
            }
        }

        if (msg.equals("BREAKANDAWAIT")) {
            if (source.equals("LEFT")) {
                leftHandler.out.add("killThread");
                leftSocket = serverSocket.accept();
                leftHandler = new SocketHandler(leftSocket, logger);
                leftHandler.start();
            } else {
                rightHandler.out.add("killThread");
                rightSocket = serverSocket.accept();
                rightHandler = new SocketHandler(rightSocket, logger);
                rightHandler.start();
            }
        }
        
        if (msg.startsWith("PLAYERLIST")) {
            makePlayerList(msg);
        } else if (msg.startsWith("BIGBLIND")) {
            bigBlind(msg.split(",")[1]);
        } else if (msg.startsWith("SMALLBLIND")) {
            smallBlind(msg.split(",")[1]);
        } else if (msg.startsWith("TELLCHIPS")) {
            broadcast(name + " has " + chips + " chips.");
        } else {
            System.out.println(msg);
        }
    }

    private void handleCommand(String command) throws Exception {
        if (command.equals("deal")) {
            dealRound();
        } else if (command.equals("playerlist")) {
            makePlayerList("");
        } 
        
        if (command.startsWith("left")) {
            leftHandler.out.add(command.substring(5));
        } else if (command.startsWith("right")) {
            rightHandler.out.add(command.substring(6));
        }
    }

    private void makePlayerList(String input) throws Exception {
        boolean block = false;
        while (true) {
            if (input.isEmpty()) {
                block = true;
                playerList.clear();
                playerList.add(null);
                leftHandler.out.add("PLAYERLIST," + name);
            } else if (input.startsWith("PLAYERLIST")) {
                if (!input.contains(name)) {
                    playerList.clear();
                    playerList.add(null);
                    leftHandler.out.add(input + "," + name);
                } else if (playerList.get(0) == null) {
                    leftHandler.out.add(input);
                    playerList.clear();
        
                    for (String str : input.split(",")) {
                        if (!str.equals("PLAYERLIST")) {
                            playerList.add(str);
                        }
                    }
        
                    System.out.println("Player list: ");
                    for (int i = 0; i < playerList.size(); i++) {
                        System.out.println(i + ": " + playerList.get(i));
                    }
                } else {
                    return;
                }
            } 

            if (block) { 
                while (rightHandler.in.isEmpty()) {
                    Thread.sleep(100);
                }

                input = rightHandler.in.poll();
            } else {
                break; 
            }
        }
    }

    private void bigBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
        leftHandler.out.add("SMALLBLIND," + integeramount / 2);
    }

    private void smallBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
    }
}
