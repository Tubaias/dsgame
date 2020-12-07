import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

// Class for asynchronously listening to a web socket.
// Two ConcurrentLinkedDeques are used to exchange information with the game's main thread.
public class SocketHandler extends Thread {
    private Socket socket;
    private PrintWriter outWriter;
    private BufferedReader inReader;
    public ConcurrentLinkedDeque<String> in;
    public ConcurrentLinkedDeque<String> out;
    private Logger logger;

    private boolean running;

    public SocketHandler(Socket socket, Logger logger) {
        this.socket = socket;
        this.logger = logger;

        this.in = new ConcurrentLinkedDeque<>();
        this.out = new ConcurrentLinkedDeque<>();
        this.running = true;
    }

    // Gets input from and sends output to the given web socket.
    // Also logs every input/output/error with the given Logger object.
    public void run() {
        try {
            outWriter = new PrintWriter(socket.getOutputStream(), true);
            inReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            logger.info("CONNECTED TO: " + socket.getInetAddress());

            String input;
            while (running) {
                if (inReader.ready()) {
                    input = inReader.readLine();
                    if (input == null || input.equals("QUIT")) { break; }
                    logger.info("FROM: " + socket.getInetAddress() + " : " + input);
                    in.add(input);
                }

                while (!out.isEmpty()) {
                    String output = out.poll();
                    if (output.equals("killThread")) {
                        running = false;
                    } else {
                        logger.info("TO: " + socket.getInetAddress() + " : " + output);
                        outWriter.println(output);
                    }
                }
            }
    
            logger.info("DISCONNECTED FROM: " + socket.getInetAddress());

            inReader.close();
            outWriter.close();
            socket.close();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return;
        }
    }
}