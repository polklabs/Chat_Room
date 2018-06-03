package chatroom;

/**
 * General Imports
 */
import java.net.*;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * Main class for chat room client. Sends user to chat room and sends messages.
 * @author Andrew Polk
 * @version 0.1
 */
public class ChatRoom {

    /***************************************************************************
    * Public variables
    ***************************************************************************/
  
    /**Instance of the encryption class*/
    public dataEncrypt DE;
    /**List of the users the client knows are in the chat room*/
    public ArrayList<String> users = new ArrayList<>();
    /**Connection has been closed, start a new one*/
    public boolean closed = false;
    
    public boolean kicked = false;  
    
    public String errorMessage = "";
    public int errorType = 0;
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    private static final      String IP    = "pi1.polklabs.com";
    //private static final    String IP      = "169.231.10.204";   //pi1.polklabs.com
    //private static final    String IP      = "192.168.0.16";
    private static final    int    PORT    = 3301;          //Server port
    private          String room;                    //Room the user joins
    private          String username;                //Users username
    private          String password;
    private          Socket sock;                    //Socket to server
    
    private int setupLevel = 0;//The current state of the connection, forces inorder setup
    
    //Streams
    // TODO Replace inFromUser with input
    private final BufferedReader      inFromUser;
    //private final DataInputStream     input;
    private DataOutputStream    out;
    private DataInputStream     in;
    
    /***************************************************************************
    * Main
    ***************************************************************************/
    
    /**
     * TODO: remove exception throw and add try loop
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        ChatRoom chatRoom = new ChatRoom();
        
        /*Run 1 or 2, not both*/
        //1.
        while(true){
            chatRoom.create();
        }
        
