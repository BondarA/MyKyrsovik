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
 * ����� �������. ����� ���� �� �����, ��������� ���������, ������� SocketProcessor �� ������ ���������
 */
public class ChatServer {
    private ArrayList<String> listMessage = new ArrayList<String>();
    
    private ServerSocket ss; // ��� ������-�����
    private Thread serverThread; // ������� ���� ��������� ������-������
    private int port; // ���� ������ ������.
    //�������, ��� ��������� ��� SocketProcessor� ��� ��������
    BlockingQueue<SocketProcessor> q = new LinkedBlockingQueue<SocketProcessor>();
 
    /**
     * ����������� ������� �������
     * @param port ����, ��� ����� ������� �������� ���������.
     * @throws IOException ���� �� �������� ������� ������-�����, ������� �� ���������, ������ ������� �� ����� ������
     */
    public ChatServer(int port) throws IOException {
        ss = new ServerSocket(port); // ������� ������-�����
        this.port = port; // ��������� ����.
    }
 
    /**
     * ������� ���� �������������/�������� ��������.
     */
    void run() {
        serverThread = Thread.currentThread(); // �� ������ ��������� ���� (����� ����� �� ���� interrupt())
        while (true) { //����������� ����, ����...
            Socket s = getNewConn(); // �������� ����� ���������� ��� ����-���������
            if (serverThread.isInterrupted()) { // ���� ��� ����-����������, �� ���� ���� ���� interrupted(),
                // ���� ����������
                break;
            } else if (s != null){ // "������ ���� ������� ������� ������"...
                try {
                    final SocketProcessor processor = new SocketProcessor(s); // ������� �����-���������
                    final Thread thread = new Thread(processor); // ������� ��������� ����������� ���� ������ �� ������
                    thread.setDaemon(true); //������ �� � ������ (����� �� ������� �� ��������)
                    thread.start(); //���������
                    q.offer(processor); //��������� � ������ �������� �����-�����������
                } //��� ������ � �������. ���� ������� ������� (new SocketProcessor()) ����������,
                // �� ��������� ������ �������, ���� ��������� �� �����, � ������ �� ��������
                catch (IOException ignored) {}  // ���� �� ���������� �������� �������� ��� �� ���������.
            }
        }
    }
 
    /**
     * ������� ����� �����������.
     * @return ����� ������ �����������
     */
    private Socket getNewConn() {
        Socket s = null;
        try {
            s = ss.accept();
        } catch (IOException e) {
            shutdownServer(); // ���� ������ � ������ ������ - "�����" ������
        }
        return s;
    }
 
    /**
     * ����� "��������" �������
     */
    private synchronized void shutdownServer() {
        // ������������ ������ ������� ���������, ��������� ������
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
     * ������� ����� ���������
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        new ChatServer(9000).run(); // ���� ������ �� ��������, ���������
        // ������� �� ���������, � ����� run() �� �����������
    }
 
    /**
     * ��������� ����� ����������� ��������� ������ ��������.
     */
    private class SocketProcessor implements Runnable{
        Socket s; // ��� �����
        BufferedReader br; // ��������������� �������� ������
        BufferedWriter bw; // ���������������� �������� � �����
 
        /**
         * ��������� �����, ������� ������� �������� � ��������. ���� �� ���������� - �������� ��� �������� �������
         * @param socketParam �����
         * @throws IOException ���� ������ � �������� br || bw
         */
        SocketProcessor(Socket socketParam) throws IOException {
            s = socketParam;
            br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            bw = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8") );
        }
 
        /**
         * ������� ���� ������ ���������/��������
         */
        public void run() {
            while (!s.isClosed()) { // ���� ����� �� ������...
                String line = null;
                try {
                    line = br.readLine(); // ������� ��������.
                } catch (IOException e) {
                    close(); // ���� �� ���������� - ��������� �����.
                }
 
                if (line == null) { // ���� ������ null - ������ ���������� � ������� ������.
                    close(); // �� ��������� �����
                } else if ("shutdown".equals(line)) {
                    try {
                        // ���� ��������� ������� "�������� ������", ��...
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
                    
                    serverThread.interrupt(); // ������� �������� ���� � �������� ���� � ������������� ����������.
                    try {
                        new Socket("localhost", port); // ������� ����-������� (����� ����� �� .accept())
                    } catch (IOException ignored) { //������ �����������
                    } finally {
                        shutdownServer(); // � ����� ������ ������ ������� ��� ������ shutdownServer().
                    }
                } else { // ����� - ��������� �������� �� ������ �����-�����������
                    for (SocketProcessor sp:q) {
                        listMessage.add(line);
                        sp.send(line);
                    }
                }
            }
        }
 
        /**
         * ����� �������� � ����� ���������� ������
         * @param line ������ �� �������
         */
        public synchronized void send(String line) {
            try {
                bw.write(line); // ����� ������
                bw.write("\n"); // ����� ������� ������
                bw.flush(); // ����������
            } catch (IOException e) {
                close(); //���� ���� � ������ �������� - ��������� ������ �����.
            }
        }
 
        /**
         * ����� ��������� ��������� ����� � ������� ��� �� ������ �������� �������
         */
        public synchronized void close() {
            q.remove(this); //������� �� ������
            if (!s.isClosed()) {
                try {
                    s.close(); // ���������
                } catch (IOException ignored) {}
            }
        }
 
        /**
         * ����������� ������ �� ������ ������.
         * @throws Throwable
         */
        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }
}
