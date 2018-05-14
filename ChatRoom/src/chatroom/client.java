package chatroom;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

/**
 * Waits for message from the server
 * @author Andrew Polk
 * @version 0.1
 */
public class client implements Runnable{
    
    /***************************************************************************
    * Public variables
    ***************************************************************************/
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/

    private final DataInputStream in;
    private final dataEncrypt     DE;
    private final ChatRoom        parent;
    
    /***************************************************************************
    * Constructor
    ***************************************************************************/
    
    /**Constructor
     * opens a new input stream
     * @param parent parent instance
     * @param in DataInputStream from main class
     */
    public client(ChatRoom parent, DataInputStream in){
        this.parent = parent;
        DE = parent.DE;
        this.in = in;
    }
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
    
    /**Main method of the thread
     * Waits for messages and closes the connection/thread if the socket closes.
     */
    @Override
    public void run(){
        String message;
        boolean waitingForKeys = false;
        
        while(true){
            try{
                message = DE.decryptText(in.readUTF());
                
                if(message != null){
                    //Server updating keys
                    String[] split = message.split(":", 2);
                    if(split[0].equals("Server") && split.length > 1){
                        if(waitingForKeys){
                            if(!split[0].equals("*")){
                                DE.userKeys.clear();
                                int length = DE.getPublicKey().length();
                                String publicKeys = split[1];
                                for(int i = 0; i < parent.users.size(); i++){
                                    DE.addPublicKey(parent.users.get(i), publicKeys.substring(i*length, (i+1)*length));
                                }
                            }
                            waitingForKeys = false;
                        }
                        else if(split[1].charAt(0) == '/'){
                            char temp = split[1].charAt(1);
                            split[1] = split[1].substring(2);
                            switch(temp){
                                case '*':
                                    parent.users.clear();
                                    parent.users.addAll(Arrays.asList(split[1].split(";")));
                                    waitingForKeys = true;
                                    break;
                                case '-':
                                    parent.kicked = true;
                                    return;
                            }
                        }else {
                            //onProgressUpdate
                            System.out.println("\r"+message);
                        }
                    }else{
                        //onProgressUpdate
                        System.out.println("\r"+message);
                    }
                }else{
                    System.out.println("::The connection has been closed.");
                    break;
                }
            }catch(IOException e){
                System.out.println("::The connection has been closed.");
                break;
            }catch(NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e){
                System.out.println("::Could not receive message.");
                e.printStackTrace();
            }
        }
        
        parent.closed = true;
    }
}
