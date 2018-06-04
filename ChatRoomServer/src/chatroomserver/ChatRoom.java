package chatroomserver;

/**
 * General Imports
 */
import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * This class handles a specific chat room and its users.
 * @author Andrew Polk
 * @version 0.1
 */
public class ChatRoom {
    
    /***************************************************************************
    * Public variables
    ***************************************************************************/
    
    /**Name of the chat room*/ 
    public    String name;
    /**List of all the usernames in the chat room*/
    public final    ArrayList<String> users = new ArrayList<>();
    /**Dictionary of all the sockets*/
    public final    Map<String, Socket> socks = new HashMap<>();
    /**Dictionary of all the output streams for all the users*/
    public final    Map<String, DataOutputStream> outs = new HashMap<>();
    /**Has the chat room thread been run yet.*/
    public boolean  isNew = true;
    
    //Encrypted Chat Room
    /**Dictionary for the pubic keys of all the users*/
    public final    Map<String, PublicKey> keys = new HashMap<>();
    public          dataEncrypt DE;
    
    //Passworded chat room
    /**If the chat room uses a password to enter*/
    public boolean  hasPassword = false;
    /**The hash of the password to allow new people to enter*/
    public String   passwordHash;
    
    public boolean isUnlisted = false;
    public boolean isLocal = false;
    public String state = "";
    public String city = "";
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    //Holds the listener thread for each user
    private final Map<String, User> threads = new HashMap<>();
    
    /***************************************************************************
    * Constructor
    ***************************************************************************/
    
    /**
     * Creates a new chat room thread and sets its name.
     * @param roomName The name of the chat room.
     * @param password password for the chat room, "" for no password
     */
    public ChatRoom(String roomName, String password){
        name = roomName;
        passwordHash = password;
        
        if(!password.equals("")){
            hasPassword = true;
        }
        keys.put("Server", ChatRoomServer.DE.publicKey);
        users.add("Server");
        DE = ChatRoomServer.DE;
        
        System.out.println(name+"::Creating room.");
    }
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
    
    /**
     * Sends a message to all users in the chat room.
     * @param message The message that you want to send.
     * @param print if the server should print the message to the log
     */
    public void messageAll(String message, boolean print){
        if(print){
            System.out.println(name+"::"+message);
        }
        users.forEach((user) -> {
            try{
                if(!user.equals("Server")){
                    byte[] s = DE.encryptText(message, keys.get(user)).getBytes();
                    outs.get(user).writeInt(s.length);
                    outs.get(user).write(s);
                    outs.get(user).flush();
                }
            }catch(IOException e){
                System.out.println(e);
                removeUser(user);
            }catch(InvalidKeyException | BadPaddingException | IllegalBlockSizeException e){
                System.out.println(e);
            }
        });
    }
    
    /**
     * Sends a message to a specific users in the chat room.
     * @param message
     * @param user 
     */
    public void messageOne(String message, String user){
        try{
            if(!user.equals("Server")){
                byte[] s = DE.encryptText(message, keys.get(user)).getBytes();
                outs.get(user).writeInt(s.length);
                outs.get(user).write(s);
                outs.get(user).flush();
            }
        }catch(IOException e){
            removeUser(user);
        }catch(InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            System.out.println(e);
        }
    }
    
    /**Adds a new user to the chat room.
     * @param username Username for the new user.
     * @param newSock The socket of the new user.
     * @param publicKey public key for the user being added
     */
    public void addUser(String username, Socket newSock, String publicKey) {
        try{
            messageAll("Server: \""+username+"\" has joined.", true);
            outs.put(username, new DataOutputStream(newSock.getOutputStream()));
            socks.put(username, newSock);
            users.add(username);
            keys.put(username, dataEncrypt.stringToPublicKey(publicKey));
            newClientThread(username);
            messageOne("Server: Welcome to \""+name+"\"", username);
            
            //Message all new userList and keys
            messageAll("Server:/*"+getUsers(), false);
           try{
               messageAll("Server:"+getKeys(), false);
            }catch(NoSuchAlgorithmException | InvalidKeySpecException e){
                messageAll("Server:*", false);
            }
            
            if(username.equals(users.get(1))){
                messageOne("Server: You are the Moderator. Type \"/kick \'username\'\" to kick a user out of the chatroom.", username);
            }
            
        }catch(IOException e){
            if(outs.containsKey(username)){
                outs.remove(username);
            }
            System.out.println(e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            System.out.println(ex);
        }
    }
    
    /**Removes the specific user from the chat room. 
     * @param username Username of who should be removed.
     */
    public void removeUser(String username){
        boolean mod = users.indexOf(username)==1;
        users.remove(username);
        outs.remove(username);
        socks.remove(username);
        threads.remove(username);
        keys.remove(username);
        
        System.out.println(Thread.currentThread().getStackTrace()[2].getLineNumber());
        
        messageAll("Server: \""+username+"\" has left.", true);
        
        if(mod && users.size() >= 2)
            messageOne("Server: You are the new Moderator. Type \"/kick \'username\'\" to kick a user out of the chatroom.", users.get(1));
        
        messageAll("Server:/*"+getUsers(), false);
        try{
            messageAll("Server:"+getKeys(), false);
        }catch(NoSuchAlgorithmException | InvalidKeySpecException e){
            messageAll("Server:*", false);
        }
    }
    
    
    /**
     * Kicks a user out of the chat room, can only be called by a moderator. 
     * I.E. user 1 in the chat room
     * @param username 
     */
    public void kickUser(String username){
        if(username.equals(users.get(1))){
            messageOne("Server: You cannot kick yourself.", users.get(1));
            return;
        }
        
        threads.get(username).kicked = true;
        
        messageOne("Server: You have been kicked from this chat room.", username);
        messageOne("Server:/-", username);
        
        users.remove(username);
        outs.remove(username);
        socks.remove(username);
        threads.remove(username);
        keys.remove(username);
        messageAll("Server: \""+username+"\" has been kicked.", true);
        
        //Send updated keys
        messageAll("Server:/*"+getUsers(), false);
        try{
            messageAll("Server:"+getKeys(), false);
        }catch(NoSuchAlgorithmException | InvalidKeySpecException e){
            messageAll("Server:*", false);
        }
    }
    
    /**
     * Returns a string with all the users in a chat room separated by ';'
     * @return The usernames of everyone in the chat room concatinated together. 
     */
    public String getUsers(){
        String result = "";
        result = users.stream().map((user) -> user+";").reduce(result, String::concat);
        result = result.substring(0, result.length()-1);
        return result;
    }
    
    /**
     * Returns string of all public keys in the chat room
     * @return String of all public keys, no delimiters 
     * @throws java.security.NoSuchAlgorithmException 
     * @throws java.security.spec.InvalidKeySpecException 
     */
    public String getKeys() 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        String result = "";
        for(int i = 0; i < users.size(); i++){
            result += dataEncrypt.PublicKeyToString(keys.get(users.get(i)));
        }
        return result;
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
    
    /**Creates a new thread to listen for massages from a specific client.*/
    private void newClientThread(String username){
        threads.put(username, new User(this, username));
        threads.get(username).start();
    }
}
