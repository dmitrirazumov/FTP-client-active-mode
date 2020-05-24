import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;

public class FtpClient {
    private static final int BUFSIZE = 0x8000;
    private static final String[] LIST = {"LIST", "NLST"};
    private static final String NUMERIC_ERROR = "Неверный ответ сервера";

    private final FtpSocket socketCmd;  // сокет команд
    private FtpSocket socketData;       // сокет данных
    private final byte[] data;          // буфер приема/передачи данных
    private String answer;              // для приема ответа
    private final String user;          // имя пользователя
    private final String password;      // пароль
    private boolean activeMode;         // активный режим?
    // используются в активном режиме
    private ServerSocket serverSocket;  // сервер
    private int localPort;              // порт
    private final boolean randomPort;
    // address - адрес FTP сервера, user - имя пользователя, 
    // password - пароль, port - FTP порт, 
    // если работаем в активном режиме, 
    // localPort - порт локального сервера
    // < 0 - случайный порт
    // иначе localPort = 0
    public FtpClient(String address, String user, String password,
                     int port, int localPort) {
        if (user == null || password == null) {
            this.user = "anonymous";
            this.password = "guest";
        } else {
            this.user = user;
            this.password = password;
        }
        data = new byte[BUFSIZE];
        socketCmd = new FtpSocket(address, port == 0 ? 21 : port);
        socketData = null;
        serverSocket = null;
        activeMode = false;
        this.localPort = localPort;
        randomPort = localPort < 0;
    }

    // читаем ответ сервера, парсим и возвращаем код ответа
    private int readAnswerCode() {
        // читаем из сокета команд
        answer = socketCmd.recv();
        if (answer == null) return -1;
        // печать ответа в стандартный вывод
        System.out.print("<< " + answer);
        String[] ss = answer.split("\n");
        for (String s : ss) {
            if (s.length() < 4 || s.charAt(3) != ' ') continue;
            try {
                int code = Integer.parseInt(answer.substring(0, 3));
                return code;
            } catch (NumberFormatException e) {
                System.out.println(NUMERIC_ERROR);
                return -1;
            }
        }
        return -1;
    }

    // ищем следующий код ответа не равный oldcode в полученной прежде посылке,
    // если не найден читаем ответ сервера, парсим и возвращаем код ответа
    private int readAnswerCode(int oldcode) {
        // первые 3 символа ответа до пробела - это код ответа
        // печать ответа в стандартный вывод
        String[] ss = answer.split("\n");
        for (String s : ss) {
            if (s.length() < 4 || s.charAt(3) != ' ') continue; // следующая строка
            try {
                int code = Integer.parseInt(s.substring(0, 3));
                if (oldcode != code) // пропускаем старый код
                    return code;
            } catch (NumberFormatException e) {
                System.out.println(NUMERIC_ERROR);
                return -1;
            }
        }
        return readAnswerCode();
    }

    // посылаем команду серверу FTP
    private boolean sendCommand(String command) {
        System.out.print(">> " + command);  // печать команды
        return socketCmd.send(command);
    }

    // переводим сервер в пассивный режим
    private boolean setPassiveMode() {

        return socketData.connect();
    }

    // переводим сервер в активный режим
    private boolean setActiveMode() {
        String ip = socketCmd.getLANIP();
        if (ip == null) return false;
        try {
            // создаем сервер
            if (serverSocket != null && randomPort) {
                serverSocket.close();
                serverSocket = null;
            }
            if (serverSocket == null) {
                if (randomPort) {
                    serverSocket = new ServerSocket(0);
                    localPort = serverSocket.getLocalPort();
                } else {
                    serverSocket = new ServerSocket(localPort);
                }
            }
            // посылаем команду PORT h1,h2,h3,h4,p1,p2 и ждем код ответа 200
            String command = String.format("PORT %s,%d,%d\r\n",
                    ip.replaceAll("\\.", ","),
                    localPort / 256, localPort % 256);
            return (sendCommand(command) && readAnswerCode() == 200);
        } catch (IOException ex) {
            //if (serverSocket != null)
            return false;
        }
    }

    // Переводим серрвер в нужный режим
    private boolean initDataSocket() {
        return activeMode ? setActiveMode() : setPassiveMode();
    }

