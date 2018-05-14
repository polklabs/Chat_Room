package chatroomserver;

/**
 * General Imports
 */
import java.net.*;
import java.io.*;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    
    public static Map<String, Integer> popular = new HashMap<>();
    public static ArrayList<String> popularNames = new ArrayList<>();
    
    public static dataEncrypt DE;
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    private static final    String          SERVER_IP   = "pi1.polklabs.com";
    private static final    int             SERVER_PORT = 3301;
    private static final    int             MAX_IN_POPULAR = 10;
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
        
        while(true){
            try{
                serverSock = new ServerSocket(SERVER_PORT, 0, InetAddress.getByName(SERVER_IP));

                System.out.println("Server started on: "+serverSock.getInetAddress()+":"+Integer.toString(serverSock.getLocalPort()));

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
    public static void newUser(Socket sock) throws Exception{
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
        for(String s : popularNames){
            popRooms += s+" "+popular.get(s)+";";
        }
        if(popRooms.length() > 0)
            popRooms = popRooms.substring(0, popRooms.length()-1);
        
        out.writeUTF(DE.encryptText(popRooms, pubKey));
        
        //Get room or new room
        String room;
        while(true){
            room = DE.decryptText(in.readUTF(), pubKey);
            if(rooms.containsKey(room)){
                //Gets room thread and adds user to it
                out.writeUTF(DE.encryptText("ACK", pubKey));
                
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
                if(DE.decryptText(in.readUTF(), pubKey).equals("ACK")){
                    //Get password hash from user
                    String password = DE.decryptText(in.readUTF(), pubKey);
                    
                    ChatRoom newRm = new ChatRoom(room, password);
                    rooms.put(room, newRm);
                    
                    break;
                }
            }
        }
        
        //TODO: fix username collision problem
        out.writeUTF(DE.encryptText(rooms.get(room).getUsers(), pubKey));
        String username = DE.decryptText(in.readUTF(), pubKey);
        
        out.writeUTF(DE.encryptText(rooms.get(room).getUsers(), pubKey));
        out.writeUTF(DE.encryptText(rooms.get(room).getKeys(), pubKey));
        
        rooms.get(room).addUser(username, sock, clientPublicKey);
        if(rooms.get(room).isNew){
            rooms.get(room).start();
        }
        
        int size = rooms.get(room).users.size();
        
        for(int i = 0; i < MAX_IN_POPULAR; i++){
            if(i >= popularNames.size()){
                popularNames.add(room);
                popular.put(room, size);
                break;
            }
            
            String name = popularNames.get(i);
            int pos = popularNames.indexOf(name);
            if(size > popular.get(name)){
                popularNames.add(pos, room);
                popular.put(room, size);
                break;
            }
        }
        
        while(popularNames.size() >= MAX_IN_POPULAR){
            popular.remove(popularNames.get(popularNames.size()));
            popularNames.remove(popularNames.size());
        }
    }
    
    /**Removes a chat room from the list of chat rooms.
     * @param name Name of the chat room to be removed.
     */
    public static void removeRoom(String name){
        rooms.remove(name);
        popular.remove(name);
        popularNames.remove(name);
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
}
