import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;

public class FileUploadServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 80), 0);
        server.createContext("/upload", new ImageUploadHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 80...");
    }

    static class ImageUploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("ImageUploadHandler running");
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                File file = new File("uploaded_image.jpg");
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
}