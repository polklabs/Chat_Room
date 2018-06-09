/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chipserver;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import org.json.*;

/**
 *
 * @author polka
 */
public class ChatRoom {
    public String roomName;
    public String passwordHash;
    public boolean unlisted = false;
    public boolean isLocal = false;
    public boolean hasPassword = false;
    
    public String state = "";
    public String city = "";
    
    public ArrayList<String> users = new ArrayList<>();
    public Map<String, Socket> socks = new HashMap<>();
    public Map<String, DataOutputStream> outs = new HashMap<>();
    public Map<String, PublicKey> keys = new HashMap<>();
    public Map<String, ReentrantLock> locks = new HashMap<>();
    public Map<String, User> threads = new HashMap<>();
    
    public dataEncrypt DE;
    
    public ChatRoom(String name, String password, String state, String city, boolean unlisted){
        this.roomName = name;
        this.passwordHash = password;
        this.state = state;
        this.city = city;
        this.unlisted = unlisted;
        
        if(!password.equals("")){
            hasPassword = true;
        }
        
        keys.put("Server", ChipServer.DE.publicKey);
        users.add("Server");
        DE = ChipServer.DE;
        
        System.out.println(roomName+"::Creating room.");
    }
    
    public void send(String data, String to){
        if(to.equals(users.get(0)))return;
        
        byte[] b = data.getBytes();

        locks.get(to).lock();
        try{
            outs.get(to).writeInt(b.length);
            outs.get(to).write(b);
            outs.get(to).flush();
        } catch (IOException ex) {
            removeUser(to);
            System.out.println(ex);
        }finally{
            locks.get(to).unlock();
        }
    }
    
    public void messageAll(String data, String from){
        users.forEach((s) -> {
            messageOne(data, s);
        });
    }
    
    public void messageOne(String data, String to) {
        if(to.equals(users.get(0)))return;
        
        String from  = users.get(0);
        try{
            JSONObject message = new JSONObject();
            message.put("type", "text");
            message.put("private", false);
            message.put("sender", from);

            String body = DE.encryptOnce(data, null, true); 
            message.put("body", body);

            JSONObject outer = new JSONObject();
            outer.put("type", "message");
            outer.put("message", DE.encryptOnce(message.toString(), keys.get(to), false));
            
            send(outer.toString(), to);
        
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
            System.out.println(ex);
        }
    }
    
    public void updateKeys() 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, InvalidKeySpecException {
        
        JSONObject data = new JSONObject();
        data.put("type", "keys");
        data.put("users", users);
        
        JSONObject k = new JSONObject();
        for(Entry<String, PublicKey> pair : keys.entrySet()){
            k.put(pair.getKey(), dataEncrypt.PublicKeyToString(pair.getValue()));
        }
        data.put("keys", k);
        
        String message = data.toString();
        
        users.forEach((s) -> {
            send(message, s);
        });
    }
    
    public void addUser(String username, Socket sock, PublicKey key){
        System.out.println(roomName+"::Adding "+username);
        try{
            messageAll("\""+username+"\" has joined.", users.get(0));

            users.add(username);
            socks.put(username, sock);
            keys.put(username, key);
            outs.put(username, new DataOutputStream(sock.getOutputStream()));
            locks.put(username, new ReentrantLock());

            try{
                updateKeys();
            }catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | InvalidKeySpecException e){
                System.out.println(e);
            }

            newClientThread(username);

            messageOne("Welcome to \""+roomName+"\"!", username);

            if(username.equals(users.get(1))){
                messageOne("You are the Moderator.", username);
                threads.get(username).mod = true;
            }
            messageOne("Open the sidebar to send private messages and to kick/report users in the chat room.", username);
            
        }catch(IOException e){
            removeUser(username);
        }
        ChipServer.sortPopular();
    }
    
    public void removeUser(String username){
        System.out.println(roomName+"::Removing "+username);
        boolean isMod = users.indexOf(username)==1;
        
        users.remove(username);
        outs.remove(username);
        socks.remove(username);
        keys.remove(username);
        threads.remove(username);
        locks.remove(username);
        
        messageAll("\""+username+"\" has left.", users.get(0));
        
        if(isMod && users.size() > 1){
            messageOne("You are the new Moderator.", users.get(1));
            threads.get(users.get(1)).mod = true;
        }
            
        try{
            updateKeys();
        }catch(InvalidKeyException e){} catch (IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            System.out.println(roomName+"::Failed to update user keys.");
        }
        ChipServer.sortPopular();
    }
    
    public void kickUser(String username){
        System.out.println(roomName+"::Kicking "+username);
        threads.get(username).kicked = true;
        
        messageOne("You have been kicked from this chat room.", username);
        
        JSONObject data = new JSONObject();
        data.put("type", "command");
        data.put("data", "kick");
        send(data.toString(), username);

        users.remove(username);
        outs.remove(username);
        socks.remove(username);
        keys.remove(username);
        threads.remove(username);
        locks.remove(username);
        
        messageAll("\""+username+"\" has been kicked.", users.get(0));
        
        try{
            updateKeys();
        }catch(InvalidKeyException e){} catch (IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            System.out.println(roomName+"::Failed to update user keys.");
        }
        ChipServer.sortPopular();
    }
    
    private void newClientThread(String username) throws IOException{
        threads.put(username, new User(this, username));
        threads.get(username).start();
    }
}
