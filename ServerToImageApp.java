import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class ServerToImageApp {
    private static final int PORT = 80;
    private static String lastMessage = ""; // Stores the latest message
    private static int image_count = 0;
    private static final String UPLOAD_DIR = "uploads";
    private static final String BOUNDARY = "*****"; // Boundary must match the Android app
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
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType != null && contentType.startsWith("multipart/form-data")) {
                    try (InputStream requestBody = exchange.getRequestBody();
                         ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                        // Read the request body into a byte array
                        byte[] data = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = requestBody.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, bytesRead);
                        }
                        buffer.flush();
                        byte[] requestBytes = buffer.toByteArray();

                        // Extract the image file from the multipart request
                        byte[] fileData = extractFileFromMultipart(requestBytes);

                        if (fileData != null) {
                            // Save the file to the upload directory
                            String fileName = "uploaded_image" + Integer.toString(image_count) +".jpg"; // You can parse the filename from the request
                            image_count += 1;
                            Path filePath = Paths.get(UPLOAD_DIR, fileName);
                            Files.write(filePath, fileData);

                            // Send a success response
                            String response = "File uploaded successfully: " + filePath.toString();
                            System.out.println(response);
                            exchange.sendResponseHeaders(200, response.length());
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(response.getBytes());
                            }
                        } else {
                            // Send an error response if no file was found
                            String response = "No file found in the request";
                            exchange.sendResponseHeaders(400, response.length());
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(response.getBytes());
                            }
                        }
                    }
                } else {
                    // Send an error response for unsupported content types
                    String response = "Unsupported content type. Expected multipart/form-data";
                    exchange.sendResponseHeaders(400, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } else {
                // Send an error response for unsupported HTTP methods
                String response = "Unsupported HTTP method. Expected POST";
                exchange.sendResponseHeaders(405, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private byte[] extractFileFromMultipart(byte[] requestBytes) {
            // Convert the request bytes to a string for easier parsing
            String requestString = new String(requestBytes);

            // Define the boundary markers
            String boundaryMarker = "--" + BOUNDARY;
            String fileStartMarker = "Content-Disposition: form-data; name=\"file\"; filename=\"";
            String fileEndMarker = boundaryMarker;

            // Find the start and end of the file content
            int fileStartIndex = requestString.indexOf(fileStartMarker);
            if (fileStartIndex == -1) {
                return null; // No file found
            }

            int fileEndIndex = requestString.indexOf(fileEndMarker, fileStartIndex);
            if (fileEndIndex == -1) {
                return null; // Invalid request
            }

            // Extract the file content
            int fileContentStart = requestString.indexOf("\r\n\r\n", fileStartIndex) + 4;
            int fileContentEnd = fileEndIndex - 2; // Exclude the boundary and trailing CRLF

            // Extract the file data as binary
            byte[] fileData = new byte[fileContentEnd - fileContentStart];
            System.arraycopy(requestBytes, fileContentStart, fileData, 0, fileData.length);

            return fileData;
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
