package chatroomserver;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.security.PublicKey;

/**
 * This class handles a specific chat room and its users.
 * @author polka
 * @version 0.1
 */
public class ChatRoom extends Thread {
    
    //Public Varibales ---------------------------------------------------------
    
    /**Name of the chat room*/ 
    public final String name;
    /**List of all the usernames in the chat room*/
    public final ArrayList<String> users = new ArrayList<>();
    /**Dictionary of all the sockets*/
    public final Map<String, Socket> socks = new HashMap<>();
    /**Dictionary of all the output streams for all the users*/
    public final Map<String, DataOutputStream> outs = new HashMap<>();
    /**Dictionary for all the threads that listen for user input*/
    public final Map<String, User> threads = new HashMap<>();
    
    //Encrypted Chat Room
    /**Dictionary for the pubic keys of all the users
     * not implemented yet
     */
    public final Map<String, PublicKey> keys = new HashMap<>();//to be used for encrypting data
    
    //Passworded chat room
    /**If the chat room uses a password to enter*/
    public final boolean hasPassword = false;
    /**The hash of the password to allow new people to enter*/
    public String passwordHash;
    
    //Public Methods -----------------------------------------------------------
    
    /**Creates a new chat room thread and sets its name.
     * @param roomName The name of the chat room.
     */
    public ChatRoom(String roomName){
        name = roomName;
        System.out.println(name+"::Creating room.");
    }
   
    /**The main function of the thread. Exiting this function closes the thread.
     * Checks to see if the room is empty every X seconds and returns if it is.
     */
    @Override
    public void run(){
        int X = 10;
        System.out.println(name+"::Running room.");
        while(users.size() > 0){
            try{
                TimeUnit.SECONDS.sleep(X);
            }catch(InterruptedException e){}
        }
        close();
        System.out.println(name+"::Closed room.");
    }
    
    /**Closes the chat room and removes its name from this list in 
     * ChatRoomServer.java
     */
    public void close(){
        System.out.println(name+"::Closing room.");
        ChatRoomServer.removeRoom(name);
    }
    
    /**Creates a new thread to listen for massages from a specific client.
     * @param username Username for the specific user.
     */
    public void newClientThread(String username){
        threads.put(username, new User(this, username));
        threads.get(username).start();
    }
    
    /**Sends a message to all users in the chat room.
     * @param message The message that you want to send.
     */
    public void messageAll(String message){
        users.forEach((user) -> {
            try{
                outs.get(user).writeUTF(message);
            }catch(IOException e){
                removeUser(user);
            }
        });
        System.out.println(name+"::"+message);
    }
    
    /**Adds a new user to the chat room.
     * @param username Username for the new user.
     * @param newSock The socket of the new user.
     */
    public void addUser(String username, Socket newSock){
        try{
            outs.put(username, new DataOutputStream(newSock.getOutputStream()));
            outs.get(username).writeUTF("Server: Welcome to "+name);
        }catch(IOException e){
            if(outs.containsKey(username)){
                outs.remove(username);
            }
            return;
        }
        socks.put(username, newSock);
        users.add(username);
        newClientThread(username);
        messageAll("Server: "+username+" has joined.");
    }
    
    /**Removes the specific user from the chat room. 
     * @param username Username of who should be removed.
     */
    public void removeUser(String username){
        users.remove(username);
        outs.remove(username);
        socks.remove(username);
        threads.remove(username);
        keys.remove(username);
        messageAll("Server: "+username+" has left.");
    }
    
    /**Used by client to determine if a username can be chosen.
     * @return The usernames of everyone in the chat room concatinated together. 
     */
    public String getUsers(){
        String result = "";
        result = users.stream().map((user) -> user+";").reduce(result, String::concat);
        return result;
    }
}
