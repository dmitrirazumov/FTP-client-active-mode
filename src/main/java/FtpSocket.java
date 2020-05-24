import java.net.*;
import java.io.*;

public class FtpSocket {
    protected static final int BUFSIZE = 0x8000;
    private Socket socket;
    private byte buffer[] = null;
    private byte buf[];
    private volatile int len;
    private int count;
    private InetAddress ipAddress = null;
    private final String address;
    private final int port;
    private OutputStream os;
    private DataOutputStream dos;
    private InputStream is;
    private DataInputStream dis;
    private static int TIMEOUT = 10000;
    public FtpSocket(String address, int port) {
        this.address = address;
        this.port = port;
    }
    public FtpSocket(Socket socket) {
        this.address = null;
        this.port = 0;
        this.socket = socket;
        try {
            buffer = new byte[BUFSIZE];
            os = socket.getOutputStream();
            dos = new DataOutputStream(os);
            is = socket.getInputStream();
            dis = new DataInputStream(is);
        } catch (IOException e) {
            onError(e.getMessage());
        }
    }

    // получить внешний IP адрес
    public String getLANIP() {
        return socket.isConnected() ? socket.getLocalAddress().getHostAddress() : null;
    }

    public boolean connect() {
        try {
            ipAddress = InetAddress.getByName(address);
            socket = new Socket(ipAddress, port);
            if (buffer == null) buffer = new byte[BUFSIZE];
            os = socket.getOutputStream();
            dos = new DataOutputStream(os);
            is = socket.getInputStream();
            dis = new DataInputStream(is);
            return true;
        } catch (Exception e) {
            onError(e.getMessage());
        }
        return false;
    }

    //закрытие сокетов команд/данных
    public void disconnect() {
        try {
            if (!socket.isClosed()) {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            }
        } catch (IOException ex) {
        }
    }

    //проверка на то, закрыт ли сокет
    public boolean isConnected() {
        try {
            return !socket.isClosed();
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean send(byte buf[], int count) {
        if (count < 0) return false;
        try {
            dos.write(buf, 0, count);
            return true;
        } catch (Exception e) {
            onError(e.getMessage());
        }
        return false;
    }

    //посылаем команду на сервер
    public boolean send(String s) {
        try {
            dos.write(s.getBytes());
            return true;
        } catch (Exception e) {
            onError(e.getMessage());
        }
        return false;
    }

    public int recv(byte buf[], int count) {
        if (count < 0) return -1;
        this.count = count;
        len = 0;
        this.buf = buf;
        long ms = System.currentTimeMillis();
        new Thread(new MyThread()).start();
        while (len == 0 && System.currentTimeMillis() - ms < TIMEOUT) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        if (len <= 0) {
            disconnect();
            return -1;
        }
        return len;
    }

    //чтение из сокета
    public String recv() {
        this.count = BUFSIZE;
        len = 0;
        this.buf = buffer;
        long ms = System.currentTimeMillis();
        new Thread(new MyThread()).start();
        while (len == 0 && System.currentTimeMillis() - ms < TIMEOUT) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        if (len <= 0) {
            disconnect();
            return null;
        }
        return new String(buffer, 0, len);
    }

    public final void onError(String error) {
        System.out.println(error);
    }

    private class MyThread implements Runnable {
        @Override
        public void run() {
            try {
                len = dis.read(buf, 0, count);
            } catch (Exception e) {
                onError(e.getMessage());
                len = -1;
            }
        }
    }
}