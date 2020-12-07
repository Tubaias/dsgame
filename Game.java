import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Game {
    // hardcoded variables
    private final int PORT = 25565;
    private final String DEFIP = "192.168.56.1";
    private final String LOGFILE = "dsgamelog.log";
    private final int SMALLBLIND = 25;
    private final int STARTINGCHIPS = 1000;
    private final int CALLAMOUNT = 50;

    // connectivity/IO variables
    private Scanner keyboard;
    private ServerSocket serverSocket;
    private Socket rightSocket;
    private Socket leftSocket;
    private SocketHandler rightHandler;
    private SocketHandler leftHandler;
    private Logger logger;

    // game logic related variables
    private ArrayList<String> playerList;
    private ArrayList<String> chipList;
    private ArrayList<String> statusList;
    private int pot;
    private int flop;
    private String ownName;
    private int chips;
    private int card1;
    private int card2;

    public Game(Scanner scanner) throws Exception {
        this.keyboard = scanner;

        FileHandler logHandler = new FileHandler(LOGFILE, true);
        this.logger = Logger.getLogger("gameLogger");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(logHandler);
        this.ownName = "tempname";
        this.chips = STARTINGCHIPS;
        this.playerList = new ArrayList<>();
        this.chipList = new ArrayList<>();
        this.statusList = new ArrayList<>();
    }

    // Initial method called by the main class. 
    // User inserts name and chooses to create or join a game.
    public void start() throws Exception {
        System.out.print("Insert name: ");
        ownName = keyboard.nextLine();
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

    // Cleanup method. Called when the game is over.
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

    // Creates a new game session by waiting for the set amount of players and creating a ring topology of TCP connections. 
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

    // Joins an already created game session that is waiting for players.
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

    // Main loop of the game that is run after the ring of connections is complete.
    private void gameLoop() throws Exception {
        System.out.println("Gaming time started. Exit with 'exit'.");
        while (true) {
            // handle inputs from the neighboring network nodes
            while(!leftHandler.in.isEmpty()) {
                String msg = leftHandler.in.poll();
                handleMessage(msg, "LEFT");
            }

            while(!rightHandler.in.isEmpty()) {
                String msg = rightHandler.in.poll();
                handleMessage(msg, "RIGHT");
            }

            // handle inputs from the local keyboard
            if (System.in.available() > 0) {
                String command = keyboard.nextLine();
                if (command.equals("exit")) { return; }
                handleCommand(command);
            }
        }
    }

    // Player becomes the dealer for a round and handles logic related to game round progression.
    private void dealRound() throws Exception {
        playerList.clear();
        chipList.clear();
        statusList.clear();
        Random rng = new Random();

        makePlayerList("");
        System.out.println(ownName + " is the new dealer.");
        broadcast(ownName + " is the new dealer.");

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

    // Deal 'cards' to self and other players.
    private void dealCards(Random rng) {
        card1 = rng.nextInt(13);
        card2 = rng.nextInt(13);
        System.out.println("Own cards: " + card1 + ", " + card2);
        for (int i = 1; i < playerList.size(); i++) {
            leftHandler.out.add("FWD," + playerList.get(i) + ",CARDS," + rng.nextInt(13) + "," + rng.nextInt(13));
        }
    }

    // Ask self and other players to either CALL or FOLD on the current round.
    private void bettingRound() throws Exception {
        // Own choice
        System.out.println(ownName + "'s turn.");
        broadcast(ownName + "'s turn.");
        System.out.println("Call or Fold?");
        String command = keyboard.nextLine();
        if (command.startsWith("c")) {
            System.out.println("Chose to call.");
            chips -= CALLAMOUNT;
            pot += CALLAMOUNT;
            statusList.add(0, "CALL");
            broadcast(ownName + " calls.");
        } else {
            System.out.println("Chose to fold.");
            statusList.add(0, "FOLD");
            broadcast(ownName + " folds.");
        }

        // Other players
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

    // Method for choosing CALL or FOLD when not the dealer.
    private void makeBet() {
        System.out.println("Call or Fold?");
        String command = keyboard.nextLine();
        if (command.startsWith("c")) {
            System.out.println("Chose to call.");
            chips -= CALLAMOUNT;
            leftHandler.out.add("BET," + ownName + ",CALL");
        } else {
            System.out.println("Chose to fold.");
            leftHandler.out.add("BET," + ownName + ",FOLD");
        }
    }

    // Prints and forwards the BET message if it was made by another player.
    private void handleBet(String msg) {
        if (!msg.contains(ownName)) {
            String[] parts = msg.split(",");
            if (parts[2].equals("CALL")) {
                System.out.println(parts[1] + " calls.");
            } else {
                System.out.println(parts[1] + " folds.");
            }

            leftHandler.out.add(msg);
        }
    }

    // Compare hands of all players that called and declare the winner.
    private void showdown() throws Exception {
        int bestHand = 0;
        String winner = "nobody";
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
            }
        }

        System.out.println("WINNER: " + winner);
        broadcast("WINNER: " + winner);
        if (winner.equals(ownName)) {
            chips += pot;
        } else if (!winner.equals("nobody")) {
            leftHandler.out.add("FWD," + winner + ",ADDCHIPS," + pot);
        }
        
        makeChipList("");
    }

    // Simplistic hand value that just sums the numbers.
    private int parseHand(int c1, int c2) {
        return c1 + c2 + flop;
    }

    // Prints and forwards the HAND message if it was made by another player.
    private void handleHand(String msg) {
        if (!msg.contains(ownName)) {
            String[] parts = msg.split(",");
            System.out.println(parts[1] + "'s hand: " + parts[2] +", " + parts[3]);
            leftHandler.out.add(msg);
        }
    }

    // Broadcasts a message to all other players in the session.
    private void broadcast(String message) {
        for (String playerName : playerList) {
            if (!playerName.equals(ownName)) {
                leftHandler.out.add("FWD," + playerName + "," + message);
            }
        }
    }

    // Decides what to do with a message received from the network
    private void handleMessage(String msg, String source) throws Exception {
        // simply forward messages if they contain the FWD tag and are not for us
        if (msg.startsWith("FWD")) {
            String[] parts = msg.split(",");

            if (!parts[1].equals(ownName)) {
                leftHandler.out.add(msg);
                return;
            } else {
                // if the message is for us, remove the assosiated FWD tag and recipient info
                msg = "";
                for (int i = 2; i < parts.length; i++) {
                    msg = msg.concat(parts[i] + ",");
                }

                msg = msg.substring(0, msg.length() - 1);
            }
        }

        // handle the BREAKANDAWAIT message that is used in forming the TCP connection circle
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
        
        // handle other messages. If unknown, simply print the message.
        if (msg.startsWith("PLAYERLIST")) {
            makePlayerList(msg);
        } else if (msg.startsWith("CHIPLIST")) {
            makeChipList(msg);
        } else if (msg.startsWith("BIGBLIND")) {
            bigBlind(msg.split(",")[1]);
        } else if (msg.startsWith("SMALLBLIND")) {
            smallBlind(msg.split(",")[1]);
        } else if (msg.startsWith("TELLCHIPS")) {
            broadcast(ownName + " has " + chips + " chips.");
        } else if (msg.startsWith("CARDS")) {
            saveCards(msg);
        } else if (msg.startsWith("MAKEBET")) {
            makeBet();
        } else if (msg.startsWith("BET")) {
            handleBet(msg);
        } else if (msg.startsWith("GETHAND")) {
            leftHandler.out.add("HAND," + ownName + "," + card1 + "," + card2);
        } else if (msg.startsWith("HAND")) {
            handleHand(msg);
        } else if (msg.startsWith("ADDCHIPS")) {
            chips += Integer.parseInt(msg.split(",")[1]);
        } else {
            System.out.println(msg);
        }
    }

    // Handles commands received from the local keyboard.
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

    // Saves values to own cards based on input message.
    private void saveCards(String input) {
        String[] parts = input.split(",");
        card1 = Integer.parseInt(parts[1]);
        card2 = Integer.parseInt(parts[2]);
        System.out.println("Own cards: " + card1 + ", " + card2);
    }

    // Discovers all the players in the session and makes every node save and print the list.
    // Only blocks the node that initiated the discovery (This should be the dealer of a round).
    private void makePlayerList(String input) throws Exception {
        boolean block = false;
        while (true) {
            if (input.isEmpty()) {
                block = true;
                playerList.clear();
                playerList.add(null);
                leftHandler.out.add("PLAYERLIST," + ownName);
            } else if (input.startsWith("PLAYERLIST")) {
                if (!input.contains(ownName)) {
                    playerList.clear();
                    playerList.add(null);
                    leftHandler.out.add(input + "," + ownName);
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

    // Discovers the chip amounts of all players in the session and makes every node save and print the list.
    // Only blocks the node that initiated the discovery (This should be the dealer of a round).
    private void makeChipList(String input) throws Exception {
        boolean block = false;
        while (true) {
            if (input.isEmpty()) {
                block = true;
                chipList.clear();
                chipList.add(null);
                leftHandler.out.add("CHIPLIST," + ownName + "|" + chips);
            } else if (input.startsWith("CHIPLIST")) {
                if (!input.contains(ownName)) {
                    chipList.clear();
                    chipList.add(null);
                    leftHandler.out.add(input + "," + ownName + "|" + chips);
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

    // Deducts the given amount from own chips and calls BIGBLIND to left neighbor.
    private void smallBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
        leftHandler.out.add("BIGBLIND," + integeramount * 2);
    }

    // Deducts the given amount from own chips.
    private void bigBlind(String amount) {
        int integeramount = Integer.parseInt(amount);
        this.chips -= integeramount;
    }
}