        //2.
        //chatRoom.run();
    }
    
    /***************************************************************************
    * Constructor
    ***************************************************************************/
    
    public ChatRoom(){
        //Generate Private Public key pair
        DE = new dataEncrypt(1024, this);
        //Input from user
        inFromUser = new BufferedReader(new InputStreamReader(System.in));
        //input = null;
    }
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
     
    /**
     * TODO REPLACE THIS IN THE ANDROID APP
     * This is the way that the activity should enter a chat room in general
     * @throws Exception 
     */
    public void create() throws Exception{
        String popular = init();
        System.out.println(popular);
        
        System.out.print("Enter chat room name: ");
        String sRoom = inFromUser.readLine();
        
        System.out.print("Enter chat room password: ");
        String sPassword = inFromUser.readLine();
        
        System.out.print("Enter username: ");
        String sUsername = inFromUser.readLine();
        
        if(join(sRoom, sPassword, sUsername)){
            System.out.println("Success");
            startListener();
            startMessenger();
        }else{
            System.out.println("Failure: "+errorMessage);
        }
    }
    
    /**
     * 
     * @param roomString
     * @param passwordString
     * @param usernameString
     * @return true means user successfully joined room, false means an error occurred
     */
    public boolean join(String roomString, String passwordString, String usernameString){
        //Setup
        room = roomString;
        password = passwordString;
        username = usernameString;
        
        try{ 
            // ALL MESSAGE FROM HERE ON ARE ENCRYPTED --------------------------
            //------------------------------------------------------------------
            
            //Send room name****************************************************
            if(setupLevel != 1){
                errorMessage = "Current SetupLevel: "+setupLevel+", Level 1 required.";
                return false;
            }
            room = room.trim();
            if(!isValid(room)){
                errorMessage = "Invalid room name.";
                return false;
            }
            out.writeUTF(DE.encryptText(room));
            
            //Create new room if needed
            boolean newRm = false;
            if(DE.decryptText(in.readUTF()).equals("NEW")){
                newRm = true;
                out.writeUTF(DE.encryptText("ACK"));
            }
            setupLevel++;
            //******************************************************************
            
            //Send password if needed*******************************************
            if(setupLevel != 2){
                errorMessage = "Current SetupLevel: "+setupLevel+", Level 2 required.";
                return false;
            }
            String passMessage = DE.decryptText(in.readUTF());
            if(newRm || passMessage.equals("PASSWORD")){
                //Send password
                if(password.equals("")){
                    out.writeUTF(DE.encryptText(""));
                }else{
                    out.writeUTF(DE.encryptText(new String(dataEncrypt.getEncryptedPassword(password, room))));
                }
                
                if(!newRm){
                    if(DE.decryptText(in.readUTF()).equals("NACK")){
                        errorMessage = "Wrong password.";
                        return false;
                    }
                }
            }else if(passMessage.equals("NO")){
                sock.close();
                errorMessage = "Trying to enter local chat room.";
                return false;
            }
            setupLevel++;
            //******************************************************************
            
            //Setup username****************************************************
            if(setupLevel != 3){
                errorMessage = "Current SetupLevel: "+setupLevel+", Level 3 required.";
                return false;
            }
            Set<String> usernames = new HashSet<>(Arrays.asList(DE.decryptText(in.readUTF()).split(";")));
            
            //Send username
            username = username.trim();
            if(isValid(username)){
                if(usernames.contains(username) && !username.equals("")){
                    errorMessage = "Username invalid/taken.";
                    return false;
                }
            }else{
                errorMessage = "Username invalid.";
                return false;
            }
            out.writeUTF(DE.encryptText(username));
            setupLevel++;
            //******************************************************************
            
            //Update public keys************************************************
            if(setupLevel != 4){
                errorMessage = "Current SetupLevel: "+setupLevel+", Level 4 required.";
                return false;
            }
            updateKeys(DE.getPublicKey());
            setupLevel++;
            //******************************************************************
            
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            errorMessage = ("Error: "+ex);
            return false;
        }
        
        return true;
    }
    
    public void reportUser(String user){
        try{
            message("/kick "+user);
        }catch(IOException | InvalidKeyException | BadPaddingException | IllegalBlockSizeException e){
            
        }
    }
    
    /**
     * Call to send message/command into chat room
     * @param text message to send
     * @return returns false if the socket was closed or connection has not been setup
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws IOException 
     */
    public boolean message(String text) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, IOException{
        
        if(setupLevel != 5){
            errorMessage = "Current SetupLevel: "+setupLevel+", Level 3 required.";
            return false;
        }
        
        while(!closed) {
            try{
                if(kicked)
                    break;
                
                if(!text.equals("")){
                    //Check for command from user
                    if(text.equals("/close")) break;
                    if(text.charAt(0) == '/'){
                        out.writeUTF(DE.encryptText(text, true));
                    }else{
                        //Send message if message is not empty
                        if(!text.equals("")){
                                out.writeUTF(DE.encryptText(username+": "+text));
                                out.flush();
                        }
                    }
                }
            }catch(IOException e){
                System.out.println("::Could not send message. "+e);
                break;
            }
            return true;
        }
        sock.close();
        return false;
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
    
    /**
     * Requires setupLevel 0
     * @return 
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException 
     * @throws java.security.InvalidKeyException 
     * @throws javax.crypto.IllegalBlockSizeException 
     * @throws javax.crypto.BadPaddingException 
     */
    public String init() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException{
        errorMessage = "";
        setupLevel = 0;
        
        if(setupLevel != 0){
            errorMessage = "Current SetupLevel: "+setupLevel+", Level 0 required.";
            return "";
        }
        
        kicked = false;
        closed = false;
        connectToServer();

        out = new DataOutputStream(sock.getOutputStream());
        in = new DataInputStream(sock.getInputStream());

        //Get Public key from server, send Public to server
        String publicKey = DE.getPublicKey();
        String serverPublicKey = in.readUTF();
        out.writeUTF(publicKey);

        users.add("Server");
        DE.addPublicKey("Server", serverPublicKey);
        
        out.writeUTF(DE.encryptText("California:Goleta"));
        
        String popularString = DE.decryptText(in.readUTF());
        
        setupLevel++;
        
        return popularString;
    }
    
    private void updateKeys(String publicKey) 
            throws IOException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, InvalidKeySpecException{
        //get usernames and public keys from the server
        String usersFromServer = DE.decryptText(in.readUTF());
        users.clear();
        users.addAll(Arrays.asList(usersFromServer.split(";")));

        String publicKeys = DE.decryptText(in.readUTF());
        DE.userKeys.clear();
        for(int i = 0; i < users.size(); i++){
            DE.addPublicKey(users.get(i), publicKeys.substring(i*publicKey.length(), (i+1)*publicKey.length()));
        }
    }
    
    /**
     * Attempts to connect to the server.
     */
    private void connectToServer(){
        //Connect to server
        while(true){
            try{
                sock = new Socket(IP, PORT);
                break;
            }catch(UnknownHostException e){
                //Should never happen
                System.out.println("::Unknown server host.");
                System.exit(1);
            }catch(IOException e){
                //Happens if server is offline or under high load
                System.out.println("::Could not connect to server.");
                System.out.print("::Hit enter to retry connection.");
                try{
                    if(!inFromUser.readLine().equals(""))
                        System.exit(0);
                }catch(IOException e1){
                    System.out.println("Input error. "+e1);
                    System.exit(1);
                }
            }
        }
    }
    
    /**
     * Starts a listener thread then waits for input from the user.
     */
    public void startListener() {
        if(setupLevel != 5){
            errorMessage = "Current SetupLevel: "+setupLevel+", Level 3 required.";
            return;
        }
        
        Thread listener = new Thread(new client(this, in));
        listener.start();
    }
    
    /*
     * TODO DELETE THIS IN THE ANDROID APP
    */
    private void startMessenger() 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, IOException {
        
        if(setupLevel != 5){
            errorMessage = "Current SetupLevel: "+setupLevel+", Level 3 required.";
            return;
        }
        
        //Message(s) to the user
        System.out.println("::Type \"/close\" to close connection.\n");
        //TODO: add other funtions for user.
        //----------------------
       
        //Wait for input from the user
        while(!closed) {
            try{
                String send = inFromUser.readLine();

                if(kicked)
                    break;
                
                if(!send.equals("")){
                    //Check for command from user
                    if(send.equals("/close")) break;
                    if(send.charAt(0) == '/'){
                        out.writeUTF(DE.encryptText(send, true));
                    }else{
                        //Send message if message is not empty
                        if(!send.equals("")){
                                out.writeUTF(DE.encryptText(username+": "+send));
                                out.flush();
                        }
                    }
                }
            }catch(IOException e){
                System.out.println("::Could not send message. "+e);
                break;
            }
        }
        closed = false;
        kicked = false;
        sock.close();
    }
    
    /***************************************************************************
    * Static methods
    ***************************************************************************/
    
    /**
     * Check if a string contains any of the illegal characters
     * @param string
     * @return 
     */
     public static boolean isValid(String string){
        String[] invalidChars = {";"," ", "/"};
        for(String s : invalidChars){
            if(string.contains(s)){
                System.out.println("::Can not contain \';\', \' \', or \'/\'");
                return false;
            }
        }
        return true;
    }
}
