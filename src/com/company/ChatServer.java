package com.company;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This is the chat server program.
 * Press Ctrl + C to terminate the program.
 *
 * @author www.codejava.net
 */
public class ChatServer {
    private int port;
    private Set<String> userNames = new HashSet<>();
    private Set<UserThread> userThreads = new HashSet<>();
    private HashMap<String, ArrayList<UserThread>> groups = new HashMap<>();

    public ChatServer(int port) {
        this.port = port;
    }

    public void execute() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Chat Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New user connected");

                UserThread newUser = new UserThread(socket, this);
                userThreads.add(newUser);
                newUser.start();

            }

        } catch (IOException ex) {
            System.out.println("Error in the server: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Syntax: java ChatServer <port-number>");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);

        ChatServer server = new ChatServer(port);
        server.execute();
    }

    /**
     * Delivers a message from one user to others (broadcasting)
     */
    void broadcast(String message, UserThread excludeUser) {
        for (UserThread aUser : userThreads) {
            if (aUser != excludeUser) {
                aUser.sendMessage(message);
            }
        }
    }

    void brodcastPM(String message, String pmUser) {
        for (UserThread aUser : userThreads) {
            if (aUser.getUsername().equalsIgnoreCase(pmUser)) {
                aUser.sendMessage(message);
                return;
            }
        }
    }

    void brodcastGroup(String message, String groupName) {
        for (int i = 0; i < groups.get(groupName).size(); i++) {
            groups.get(groupName).get(i).sendMessage(message);
        }
    }

    void createGroup(String groupName, ArrayList<UserThread> userNames){
            // TODO Denne løkke bruges flere steder
        if (!groups.containsKey(groupName)) groups.put(groupName, userNames);
        /*
        for (int i = 0; i < userNames.size(); i++) {
            for (UserThread aUser : userThreads) {
                brodcastPM("Indre: " + aUser.getUsername(), userNames.get(0).getUsername());
                if (aUser.getUsername().equalsIgnoreCase(groups.get(groupName).get(i).getUsername())) {
                    groups.get(groupName).add(aUser);
                    break;
                }
            }
        }

         */

    }

    void addOrJoinGroup(String groupName, String userName) {
        if(!groups.get(groupName).isEmpty()) {
            for (UserThread aUser : userThreads) {
                if (aUser.getUsername().equalsIgnoreCase(userName)) {
                    groups.get(groupName).add(aUser);
                    return;
                }
            }
        }
    }

    public void removeUserFromGroup(String groupName, String userName) {
        for (UserThread aUser : groups.get(groupName)) {
            if (aUser.getUsername().equalsIgnoreCase(userName)) {
                groups.get(groupName).remove(aUser);
                return;
            }
        }
    }

    UserThread convertStringToUT(String userName){
        for (UserThread aUser : userThreads) {
            if (aUser.getUsername().equalsIgnoreCase(userName)) {
                return aUser;
            }
        }
        return null;
    }




    /**
     * Stores username of the newly connected client.
     */
    void addUserName(String userName) {
        userNames.add(userName);
    }

    /**
     * When a client is disconneted, removes the associated username and UserThread
     */
    void removeUser(String userName, UserThread aUser) {
        boolean removed = userNames.remove(userName);
        if (removed) {
            userThreads.remove(aUser);
            System.out.println("The user " + userName + " quitted");
        }
    }

    Set<String> getUserNames() {
        return this.userNames;
    }

    public HashMap<String, ArrayList<UserThread>> getGroups() {
        return groups;
    }

    /**
     * Returns true if there are other users connected (not count the currently connected user)
     */
    boolean hasUsers() {
        return !this.userNames.isEmpty();
    }
}

/**
 * This thread handles connection for each connected client, so the server
 * can handle multiple clients at the same time.
 *
 * @author www.codejava.net
 */
class UserThread extends Thread {
    private Socket socket;
    private ChatServer server;
    private PrintWriter writer;
    private String userName;

    public UserThread(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void run() {
        try {
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            OutputStream output = socket.getOutputStream();
            writer = new PrintWriter(output, true);

            printUsers();

            String userName = reader.readLine();
            this.userName = userName;
            server.addUserName(userName);

            String serverMessage = "New user connected: " + userName;
            server.broadcast(serverMessage, this);

            String clientMessage;

            do {
                clientMessage = reader.readLine();
                String[] pmUser = clientMessage.split(" ");
                if(pmUser[0].equalsIgnoreCase("pm")) {
                    serverMessage = "[" + userName + " PRIVATE]: " + clientMessage;
                    server.brodcastPM(serverMessage, pmUser[1]);
                } else if(pmUser[0].equalsIgnoreCase("createGroup")) {
                    ArrayList<UserThread> users = new ArrayList<>();
                    users.add(this);
                    for (int i = 2; i < pmUser.length; i++) {
                        users.add(server.convertStringToUT(pmUser[i]));
                    }
                    server.createGroup(pmUser[1], users);
                    server.broadcast("Gruppen \"" + pmUser[1] + "\" er oprettet af: " + this.getUsername(), null);
                } else if(pmUser[0].equalsIgnoreCase("joinGroup")) {
                    server.addOrJoinGroup(pmUser[1], this.userName);
                    server.broadcast(this.userName + " Joiner gruppen " + pmUser[1], null);
                } else if(pmUser[0].equalsIgnoreCase("addToGroup")) {
                    server.addOrJoinGroup(pmUser[1], pmUser[2]);
                    server.brodcastPM("You have been added to the group: " + pmUser[1], pmUser[2]);
                }  else if(pmUser[0].equalsIgnoreCase("group")) {
                    server.brodcastGroup(clientMessage.substring(pmUser[0].length() + pmUser[1].length() + 1)
                            , pmUser[1]);
                    //server.broadcast(clientMessage.substring(pmUser[0].length()+ pmUser[1].length()+1)
                    //        , null);
                } else if (pmUser[0].equalsIgnoreCase("removeUser")) {
                    server.removeUserFromGroup(pmUser[1], pmUser[2]);
                    server.broadcast("User: " + pmUser[2] + " has been removed from group: " + pmUser[1], null);
                } else {
                    serverMessage = "[" + userName + "]: " + clientMessage;
                    server.broadcast(serverMessage, this);
                }
            } while (!clientMessage.equals("bye"));

            server.removeUser(userName, this);
            socket.close();

            serverMessage = userName + " has quitted.";
            server.broadcast(serverMessage, this);

        } catch (IOException ex) {
            System.out.println("Error in UserThread: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Sends a list of online users to the newly connected user.
     */
    void printUsers() {
        if (server.hasUsers()) {
            writer.println("Connected users: " + server.getUserNames());
        } else {
            writer.println("No other users connected");
        }
    }

    /**
     * Sends a message to the client.
     */
    void sendMessage(String message) {
        writer.println(message);
    }

    public String getUsername() {
        return userName;
    }
}