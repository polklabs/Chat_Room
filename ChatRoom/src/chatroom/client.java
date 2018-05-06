/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatroom;

import java.io.*;
import java.net.*;

/**
 *
 * @author polka
 */
public class client implements Runnable{
    /**Th socket for the user*/
    private Socket sock;
    /**The stream to get messages from the server*/
    private DataInputStream in;
    
    /**Constructor
     * opens a new input stream
     * @param socket 
     */
    public client(Socket socket){
        sock = socket;
        try{
            in = new DataInputStream(sock.getInputStream());
        }catch(IOException e){
            System.out.println("::Could not start client listener.");
        }
    }
    
    /**Main method of the thread
     * Waits for messages and closes the connection/thread if the socket closes.
     */
    @Override
    public void run(){
        try{
            String message;
            while(true){
                message = in.readUTF()
                        ;
                
                if(message == null){
                    System.out.println("::The connection has been closed.");
                    return;
                }else{
                    System.out.println("\r"+message);
                }
            }
        }catch(IOException e){
            System.out.println("::The connection has been closed.");
        }
    }
}
