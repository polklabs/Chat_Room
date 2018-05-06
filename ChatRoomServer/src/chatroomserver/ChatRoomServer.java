package chatroomserver;

import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Main class, listens for incoming connections and sets up the new user.
 * @author polka
 * @version  0.1
 */
public class ChatRoomServer {

    /**Dictionary of all the chat rooms*/
    public static Map<String, ChatRoom> rooms = new HashMap<>();
    
    /**IP address the server listens on*/
    public static final String  SERVER_IP   = "localhost";
    /**Port the server listens on*/
    public static final int     SERVER_PORT = 3301;
    
    /**Socket that is listening for connections*/
    public static ServerSocket serverSock;

    /**Has an error occurred. Restarts the server socket if true*/
    public static boolean error;
    
    /**Waits for connections and creates a new thread when one is received to set up a new user.
     * @param args Initial arguments. currently none.
     */
    public static void main(String[] args){
        //TODO: Generate Public Private key pair
        
        while(true){
            try{
                serverSock = new ServerSocket(SERVER_PORT);

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
                            }catch(IOException e){
                                System.out.println("::Failed to add new user.");
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
    
    /**Runs in separate thread to allow multiple users to be added at once.
     * Gets room name, creates new room if needed, gets username. Adds to chat room.
     * @param sock The socket of the new user.
     * @throws IOException Don't they all?
     */
    public static void newUser(Socket sock) throws IOException{
        DataOutputStream out = new DataOutputStream(sock.getOutputStream());
        DataInputStream in = new DataInputStream(sock.getInputStream());

        //TODO: Send Public key to user
        //TODO: get Public key from user
        
        //Get room or new room
        String room;
        while(true){
            room = in.readUTF();
            if(rooms.containsKey(room)){
                //Gets room thread and adds user to it
                out.writeUTF("ACK");
                
                //TODO: Ask for password if needed. Get hash from user, check against saved hash.
                
                break;
            }else{
                //Creates a new room
                out.writeUTF("NEW");
                if(!in.readUTF().equals("NACK")){
                    ChatRoom newRm = new ChatRoom(room);
                    rooms.put(room, newRm);
                    
                    //TODO: Get password hash from user
                    
                    break;
                }
            }
        }
        
        out.writeUTF(rooms.get(room).getUsers());
        String username = in.readUTF();
        
        rooms.get(room).addUser(username, sock);
        if(rooms.get(room).users.size() == 1){
            rooms.get(room).start();
        }
    }
    
    /**Removes a chat room from the list of chat rooms.
     * @param name Name of the chat room to be removed.
     */
    public static void removeRoom(String name){
        rooms.remove(name);
    }
    
}
