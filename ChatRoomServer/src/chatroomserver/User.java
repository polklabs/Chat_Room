package chatroomserver;

/**
 * General Imports
 */
import java.io.*;
import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * Simple thread to get massage from user asynch and send it to all other users in the chat room.
 * @author Andrew Polk
 * @version 0.1
 */
public class User extends Thread{
    
    /***************************************************************************
    * Public variables
    ***************************************************************************/
    
    public boolean kicked = false;
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    /**Parent chat room thread*/
    private ChatRoom room;
    /**Username of attached user*/
    private String username;
    /**Input from current user*/
    private DataInputStream in;
    
    /***************************************************************************
    * Constructor
    ***************************************************************************/
    
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
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
    
    /**
     * Main thread method.
     * Waits for input from user and sends it back to all other users
     * Removes user from chat room if connection is closed or message sent is NULL.
     */
    @Override
    public void run(){
        //String message;
        while(true){
            try{
                String message = in.readUTF();
                
                if(kicked)
                    return;
                
                if(message == null){
                    room.removeUser(username);
                    return;
                }
                
                byte[] m1 = room.DE.decryptBytes(message);

                if(m1.length == 173){
                    //For the server
                    serverCommand(m1);
                }else{
                    broadCastData(m1);
                }
            }catch(IOException e){
                room.removeUser(username);
                break;
            } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
                System.out.println(ex);
            }
        }
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
    
    private void serverCommand(byte[] command) 
            throws UnsupportedEncodingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
        
        String mS = new String(room.DE.decrypt((new String(command, "UTF-8")).substring(1), room.keys.get(username)), "UTF-8");
        if(mS.substring(0, 5).equals("/kick")){
            int x = mS.lastIndexOf(" ");
            String kickUser = mS.substring(x+1);
            if(!kickUser.equals("")){
                if(room.users.get(1).equals(username)){
                    if(room.users.indexOf(kickUser) != -1)
                        room.kickUser(kickUser);
                    else
                        room.messageOne("Server: \""+kickUser+"\" not in chat room.", username);
                }else{
                    if(room.users.indexOf(kickUser) != -1)
                        room.messageOne("Server: \""+username+"\" has reported \""+kickUser+"\".", room.users.get(1));
                }
            }
        }
    }
    
    private void broadCastData(byte[] m1) 
            throws UnsupportedEncodingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
        
        for(String user : room.users){
            if(!user.equals(username)){
                try{
                    if(!user.equals("Server")){
                        room.outs.get(user).writeUTF(room.DE.encryptBytes(m1, room.keys.get(user), room.users.indexOf(username)));
                    }
                }catch(IOException e){
                    room.removeUser(user);
                }
            }
        }
    }
    
    /***************************************************************************
    * Static methods
    ***************************************************************************/
}
