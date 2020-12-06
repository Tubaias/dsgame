import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.logging.Logger;

public class SocketHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile ArrayDeque<String> inQueue;
    private volatile ArrayDeque<String> outQueue;
    private Logger logger;

    private boolean running;

    public SocketHandler(Socket socket, ArrayDeque<String> inQueue, ArrayDeque<String> outQueue, Logger logger) {
        this.socket = socket;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
        this.logger = logger;
        this.running = true;
    }

    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            logger.info("CONNECTED TO: " + socket.getInetAddress());

            String input;
            while (running) {
                if (in.ready()) {
                    input = in.readLine();

                    if (input == null || input.equals("quit")) {
                        break;
                    }

                    logger.info("FROM: " + socket.getInetAddress() + " : " + input);
                    inQueue.add(input);
                }

                while (!outQueue.isEmpty()) {
                    String output = outQueue.poll();
                    if (output.equals("killThread")) {
                        running = false;
                    } else {
                        logger.info("TO: " + socket.getInetAddress() + " : " + output);
                        out.println(output);
                    }
                }
            }
    
            logger.info("DISCONNECTED FROM: " + socket.getInetAddress());

            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return;
        }
    }
}