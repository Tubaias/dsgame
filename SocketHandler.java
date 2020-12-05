import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayDeque;

public class SocketHandler extends Thread {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ArrayDeque<String> inQueue;
    private ArrayDeque<String> outQueue;

    public SocketHandler(Socket socket, ArrayDeque<String> inQueue, ArrayDeque<String> outQueue) {
        this.socket = socket;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println("Connected: " + socket.getInetAddress());

            String input;
            while (true) {
                if (in.ready()) {
                    input = in.readLine();

                    if (input == null || input.equals("quit")) {
                        break;
                    }

                    inQueue.add(input);
                    System.out.println(socket.getInetAddress() + ": " + input);
                }

                while (!outQueue.isEmpty()) {
                    out.println(outQueue.poll());
                }
            }
    
            System.out.println("Disconnected: " + socket.getInetAddress());

            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
    }
}