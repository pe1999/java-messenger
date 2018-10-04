package ru.geekbrains;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String login;
    private String nick;

    public String getNick() {
        return nick;
    }

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            startWorkerThread();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startWorkerThread() {
        server.getServerExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        String msg = in.readUTF();
                        if (msg.startsWith("/auth ")) {
                            // /auth login1 pass1
                            String[] tokens = msg.split(" ");
                            ClientHandler.this.login = tokens[1];
                            String nick = server.getAuthHandler().getNickByLoginPass(tokens[1], tokens[2]);
                            if (nick != null) {
                                if (server.isNickBusy(nick)) {
                                    out.writeUTF("Учетная запись уже используется");
                                    server.getLogger().log(Level.WARNING, "Учетная запись уже используется");
                                    continue;
                                }
                                out.writeUTF("/authok " + nick);
                                ClientHandler.this.nick = nick;
                                server.subscribe(ClientHandler.this);
                                server.getLogger().log(Level.INFO, "Пользователь " + nick + " авторизовался");
                                break;
                            } else {
                                out.writeUTF("Неверный логин/пароль");
                                server.getLogger().log(Level.WARNING, "Неверный логин/пароль");
                            }
                        }
                    }
                    while (true) {
                        String msg = in.readUTF();
                        if (msg.startsWith("/")) {
                            if (msg.startsWith("/w ")) {
                                // /w nick2 hello hello
                                String[] tokens = msg.split(" ", 3);
                                server.sendPrivateMsg(ClientHandler.this, tokens[1], tokens[2]);
                                server.getLogger().log(Level.INFO, "Пользователь " + ClientHandler.this.nick + " отправил приватное сообщение");
                            }
                            if (msg.startsWith("/changenick ")) {
                                String[] tokens = msg.split(" ");
                                if (tokens.length == 2) {
                                    if (server.getAuthHandler().changeNick(ClientHandler.this.login, tokens[1])) {
                                        server.getLogger().log(Level.INFO, "Пользователь " + ClientHandler.this.nick + " сменил ник на " + tokens[1]);
                                        server.broadcastMsg(ClientHandler.this, "Пользователь " + ClientHandler.this.nick + " сменил ник на " + tokens[1] + ".");
                                        ClientHandler.this.nick = tokens[1];
                                        server.broadcastClientsList();
                                    } else {
                                        server.getLogger().log(Level.WARNING, "Ник " + tokens[1] + " уже используется");
                                        ClientHandler.this.sendMessage("Ник уже используется.");
                                    }
                                } else {
                                    server.getLogger().log(Level.WARNING, "Пользователь " + ClientHandler.this.nick + " использовал неправильный формат команды /changenick");
                                    ClientHandler.this.sendMessage("Неправильный формат команды /changenick.");
                                }
                            }
                        } else {
                            server.getLogger().log(Level.INFO, "Пользователь " + ClientHandler.this.nick + " отправил широковещательное сообщение");
                            server.broadcastMsg(ClientHandler.this, msg);
                        }
                        //System.out.println(msg);
                    }
                } catch (IOException e) {
                    //e.printStackTrace();
                } finally {
                    ClientHandler.this.closeConnection();
                    server.getLogger().log(Level.INFO, (ClientHandler.this.nick != null ? "Пользователь " + ClientHandler.this.nick : "Клиент") + " отключился");
                }
            }
        });
    }

    public void sendMessage(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        server.unsubscribe(this);
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
