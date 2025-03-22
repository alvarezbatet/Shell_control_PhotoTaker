import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class HttpImageServer {
    private static final int PORT = 80;
    private static final String SAVE_DIR = "uploads/";

    public static void main(String[] args) {
        File directory = new File(SAVE_DIR);
        if (!directory.exists()) directory.mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ImageHandler(socket)).start();
                
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ImageHandler implements Runnable {
    private Socket socket;

    public ImageHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (InputStream inputStream = socket.getInputStream();
             DataInputStream dis = new DataInputStream(inputStream)) {

            String fileName = "image_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
            File file = new File("uploads/" + fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("Saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}