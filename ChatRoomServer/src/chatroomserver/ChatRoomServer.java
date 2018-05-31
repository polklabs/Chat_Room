package chatroomserver;

/**
 * General Imports
 */
import java.net.*;
import java.io.*;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.maxmind.geoip2.*;
import com.maxmind.geoip2.exception.GeoIp2Exception;

/**
 * Main class, listens for incoming connections and sets up the new user.
 * @author Andrew Polk
 * @version  0.1
 */
public class ChatRoomServer {

    /***************************************************************************
    * Public variables
    ***************************************************************************/
    
    /**Dictionary of all the chat rooms*/
    public static Map<String, ChatRoom> rooms = new HashMap<>();
    
    public static Map<String, Map<String, ArrayList<String>>> locals = new HashMap<>();
    
    public static dataEncrypt DE;
    
    public static DatabaseReader dbReader;
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    private static final    String          SERVER_IP   = "pi1.polklabs.com";
    //private static final    String          SERVER_IP = "192.168.0.16";
    private static final    int             SERVER_PORT = 3301;
    private static final    int             MAX = 251;
    private static final    int             MAX_POPULAR = 10;
    private static          ServerSocket    serverSock;
    private static boolean  error;
    
    /***************************************************************************
    * Main
    ***************************************************************************/
    
    /**Waits for connections and creates a new thread when one is received to set up a new user.
     * @param args Initial arguments. currently none.
     */
    public static void main(String[] args){
        //Generate Public Private key pair
        DE = new dataEncrypt(1024);
        
        File database = new File("/home/pi/Downloads/GeoLite2-City.mmdb");
        
        try{
            dbReader = new DatabaseReader.Builder(database).build();
            System.out.println("Server started on: "+InetAddress.getByName(SERVER_IP)+":"+Integer.toString(SERVER_PORT));
        }catch(IOException e){
            e.printStackTrace();
        }
        
        while(true){
            try{
                //serverSock = new ServerSocket(SERVER_PORT);
                serverSock = new ServerSocket(SERVER_PORT, 0, InetAddress.getByName(SERVER_IP));

                error = false;
                while(!error){
                    Socket sock = serverSock.accept();
                    //New thread for a new user - multiple users at a time
                    (new Thread() {
                        @Override
                        public void run(){
                            try{
                                newUser(sock);
                            }catch(Exception e){
                                System.out.println("::Failed to add new user. "+e);
                                e.printStackTrace();
                                error = true;
                            }
                        }
                    }).start();
                }
                serverSock.close();
            }catch(IOException e){
                System.out.println("Server sock error. "+e);
            }
        }
    }
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
    