    // проверка перед передачей данных
    private boolean prepareDataSocket() {
        // если пассивный режим, ничего делать не надо
        if (!activeMode) return true;
        // если активный режим, ожидать соединения от сервера
        try {
            socketData = new FtpSocket(serverSocket.accept());
            // соединение для передачи данных установлено
            return true;
        } catch (IOException e) {
            socketData = null;
            return false;
        }
    }

    // закрытие сокета после передачи данных
    private void closeDataSocket() {
        // если пассивный режим, ничего делать не надо
        if (!activeMode) return;
        // если активный режим
        socketData.disconnect();  // закрываем сокет данных
        try {
            serverSocket.close();
        } catch (IOException e) {
        }
        serverSocket = null;
        socketData = null;
    }

    //проверка на то, закрыт ли сокет команд
    public boolean isConnected() {
        return socketCmd.isConnected();
    }

    // открыть FTP сессию
    public boolean connect() {
        // соединяемся с сервером
        if (!socketCmd.connect()) return false;
        socketData = null;
        if (readAnswerCode() != 220) { // ожидаем код ответа 220
            disconnect();
            return false;
        }

        // авторизация на сервере
        // посылаем команду USER <username> и ожидаем ответа 331
        String cmd = String.format("USER %s\r\n", user); //String value
        if (!sendCommand(cmd) || readAnswerCode() != 331) {
            disconnect();
            return false;
        }

        // посылаем команду PASS <password> и ожидаем ответа 230
        cmd = String.format("PASS %s\r\n", password);
        if (!sendCommand(cmd) || readAnswerCode() != 230) {
            disconnect();
            return false;
        }

        // переводим сервер в двоичный 8-битный режим
        // посылаем команду TYPE I и ожидаем ответа 200
        if (!sendCommand("TYPE I\r\n") || readAnswerCode() != 200) {
            disconnect();
            return false;
        }
        if (localPort != 0)
            activeMode = setActiveMode();
        return true;
    }

