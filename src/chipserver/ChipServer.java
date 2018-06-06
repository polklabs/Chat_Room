/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chipserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import org.json.*;

/**
 *
 * @author polka
 */
public class ChipServer {
    
    private static final String IP = "pi1.polklabs.com";
    //private static final String IP = "169.231.10.204";
    private static final int PORT = 3301;
    private static final int MAX_PER_ROOM = 251;
    private static final int MAX_POPULAR = 10;
    
    private static final Map<String, ChatRoom> ROOMS = new HashMap<>();
    private static final ArrayList<String> SORTED_POPULAR = new ArrayList<>();
    private static final Map<String, Map<String, ArrayList<String>>> LOCALS = new HashMap<>();
    public static dataEncrypt DE;
    private static ServerSocket serverSock;
    private static boolean restart = false;
    
    private static Thread roomManager;
    
    public static ReentrantLock lock;
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            init();
            run();
        } catch (UnknownHostException ex) {
            System.out.println(ex);
        }
    }
    
    public static void init() throws UnknownHostException{
        DE = new dataEncrypt(512);
        lock = new ReentrantLock();
        System.out.println("Server started on: "+IP+", "+InetAddress.getByName(IP)+":"+Integer.toString(PORT));
        
        roomManager = new Thread(){
            @Override
            public void run(){
                while(true){
                    try{
                        TimeUnit.SECONDS.sleep(30);
                        
                        lock.lock();
                        try{
                            boolean change = false;
                            Iterator<Map.Entry<String, ChatRoom>> itr = ROOMS.entrySet().iterator();
                            while(itr.hasNext()){
                                Map.Entry<String, ChatRoom> pair = itr.next();
                                if(pair.getValue().users.size() <= 1){
                                    try{
                                        LOCALS.get(pair.getValue().state).get(pair.getValue().city).remove(pair.getKey());
                                    }catch(Exception e){}
                                    itr.remove();
                                    System.out.println(pair.getKey()+"::Closed");
                                    change = true;
                                }
                            }
                            if(change) ChipServer.sortPopular();
                        }catch(Exception e){
                            System.out.println(e);
                        }finally{
                            lock.unlock();
                        }
                        
                    } catch (InterruptedException ex) {
                        System.out.println(ex);
                    }
                }
            }
        };
        
        roomManager.start();
    }
    
    public static void run(){
        while(true){
            try{
                serverSock = new ServerSocket(PORT, 0, InetAddress.getByName(IP));
                
                restart = false;
                while(!restart){
                    Socket sock = serverSock.accept();
                    
                    (new Thread() {
                        @Override
                        public void run(){
                            try{
                                newUser(sock);
                            }catch(IOException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | BadPaddingException | IllegalBlockSizeException e){
                                System.out.println(e);
                                e.printStackTrace();
                                try{
                                    sock.close();
                                }catch(Exception ex){}
                                restart = true;
                            }
                        }
                    }).start();
                }
                serverSock.close();
            } catch (UnknownHostException ex) {
                System.out.println(ex);
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
    }
    
    public static void newUser(Socket sock) 
            throws BadPaddingException, InvalidKeyException, 
            IllegalBlockSizeException, IOException, NoSuchAlgorithmException, 
            InvalidKeySpecException{
        
        String city;
        String state;
        PublicKey clientPublicKey;
        
        JSONObject result = new JSONObject();
        
        DataOutputStream out = new DataOutputStream(sock.getOutputStream());
        DataInputStream in = new DataInputStream(sock.getInputStream());
        
        //Public key exchange
        clientPublicKey = dataEncrypt.stringToPublicKey(in.readUTF());
        out.writeUTF(DE.getPublicKey());
        
        int length = in.readInt();
        byte[] b = new byte[length];
        in.readFully(b);
        JSONObject data = new JSONObject(DE.decryptText(new String(b, "UTF-8"), clientPublicKey));
        
        city = data.getString("locationCity");
        state = data.getString("locationState");
        
        lock.lock();
        try{
            if(data.getString("type").equals("join")){
                //Join the room
                result.put("type", "join");
                
                String roomName = data.getString("name");
                ChatRoom room = ROOMS.get(roomName);
                boolean adding = false;
                
                if(ROOMS.containsKey(roomName)){
                    if((room.hasPassword && room.passwordHash.equals(data.getString("password"))) || !room.hasPassword){
                        if(!room.users.contains(data.getString("username"))){
                            if(room.users.size() < MAX_PER_ROOM){
                                if(room.isLocal){
                                    if(room.city.equals(city) && room.state.equals(state)){
                                        adding = true;
                                        result.put("success", true);
                                        result.put("error", 0);
                                    }else{
                                        result.put("success", false);
                                        result.put("error", 3);
                                    }
                                }else{
                                    adding = true;
                                    result.put("success", true);
                                    result.put("error", 0);
                                }
                            }else{
                                result.put("success", false);
                                result.put("error", 6);
                            }
                        }else{
                            result.put("success", false);
                            result.put("error", 4);    
                        }
                    }else{
                        result.put("success", false);
                        result.put("error", 2);
                    }
                }else{
                    room = new ChatRoom(data.getString("name"), data.getString("password"), state, city, data.getBoolean("unlisted"));
                    if(data.getBoolean("local")){
                        if(!city.equals("") && !state.equals("")){
                            room.isLocal = true;
                            room.unlisted = true;
                            
                            if(!LOCALS.containsKey(state))LOCALS.put(state, new HashMap<>());
                            if(!LOCALS.get(state).containsKey(city))LOCALS.get(state).put(city, new ArrayList<>());
                            
                            LOCALS.get(state).get(city).add(data.getString("name"));
                        }
                    }
                    if(!room.users.contains(data.getString("username"))){
                        ROOMS.put(data.getString("name"), room);
                        adding = true;
                        result.put("success", true);
                        result.put("error", 0);
                    }else{
                        result.put("success", false);
                        result.put("error", 4);
                    }
                }

                b = DE.encryptText(result.toString(), clientPublicKey).getBytes();
                out.writeInt(b.length);
                out.write(b);
                out.flush();
                
                if(adding) room.addUser(data.getString("username"), sock, clientPublicKey);
            }else{
                //Send the init data
                result.put("type", "init");

                ArrayList<JSONObject> roomListTemp = new ArrayList<>();
                for(String s : SORTED_POPULAR){
                    JSONObject temp = new JSONObject();
                    temp.put("name", s);
                    temp.put("lock", ROOMS.get(s).hasPassword);
                    temp.put("size", ROOMS.get(s).users.size()-1);
                    roomListTemp.add(temp);
                }
                result.put("popularRooms", roomListTemp);

                roomListTemp = new ArrayList<>();
                if(LOCALS.containsKey(state) && LOCALS.get(state).containsKey(city)){
                    for(String s : LOCALS.get(state).get(city)){
                        JSONObject temp = new JSONObject();
                        temp.put("name", s);
                        temp.put("lock", ROOMS.get(s).hasPassword);
                        temp.put("size", ROOMS.get(s).users.size()-1);
                        roomListTemp.add(temp);
                    }
                }
                result.put("localRooms", roomListTemp);
                
                b = DE.encryptText(result.toString(), clientPublicKey).getBytes();
                out.writeInt(b.length);
                out.write(b);
                out.flush();
                
                sock.close();
            }
        }finally{
            lock.unlock();
        }
    }
    
    public static void sortPopular(){
        Comparator<Entry<String, ChatRoom>> valueComparator = (Entry<String, ChatRoom> e1, Entry<String, ChatRoom> e2) -> {
            int v1 = e1.getValue().users.size()-1;
            int v2 = e2.getValue().users.size()-1;
            return v2 - v1;
        };
        
        lock.lock();
        try{
            Set<Entry<String, ChatRoom>> entries = ROOMS.entrySet();
            ArrayList<Entry<String, ChatRoom>> listOfEntries = new ArrayList<>(entries);
            Collections.sort(listOfEntries, valueComparator);
            int i = 0;
            SORTED_POPULAR.clear();
            for(Entry<String, ChatRoom> pair : listOfEntries){
                if(!pair.getValue().unlisted){
                    SORTED_POPULAR.add(pair.getKey());
                }
                i++;
                if(i > MAX_POPULAR){
                    break;
                }
            }
        }finally{
            lock.unlock();
        }
    }
    
}
