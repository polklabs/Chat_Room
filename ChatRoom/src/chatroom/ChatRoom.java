package chatroom;

import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Main class for chat room client. Sends user to chat room and sends messages.
 * @author polka
 * @version 0.1
 */
public class ChatRoom {

    /**IP address that the client connects to.
     * Must point to IP that the Server is listening on.
     */
    private static final    String IP      = "localhost";
    /**Port number that the client connects to.
     * Must point to port that the server is listening on.
     */
    private static final    int    PORT    = 3301;
    /**Name of the chat room that the client is in*/
    private static          String room;
    /**Username of the client*/
    private static          String username;
    /**Socket that the client is connected to the server with*/
    private static          Socket sock;
    
    /**
     * 
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        //TODO: Generate Private Public key pair

        //Input from user
        BufferedReader user = new BufferedReader(new InputStreamReader(System.in));
        
        while(true){
            connectToServer();

            DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            DataInputStream in = new DataInputStream(sock.getInputStream());

            //TODO: Get Public key from server
            //TODO: Send Public to server
            
            //Enter chat room to create or connect to
            while(true){
                System.out.print("::Enter chatroom name: ");
                room = user.readLine();
                out.writeUTF(room);

                String newR;
                if(!(newR = in.readUTF()).equals("NACK")){
                    if(newR.equals("NEW")){
                        System.out.println("::Chatroom does not exist.");
                        System.out.print("::Create room?(y/n) ");
                        if(user.readLine().toUpperCase().equals("Y")){
                            out.writeUTF("ACK");
                            
                            //TODO: Ask for password from user, hash password, send to server         
                            
                            System.out.println("::Chatroom created.");
                            break;
                        }else{
                            out.writeUTF("NACK");
                        }
                    }else{
                        //TODO: Ask for password from user, check hash against hash on server
                        break;
                    }
                }
                //System.out.println("::Not a valid chatroom.");
            }

            //Set of all usernames currently in the chat room, Set avoids duplicate name errors
            Set<String> usernames = new HashSet<String>(Arrays.asList(in.readUTF().split(";")));
            
            //Enter username
            while(true){
                System.out.print("::Enter a username: ");
                username = user.readLine();
                if(!usernames.contains(username)){
                    break;
                }
                System.out.println("::Username taken.");
            }
            //Send username
            out.writeUTF(username);

            startClient();
        }
    }
    
    /**
     * Attempts to connect to the server.
     */
    private static void connectToServer(){
        //Connect to server
        try{
            sock = new Socket(IP, PORT);
        }catch(UnknownHostException e){
            System.out.println("Unknown server host. "+e);
            System.exit(1);
        }catch(IOException e){
            System.out.println("Could not connect to server. "+e);
            System.exit(1);
        }
    }
    
    /**Starts a listener thread then waits for input from the user.
     * @throws Exception DUH..
     */
    private static void startClient() throws Exception{
        //TODO: Get public keys from all users in the chat room
        
        Thread listener = new Thread(new client(sock));
        listener.start();
       
        System.out.println("::Type \"/close\" to close connection.\n");
        
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        DataOutputStream outToServer = new DataOutputStream(sock.getOutputStream());

        while(true) {
            String send = inFromUser.readLine();
            if(send.equals("/close")) break;
            if(!send.equals("")){
                try{
                    outToServer.writeUTF(send);
                    outToServer.flush();
                }catch(IOException e){
                    System.out.println("::Could not send message.");
                }
            }
        }
        sock.close();
    }
}
