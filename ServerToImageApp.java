import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class ServerToImageApp {
    private static final int PORT = 80;
    private static String lastMessage = ""; // Stores the latest message
    private static int image_count = 0;
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/send", new ImageUploadHandler());
        server.createContext("/receive", new ReceiveHandler());
        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())); // Enable thread pool
        server.start();

        System.out.println("Server started on port " + PORT);

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String input;
                while (true) {
                    System.out.print("Enter message to send (type 'exit' to stop): ");
                    input = reader.readLine();
                    if ("exit".equalsIgnoreCase(input)) {
                        System.out.println("Server shutting down...");
                        System.exit(0);
                    }
                    if (input != null && !input.trim().isEmpty()) {
                        lastMessage = input;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    static class ImageUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("ImageUploadHandler running");
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String fileName = "take-photo";
                fileName += Integer.toString(image_count) + ".jpg";
                File file = new File(fileName);
                image_count += 1;
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.close();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                System.out.println("Image uploaded!");
            }
        }
    }

    static class ReceiveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = lastMessage.isEmpty() ? "No new messages" : lastMessage;
            lastMessage = ""; // Clear the message after sending

            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
