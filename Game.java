import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Game {
    private Scanner keyboard;
    private final int PORT = 25565;
    private final String DEFIP = "192.168.56.1";
    private final String LOGFILE = "dsgamelog.log";
    private final int SMALLBLIND = 25;
    private final int STARTINGCHIPS = 1000;
    private final int CALLAMOUNT = 50;

    private ServerSocket serverSocket;
    private Socket rightSocket;
    private Socket leftSocket;
    private SocketHandler rightHandler;
    private SocketHandler leftHandler;
    private Logger logger;

    private ArrayList<String> playerList;
    private ArrayList<String> chipList;
    private ArrayList<String> statusList;
    private int pot;
    private int flop;
    private String name;
    private int chips;
    private int card1;
    private int card2;

    public Game(Scanner scanner) throws Exception {
        this.keyboard = scanner;

        FileHandler logHandler = new FileHandler(LOGFILE, true);
        this.logger = Logger.getLogger("gameLogger");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(logHandler);
        this.name = "tempname";
        this.chips = STARTINGCHIPS;
        this.playerList = new ArrayList<>();
        this.chipList = new ArrayList<>();
        this.statusList = new ArrayList<>();
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
        playerList.clear();
        chipList.clear();
        statusList.clear();
        Random rng = new Random();

        System.out.println(name + " is the new dealer.");
        broadcast(name + " is the new dealer.");

        makePlayerList("");
        leftHandler.out.add("SMALLBLIND," + SMALLBLIND);
        makeChipList("");
        pot = SMALLBLIND + (SMALLBLIND * 2);
        System.out.println("POT: " + pot);
        broadcast("POT: " + pot);

        dealCards(rng);
        bettingRound();

        System.out.println("POT: " + pot);
        broadcast("POT: " + pot);

        flop = rng.nextInt(13);
        System.out.println("FLOP: " + flop);
        broadcast("FLOP: " + flop);

        showdown();
    }

    private void dealCards(Random rng) {
        card1 = rng.nextInt(13);
        card2 = rng.nextInt(13);
        System.out.println("Own cards: " + card1 + ", " + card2);
        for (int i = 1; i < playerList.size(); i++) {
            leftHandler.out.add("FWD," + playerList.get(i) + ",CARDS," + rng.nextInt(13) + "," + rng.nextInt(13));
        }
    }

    private void bettingRound() throws Exception {
        System.out.println(name + "'s turn.");
        broadcast(name + "'s turn.");
        System.out.println("Call or Fold?");
        String command = keyboard.nextLine();
        if (command.startsWith("c")) {
            System.out.println("Chose to call.");
            chips -= CALLAMOUNT;
            pot += CALLAMOUNT;
            statusList.add(0, "CALL");
            broadcast(name + " calls.");
        } else {
            System.out.println("Chose to fold.");
            statusList.add(0, "FOLD");
            broadcast(name + " folds.");
        }

        for (int i = 1; i < playerList.size(); i++) {
            String playerName = playerList.get(i);
            System.out.println(playerName + "'s turn.");
            broadcast(playerName + "'s turn.");
            leftHandler.out.add("FWD," + playerName + ",MAKEBET");

            while (true) {
                while (rightHandler.in.isEmpty()) { Thread.sleep(100); }

                String message = rightHandler.in.poll();
                if (message.startsWith("BET") && message.contains(playerName)) {
                    String action = message.split(",")[2];
                    statusList.add(i, action);
                    if (action.equals("CALL")) { pot += CALLAMOUNT; }
                    handleMessage(message, "RIGHT");
                    break;
                } else {
                    handleMessage(message, "RIGHT");
                }
            }
        }
    }

    private void makeBet() {
        System.out.println("Call or Fold?");
        String command = keyboard.nextLine();
        if (command.startsWith("c")) {
            System.out.println("Chose to call.");
            chips -= CALLAMOUNT;
            leftHandler.out.add("BET," + name + ",CALL");
        } else {
            System.out.println("Chose to fold.");
            leftHandler.out.add("BET," + name + ",FOLD");
        }
    }

    private void handleBet(String msg) {
        if (!msg.contains(name)) {
            String[] parts = msg.split(",");
            if (parts[2].equals("CALL")) {
                System.out.println(parts[1] + " calls.");
            } else {
                System.out.println(parts[1] + " folds.");
            }

            leftHandler.out.add(msg);
        }
    }

    private void showdown() throws Exception {
        int bestHand = 0;
        String winner = null;
        for (int i = 0; i < playerList.size(); i++) {
            if (statusList.get(i).equals("CALL")) {
                String playerName = playerList.get(i);

                leftHandler.out.add("FWD," + playerName + ",GETHAND");

                while (true) {
                    while (rightHandler.in.isEmpty()) { Thread.sleep(100); }
    
                    String message = rightHandler.in.poll();
                    if (message.startsWith("HAND")) {
                        String[] parts = message.split(",");
                        int playersCard1 = Integer.parseInt(parts[2]);
                        int playersCard2 = Integer.parseInt(parts[3]);

                        int handValue = parseHand(playersCard1, playersCard2);
                        if (handValue > bestHand) {
                            bestHand = handValue;
                            winner = playerName;
                        } 

                        handleMessage(message, "RIGHT");
                        break;
                    } else {
                        handleMessage(message, "RIGHT");
                    }
                }

                System.out.println("WINNER: " + winner);
                broadcast("WINNER: " + winner);
                leftHandler.out.add("FWD," + playerName + ",ADDCHIPS," + pot);
                makeChipList("");
            }
        }
    }

    private int parseHand(int c1, int c2) {
        return c1 + c2 + flop;
    }

    private void handleHand(String msg) {
        if (!msg.contains(name)) {
            String[] parts = msg.split(",");
            System.out.println(parts[1] + "'s hand: " + parts[2] +", " + parts[3]);
            leftHandler.out.add(msg);
        }
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
                    msg = msg.concat(parts[i] + ",");
                }

                msg = msg.substring(0, msg.length() - 1);
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
        } else if (msg.startsWith("CHIPLIST")) {
            makeChipList(msg);
        } else if (msg.startsWith("BIGBLIND")) {
            bigBlind(msg.split(",")[1]);
        } else if (msg.startsWith("SMALLBLIND")) {
            smallBlind(msg.split(",")[1]);
        } else if (msg.startsWith("TELLCHIPS")) {
            broadcast(name + " has " + chips + " chips.");
        } else if (msg.startsWith("CARDS")) {
            saveCards(msg);
        } else if (msg.startsWith("MAKEBET")) {
            makeBet();
        } else if (msg.startsWith("BET")) {
            handleBet(msg);
        } else if (msg.startsWith("GETHAND")) {
            leftHandler.out.add("HAND," + name + "," + card1 + "," + card2);
        } else if (msg.startsWith("HAND")) {
            handleHand(msg);
        } else {
            System.out.println(msg);
        }
    }

    private void handleCommand(String command) throws Exception {
        if (command.equals("deal")) {
            dealRound();
        }
        
        if (command.startsWith("left")) {
            leftHandler.out.add(command.substring(5));
        } else if (command.startsWith("right")) {
            rightHandler.out.add(command.substring(6));
        }
    }

    private void saveCards(String input) {
        String[] parts = input.split(",");
        card1 = Integer.parseInt(parts[1]);
        card2 = Integer.parseInt(parts[2]);
        System.out.println("Own cards: " + card1 + ", " + card2);
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

    private void makeChipList(String input) throws Exception {
        boolean block = false;
        while (true) {
            if (input.isEmpty()) {
                block = true;
                chipList.clear();
                chipList.add(null);
                leftHandler.out.add("CHIPLIST," + name + "|" + chips);
            } else if (input.startsWith("CHIPLIST")) {
                if (!input.contains(name)) {
                    chipList.clear();
                    chipList.add(null);
                    leftHandler.out.add(input + "," + name + "|" + chips);
                } else if (chipList.get(0) == null) {
                    leftHandler.out.add(input);
                    chipList.clear();
        
                    for (String str : input.split(",")) {
                        if (!str.equals("CHIPLIST")) {
                            chipList.add(str);
                        }
                    }
        
                    System.out.println("Chips: ");
                    for (int i = 0; i < chipList.size(); i++) {
                        System.out.println(chipList.get(i) + " chips");
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

    private void smallBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
        leftHandler.out.add("BIGBLIND," + integeramount * 2);
    }

    private void bigBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
    }
}
