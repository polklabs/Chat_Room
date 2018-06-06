/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chipserver;

import java.io.DataInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import org.json.*;

/**
 *
 * @author polka
 */
public class User extends Thread{
    
    public boolean kicked = false;
    public boolean mod = false;
    
    private ChatRoom room;
    private DataInputStream in;
    private String username;
    
    public User(ChatRoom chatRoom, String username) throws IOException{
        this.room = chatRoom;
        this.username = username;
        this.in = new DataInputStream(chatRoom.socks.get(username).getInputStream());
    }
    
    @Override
    public void run(){
        while(true){
            try{
                if(kicked)break;
                
                int lengthData = in.readInt();
                if(kicked)break;
                byte[] b = new byte[lengthData];
                in.readFully(b);
                if(kicked)break;
                JSONObject data = new JSONObject(new String(b, "UTF-8"));
                
                switch(data.getString("type")){
                    case "message":
                        message(data.getJSONObject("messages"));
                        break;
                    case "data":
                        image(data.getString("messages"));
                        break;
                    case "command":
                        command(data.getString("data"));
                        break;
                    default:
                        System.out.println(room.roomName+"::Message from "+username+", "+data.getString("type"));
                }
                
            }catch(IOException e){
                room.removeUser(username);
                break;
            }
        }
        
        try{
            room.socks.get(username).close();
        }catch(IOException e){
        }catch(NullPointerException e){
        }
    }
    
    public void message(JSONObject data){
        for(String s : data.keySet()){
            JSONObject obj = new JSONObject();
            obj.put("type", "message");
            obj.put("message", data.getString(s));
            room.send(obj.toString(), s);
        }
    }
    
    public void image(String data){
        JSONObject obj = new JSONObject();
        obj.put("type", "image");
        obj.put("sender", username);
        obj.put("body", data);
        
        for(String s : room.users){
            if(!s.equals("Server") && !s.equals(username)){
                try{
                    JSONObject message = new JSONObject();
                    message.put("type", "message");
                    message.put("message", room.DE.encryptOnce(obj.toString(), room.keys.get(s), false));
                    room.send(message.toString(), s);
                }catch(InvalidKeyException | IllegalBlockSizeException | BadPaddingException e){
                    System.out.println(e);
                }
            }
        }
    }
    
    public void command(String data){
        switch(data.substring(0, 5)){
            case "close":
                room.removeUser(username);
                kicked = true;
                break;
            case "kick ":
                int x = data.lastIndexOf(" ");
                String kickUser = data.substring(x+1);
                if(room.users.contains(kickUser)){
                    if(mod && !kickUser.equals(username) && !kickUser.equals("Server"))
                        room.kickUser(kickUser);
                    else
                        room.messageOne("\""+username+"\" has reported \""+kickUser+"\".", room.users.get(1));
                }
        }
    }
}
