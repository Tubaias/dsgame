import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Setting up");
        Scanner input = new Scanner(System.in);
        Game game = new Game(input);
        game.start();
        input.close();
    }
}