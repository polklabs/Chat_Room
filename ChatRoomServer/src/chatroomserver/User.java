package chatroomserver;

import java.io.*;

/**
 * Simple thread to get massage from user asynch and send it to all other users in the chat room.
 * @author polka
 * @version 0.1
 */
public class User extends Thread{
    /**Parent chat room thread*/
    private ChatRoom room;
    /**Username of attached user*/
    private String username;
    /**Input from current user*/
    private DataInputStream in;
    
    /**
     * Constructor for listener thread
     * @param room Parent thread
     * @param username Name of user.
     */
    public User(ChatRoom room, String username){
        this.room = room;
        this.username = username;
        
        try{
            in = new DataInputStream(room.socks.get(username).getInputStream());
        }catch(IOException e){
            room.removeUser(username);
        }
    }
    
    /**
     * Main thread method.
     * Waits for input from user and sends it back to all other users
     * Removes user from chat room if connection is closed or message sent is NULL.
     */
    @Override
    public void run(){
        try{
            String message;
            while(true){
                //String m1 = in.readLine();
                String m1 = in.readUTF();
                
                if(m1 == null){
                    room.removeUser(username);
                    return;
                }
                
                message = username+": "+m1+"\n";

                for(String user : room.users){
                    if(!user.equals(username)){
                        try{
                            room.outs.get(user).writeUTF(message);
                        }catch(IOException e){
                            room.removeUser(user);
                        }
                    }
                }
            }
        }catch(IOException e){
            room.removeUser(username);
        }
    }
}
