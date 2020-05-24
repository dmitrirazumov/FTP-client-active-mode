import java.io.File;
import java.io.FileInputStream;
import java.util.Scanner;

public class Client {
    static String[] commands = {"quit", "help", "list", "nlst", "pwd",
            "cwd", "mkd", "rmd", "stor", "retr", "dele", "mv", "type"};
    static String[] helpstrings = {
            "quit               выход",
            "help               эта страница",
            "list               содержимое директории",
            "nlst               содержимое директории (краткое)",
            "pwd                текущая директория",
            "cwd <dir>          переход в директорию",
            "mkd <dir>          создать директорию",
            "rmd <dir>          удалить директорию",
            "stor <file>        загрузить файл на сервер",
            "retr <file>        загрузить файл с сервера",
            "dele <file>        удалить файл",
            "mv <file1> <file2> переименовать файл",
            "type <I|A>         установить тип передачи данных"
    };
    static final int QUIT       = 0;
    static final int HELP       = 1;
    static final int LIST       = 2;
    static final int NLST       = 3;
    static final int PWD        = 4;
    static final int CWD        = 5;
    static final int MKD        = 6;
    static final int RMD        = 7;
    static final int STOR       = 8;
    static final int RETR       = 9;
    static final int DELE       = 10;
    static final int MV         = 11;
    static final int TYPE       = 12;

    static String host, user, password;
    static int port, lport;

    static final String[] keys = {"host", "user", "password",
            "port", "lport"};

    private static int getCmdIndex(String cmd) { //получаем номер команды cmd
        String s = cmd.toLowerCase();
        for (int i = 0, c = commands.length; i < c; i++)
            if (s.equals(commands[i])) return i;
        return -1;
    }

    private static int getKeyIndex(String key) { //получаем ключи
        String s = key.toLowerCase();
        for (int i = 0, c = keys.length; i < c; i++)
            if (s.equals(keys[i])) return i;
        return -1;
    }

    private static void help() {
        System.out.println("Поддерживаются команды: ");
        for (String help : helpstrings) {
            System.out.println(help);
        }
        System.out.println();
    }

    private static boolean init(String filename) {
        host = null;
        user = null;
        password = null;
        port = 21; //связываемся с сервером через TCP-порт 21
        lport = 0; //если в качестве локального порта указан 0, то система сама выделит свободный порт
        File file = new File(filename);
        try {
            FileInputStream fis = new FileInputStream(file);
            Scanner scanner = new Scanner(fis);
            scanner.useDelimiter("\n");
            String s;
            while (scanner.hasNext()) {
                s = scanner.nextLine();
                int i = s.indexOf('#');     // комментарий в файле
                if (i >= 0) s = s.substring(0, i);
                s = s.trim();
                if (s.length() == 0) continue;
                String[] tokens = s.split("=");
                if (tokens.length != 2 || (i = getKeyIndex(tokens[0].trim())) < 0)
                    throw new Exception("Ошибка в строке: " + s);
                s = tokens[1].trim();
                switch (i) {
                    case 0: // host
                        host = s;
                        break;
                    case 1: // user
                        user = s;
                        break;
                    case 2: // password
                        password = s;
                        break;
                    case 3: // port
                        port = Integer.parseInt(s);
                        break;
                    case 4: // lport
                        lport = Integer.parseInt(s);
                        break;
                }
            }
            fis.close();
            if (host == null)
                throw new Exception("FTP сервер не задан");
        } catch (Exception e) {
            if (e.getClass() == NumberFormatException.class)
                System.out.print("NumberFormatException: ");
            System.out.println(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * @param args the command line arguments
     */

    public static void main(String[] args) {
        // TODO code application logic here
        if (!init("ftpclient.ini")) System.exit(1);
        //FtpClient client = new FtpClient("ftp.funet.fi", null, null, 21, 40000);
        FtpClient client = new FtpClient(host, user, password, port, lport);
        if (!client.connect()) System.exit(1); //вход в сессию
        help();
        Scanner scanner = new Scanner(System.in);
        String s;
        while (true) {
            if (!client.isConnected()) {
                System.out.println("Соединение разорвано. Переподключение...");
                if (!client.connect()) System.exit(1);
            }
            String[] tokens = scanner.nextLine().split(" ");
            int cmd = getCmdIndex(tokens[0]);
            if (cmd == QUIT) break;
            switch (cmd) {
                case HELP:
                    help();
                    break;
                case LIST:
                    s = client.list(false);
                    if (s != null) System.out.println(s);
                    break;
                case NLST:
                    s = client.list(true);
                    if (s != null) System.out.println(s);
                    break;
                case PWD:
                    s = client.pwd();
                    if (s != null) System.out.println("Рабочая директория: " + s);
                    break;
                case CWD:
                    if (tokens.length != 2) {
                        System.out.println("Формат: cwd <директория>");
                        break;
                    }
                    client.chdir(tokens[1]);
                    break;
                case MKD:
                    if (tokens.length != 2) {
                        System.out.println("Формат: mkd <директория>");
                        break;
                    }
                    client.mkdir(tokens[1]);
                    break;
                case RMD:
                    if (tokens.length != 2) {
                        System.out.println("Формат: rmd <директория>");
                        break;
                    }
                    client.rmdir(tokens[1]);
                    break;
                case STOR:
                    if (tokens.length != 2) {
                        System.out.println("Формат: stor <файл>");
                        break;
                    }
                    client.upload(tokens[1]);
                    break;
                case RETR:
                    if (tokens.length != 2) {
                        System.out.println("Формат: retr <файл>");
                        break;
                    }
                    client.download(tokens[1]);
                    break;
                case DELE:
                    if (tokens.length != 2) {
                        System.out.println("Формат: dele <файл>");
                        break;
                    }
                    client.delete(tokens[1]);
                    break;
                case MV:
                    if (tokens.length != 3) {
                        System.out.println("Формат: mv <файл1> <файл2>");
                        break;
                    }
                    client.rename(tokens[1], tokens[2]);
                    break;
                case TYPE:
                    if (tokens.length != 2 || tokens[1].length() != 1) {
                        System.out.println("Формат: type <A|I>");
                        break;
                    }
                    client.type(tokens[1].charAt(0));
                    break;
                default:
                    System.out.println(String.format("Неверная команда %s", tokens[0]));
            }
        }
        client.quit();
        client.disconnect();
        System.exit(0);
    }
}