    // закрыть FTP сессию
    public void disconnect() {
        socketCmd.disconnect();
        if (socketData != null) socketData.disconnect();
        socketData = null;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
            }
            serverSocket = null;
        }
    }

    // завершить работу с FTP сервером
    public boolean quit() {
        // посылаем команду QUIT и ожидаем ответа 221
        return (sendCommand("QUIT\r\n") && readAnswerCode() == 221);
    }

    // получить содержание текущей директории на FTP сервере
    public String list(boolean shortmode) {
        int code;
        if (!initDataSocket()) return null;
        String cmd = String.format("%s\r\n", LIST[shortmode ? 1 : 0]);
        // посылаем команду LIST и ожидаем ответа 150
        if (!sendCommand(cmd)
                || ((code = readAnswerCode()) != 150 && code != 125))
            return null;
        if (!prepareDataSocket()) return null;
        // если успешно, считываем данные с сервера, пока он не закроет соединение
        StringBuilder sb = new StringBuilder();
        while (true) {
            // читаем из сокета данных
            String s = socketData.recv();
            // сервер закрыл соединение, больше нет данных
            if (s == null || s.isEmpty()) break;
            sb.append(s);
        }
        closeDataSocket();  // закрываем сокет данных
        // ожидаем код 226
        if (readAnswerCode(150) != 226) return null;
        return sb.toString();   // возвращаем содержимое директории
    }

    // создать директорию на FTP сервере
    public boolean mkdir(String dirname) {
        // посылаем команду MKD <dirname> и ожидаем ответа 257
        String cmd = String.format("MKD %s\r\n", dirname);
        return (sendCommand(cmd) && readAnswerCode() == 257);
    }

    // удалить директорию на FTP сервере
    public boolean rmdir(String dirname) {
        // посылаем команду RMD <dirname> и ожидаем ответа 250
        String cmd = String.format("RMD %s\r\n", dirname);
        return (sendCommand(cmd) && readAnswerCode() == 250);
    }

    // перейти в директорию на FTP сервере
    public boolean chdir(String dirname) {
        // посылаем команду CWD <dirname> и ожидаем ответа 250
        String cmd = String.format("CWD %s\r\n", dirname);
        return (sendCommand(cmd) && readAnswerCode() == 250);
    }

    // получить текущую директорию на FTP сервере
    public String pwd() {
        // посылаем команду PWD и ожидаем ответа 257
        if (!sendCommand("PWD\r\n") || readAnswerCode() != 257) return null;
        // ответ сервера такой:
        // << 257 "<dirname>" is the current directory
        // возвращаем имя директории без лишнего текста и кавычек
        int i1 = answer.indexOf('"');
        int i2 = answer.indexOf('"', i1 + 1);
        if (i1 < 0 || i2 < 0) return null;
        return answer.substring(i1 + 1, i2);
    }

    // закачать файл на FTP сервер
    boolean upload(String filename) {
        File file = new File(filename);
        if (!file.exists()) return false;
        DataInputStream dis;
        try {
            FileInputStream fis = new FileInputStream(file);
            dis = new DataInputStream(fis);
            if (!initDataSocket()) dis.close();
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
        int code;
        // посылаем команду STOR <filename> и ожидаем ответа 150
        String cmd = String.format("STOR %s\r\n", file.getName());
        try {
            if (!sendCommand(cmd)
                    || ((code = readAnswerCode()) != 150 && code != 125)
                    || !prepareDataSocket()) {
                dis.close();
                return false;
            }
            // пересылаем содержимое файла
            while (true) {
                int rcount = dis.read(data); // читаем из файла
                if (rcount <= 0) break; // если конец файла, то на выход из цикла
                socketData.send(data, rcount);
            }
            dis.close(); // закрываем файл
        } catch (IOException ex) {
            try {
                dis.close(); // закрываем файл
            } catch (IOException ex1) {
            }
            closeDataSocket();  // закрываем сокет данных
            return false;
        }
        closeDataSocket();  // закрываем сокет данных
        return readAnswerCode() == 226; // ждем код ответа 226
    }

    // скачать файл с FTP сервера
    boolean download(String filename) {
        File file = new File(filename);
        DataOutputStream dos;
        try {
            FileOutputStream fos = new FileOutputStream(file);
            dos = new DataOutputStream(fos);
            if (!initDataSocket()) {
                dos.close();
                file.delete();
            }
        } catch (FileNotFoundException ex) {
            return false;
        } catch (IOException ex) {
            return false;
        }
        int code;
        // посылаем команду RETR <filename> и ожидаем ответа 150 (или 125)
        String cmd = String.format("RETR %s\r\n", filename);
        if (!sendCommand(cmd)
                || ((code = readAnswerCode()) != 150 && code != 125)
                || !prepareDataSocket()) {
            try {
                dos.close();
                file.delete();
            } catch (IOException ex) {
            }
            return false;
        }
        try {
            while (true) {
                // принимаем содержание файла с сервера
                int rcount = socketData.recv(data, BUFSIZE);
                if (rcount <= 0) break; // если нет данных, значит сервер закрыл
                // соединение, файл считан, выходим из цикла
                // записываем полученные данные в локальный файл
                dos.write(data, 0, rcount);
            }
            dos.close(); // закрываем файл
            closeDataSocket();  // закрываем сокет данных
            return readAnswerCode() == 226; // ждем код ответа 226
        } catch (IOException ex) {
            try {
                dos.close();
            } catch (IOException ex1) {
            }
            closeDataSocket();  // закрываем сокет данных
            file.delete();
            return false;
        }
    }

    // удалить файл на FTP сервере
    boolean delete(String filename) {
        // посылаем команду DELE <filename> и ожидаем ответа 250
        String cmd = String.format("DELE %s\r\n", filename);
        return sendCommand(cmd) && readAnswerCode() == 250;
    }

    // переименовать файл на FTP сервере
    boolean rename(String from, String to) {
        // посылаем команду RNFR <from> и ожидаем ответа 350
        String cmd = String.format("RNFR %s\r\n", from);
        if (!sendCommand(cmd) || readAnswerCode() != 350) return false;
        // посылаем команду RNTO <to> и ожидаем ответа 250
        cmd = String.format("RNTO %s\r\n", to);
        return sendCommand(cmd) && readAnswerCode() == 250;
    }

    // установить режим передачи данных
    public boolean type(char t) {
        char u = Character.toUpperCase(t);
        switch (u) {
            // двоичный
            case 'I':
                // текстовый
            case 'A':
                break;
            default:
                return false;
        }
        // посылаем команду TYPE <type> и ожидаем ответа 200
        String cmd = String.format("TYPE %c\r\n", u);
        return sendCommand(cmd) && readAnswerCode() == 200;
    }

}