    /**Runs in separate thread to allow multiple users to be added at once.
     * Gets room name, creates new room if needed, gets username. Adds to chat room.
     * @param sock The socket of the new user.
     * @throws IOException Don't they all?
     */
    public static void newUser(Socket sock) throws Exception {
        String city = "";
        String state = "";
        try{
            state = dbReader.city(sock.getInetAddress()).getSubdivisions().get(0).getName();
            city = dbReader.city(sock.getInetAddress()).getCity().getName();
        }catch(GeoIp2Exception | IOException e){
            e.printStackTrace();
        }
        
        System.out.println(city);
        
        DataOutputStream out = new DataOutputStream(sock.getOutputStream());
        DataInputStream in = new DataInputStream(sock.getInputStream());

        //Send Public key to user, get Public key from user
        String publicKey = DE.getPublicKey();
        out.writeUTF(publicKey);
        String clientPublicKey = in.readUTF();
        PublicKey pubKey = dataEncrypt.stringToPublicKey(clientPublicKey);
        
        // ALL MESSAGE FROM HERE ON ARE ENCRYPTED ------------------------------
        //----------------------------------------------------------------------
        
        String popRooms = "";
        
        Comparator<Entry<String, ChatRoom>> valueComparator = (Entry<String, ChatRoom> e1, Entry<String, ChatRoom> e2) -> {
            int v1 = e1.getValue().users.size()-1;
            int v2 = e2.getValue().users.size()-1;
            return v2 - v1;
        };
        Set<Entry<String, ChatRoom>> entries = rooms.entrySet();
        ArrayList<Entry<String, ChatRoom>> listOfEntries = new ArrayList<>(entries);
        Collections.sort(listOfEntries, valueComparator);
        int i = 0;
        for(Entry<String, ChatRoom> pair : listOfEntries){
            if(!rooms.get(pair.getKey()).isUnlisted){
                String lock = "O";
                if(rooms.get(pair.getKey()).hasPassword)
                    lock = "L";
                popRooms += pair.getKey()+" "+(pair.getValue().users.size()-1)+" "+lock+";";
                i++;
            }
            if(i >= MAX_POPULAR){
                break;
            }
        }
        if(popRooms.length() > 0)
            popRooms = popRooms.substring(0, popRooms.length()-1);
        
        //Get list of local rooms
        popRooms += "/";
        if(locals.containsKey(state) && locals.get(state).containsKey(city)){
            for(String s : locals.get(state).get(city)){
                System.out.println("Room name: "+s);
                String lock = "O";
                if(rooms.get(s).hasPassword)
                    lock = "L";
                popRooms += s+" "+(rooms.get(s).users.size()-1)+" "+lock+";";
            }
            if(locals.get(state).get(city).size() > 0)
                popRooms = popRooms.substring(0, popRooms.length()-1);
        }
        out.writeUTF(DE.encryptText(popRooms, pubKey));
        
        //Get room or new room
        String room;
        while(true){
            room = DE.decryptText(in.readUTF(), pubKey);
            if(rooms.containsKey(room)){
                //Gets room thread and adds user to it
                out.writeUTF(DE.encryptText("ACK", pubKey));
                
                //Lock out if locality doesnt match
                if(rooms.get(room).isLocal){
                    if(!state.equals(rooms.get(room).state) || !city.equals(rooms.get(room).city)){
                        out.writeUTF(DE.encryptText("NO", pubKey));
                    }
                } 
                //Ask for password if needed. Get hash from user, check against saved hash.
                if(rooms.get(room).hasPassword){
                    out.writeUTF(DE.encryptText("PASSWORD", pubKey));
                    while(true){
                        String password = DE.decryptText(in.readUTF(), pubKey);
                        
                        if(password.equals(rooms.get(room).passwordHash)){
                            out.writeUTF(DE.encryptText("ACK", pubKey));
                            break;
                        }
                        out.writeUTF(DE.encryptText("NACK", pubKey));
                    }
                    break;
                }else{
                    out.writeUTF(DE.encryptText("NOPASS", pubKey));
                    break;
                }
            }else{
                //Creates a new room
                out.writeUTF(DE.encryptText("NEW", pubKey));
                //TODO: fix chat room collision problem
                String options = DE.decryptText(in.readUTF(), pubKey);
                
                if(options.substring(0, 3).equals("ACK")){
                    //Get password hash from user
                    String password = DE.decryptText(in.readUTF(), pubKey);
                    
                    ChatRoom newRm = new ChatRoom(room, password);
                    rooms.put(room, newRm);
                    
                    if(options.length() > 3){
                        if(options.charAt(3) == 'Y'){
                            newRm.isUnlisted = true;
                            System.out.println("Unlisted Room");
                        }
                        if(options.charAt(4) == 'Y'){
                            newRm.isLocal = true;
                            if(!locals.containsKey(state)){
                                locals.put(state, new HashMap<>());
                            }
                            if(!locals.get(state).containsKey(city)){
                                locals.get(state).put(city, new ArrayList<>());
                            }
                            locals.get(state).get(city).add(room);
                            newRm.isUnlisted = true;
                            newRm.state = state;
                            newRm.city = city;
                            System.out.println("Local room");
                        }
                    }
                    
                    break;
                }
            }
        }
        
        //TODO: fix username collision problem
        //Kinda fixed by the join method
        out.writeUTF(DE.encryptText(rooms.get(room).getUsers(), pubKey));
        String username = DE.decryptText(in.readUTF(), pubKey);
        
        out.writeUTF(DE.encryptText(rooms.get(room).getUsers(), pubKey));
        out.writeUTF(DE.encryptText(rooms.get(room).getKeys(), pubKey));
        
        if(rooms.get(room).users.size() < MAX){
            rooms.get(room).addUser(username, sock, clientPublicKey);
            if(rooms.get(room).isNew){
                rooms.get(room).start();
            }
        }else{
            sock.close();
        }
    }
    
    /**Removes a chat room from the list of chat rooms.
     * @param name Name of the chat room to be removed.
     */
    public static void removeRoom(String name){
        try{
            locals.get(rooms.get(name).state).get(rooms.get(name).city).remove(name);
        }catch(Exception e){}
        rooms.remove(name);
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
}
