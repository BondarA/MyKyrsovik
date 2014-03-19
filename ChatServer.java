package andrew;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
 
/**
 * Класс сервера. Сидит тихо на порту, принимает сообщение, создает SocketProcessor на каждое сообщение
 */
public class ChatServer {
    private ArrayList<String> listMessage = new ArrayList<String>();
    
    private ServerSocket ss; // сам сервер-сокет
    private Thread serverThread; // главная нить обработки сервер-сокета
    private int port; // порт сервер сокета.
    //очередь, где храняться все SocketProcessorы для рассылки
    BlockingQueue<SocketProcessor> q = new LinkedBlockingQueue<SocketProcessor>();
 
    /**
     * Конструктор объекта сервера
     * @param port Порт, где будем слушать входящие сообщения.
     * @throws IOException Если не удасться создать сервер-сокет, вылетит по эксепшену, объект Сервера не будет создан
     */
    public ChatServer(int port) throws IOException {
        ss = new ServerSocket(port); // создаем сервер-сокет
        this.port = port; // сохраняем порт.
    }
 
    /**
     * главный цикл прослушивания/ожидания коннекта.
     */
    void run() {
        serverThread = Thread.currentThread(); // со старта сохраняем нить (чтобы можно ее было interrupt())
        while (true) { //бесконечный цикл, типа...
            Socket s = getNewConn(); // получить новое соединение или фейк-соедиение
            if (serverThread.isInterrupted()) { // если это фейк-соединение, то наша нить была interrupted(),
                // надо прерваться
                break;
            } else if (s != null){ // "только если коннект успешно создан"...
                try {
                    final SocketProcessor processor = new SocketProcessor(s); // создаем сокет-процессор
                    final Thread thread = new Thread(processor); // создаем отдельную асинхронную нить чтения из сокета
                    thread.setDaemon(true); //ставим ее в демона (чтобы не ожидать ее закрытия)
                    thread.start(); //запускаем
                    q.offer(processor); //добавляем в список активных сокет-процессоров
                } //тут прикол в замысле. Если попытка создать (new SocketProcessor()) безуспешна,
                // то остальные строки обойдем, нить запускать не будем, в список не сохраним
                catch (IOException ignored) {}  // само же исключение создания коннекта нам не интересно.
            }
        }
    }
 
    /**
     * Ожидает новое подключение.
     * @return Сокет нового подключения
     */
    private Socket getNewConn() {
        Socket s = null;
        try {
            s = ss.accept();
        } catch (IOException e) {
            shutdownServer(); // если ошибка в момент приема - "гасим" сервер
        }
        return s;
    }
 
    /**
     * метод "глушения" сервера
     */
    private synchronized void shutdownServer() {
        // обрабатываем список рабочих коннектов, закрываем каждый
        for (SocketProcessor s: q) {
            s.close();
        }
        if (!ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException ignored) {}
        }
    }
 
    /**
     * входная точка программы
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        new ChatServer(9000).run(); // если сервер не создался, программа
        // вылетит по эксепшену, и метод run() не запуститься
    }
 
    /**
     * вложенный класс асинхронной обработки одного коннекта.
     */
    private class SocketProcessor implements Runnable{
        Socket s; // наш сокет
        BufferedReader br; // буферизировнный читатель сокета
        BufferedWriter bw; // буферизированный писатель в сокет
 
        /**
         * Сохраняем сокет, пробуем создать читателя и писателя. Если не получается - вылетаем без создания объекта
         * @param socketParam сокет
         * @throws IOException Если ошибка в создании br || bw
         */
        SocketProcessor(Socket socketParam) throws IOException {
            s = socketParam;
            br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8") );
        }
 
        /**
         * Главный цикл чтения сообщений/рассылки
         */
        public void run() {
            while (!s.isClosed()) { // пока сокет не закрыт...
                String line = null;
                try {
                    line = br.readLine(); // пробуем прочесть.
                } catch (IOException e) {
                    close(); // если не получилось - закрываем сокет.
                }
 
                if (line == null) { // если строка null - клиент отключился в штатном режиме.
                    close(); // то закрываем сокет
                } else if ("shutdown".equals(line)) {
                    try {
                        // если поступила команда "погасить сервер", то...
                        FileWriter fileWriter = new FileWriter("HistoryOfChar.txt");
                        for(int i = 0; i < listMessage.size(); i++) {
                                if(i % 2 == 0) {
                                fileWriter.append(listMessage.get(i));
                                fileWriter.append("\t||\t");
                                fileWriter.flush();
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(ChatServer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    
                    serverThread.interrupt(); // сначала возводим флаг у северной нити о необходимости прерваться.
                    try {
                        new Socket("localhost", port); // создаем фейк-коннект (чтобы выйти из .accept())
                    } catch (IOException ignored) { //ошибки неинтересны
                    } finally {
                        shutdownServer(); // а затем глушим сервер вызовом его метода shutdownServer().
                    }
                } else { // иначе - банальная рассылка по списку сокет-процессоров
                    for (SocketProcessor sp:q) {
                        listMessage.add(line);
                        sp.send(line);
                    }
                }
            }
        }
 
        /**
         * Метод посылает в сокет полученную строку
         * @param line строка на отсылку
         */
        public synchronized void send(String line) {
            try {
                bw.write(line); // пишем строку
                bw.write("\n"); // пишем перевод строки
                bw.flush(); // отправляем
            } catch (IOException e) {
                close(); //если глюк в момент отправки - закрываем данный сокет.
            }
        }
 
        /**
         * метод аккуратно закрывает сокет и убирает его со списка активных сокетов
         */
        public synchronized void close() {
            q.remove(this); //убираем из списка
            if (!s.isClosed()) {
                try {
                    s.close(); // закрываем
                } catch (IOException ignored) {}
            }
        }
 
        /**
         * финализатор просто на всякий случай.
         * @throws Throwable
         */
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }
}
