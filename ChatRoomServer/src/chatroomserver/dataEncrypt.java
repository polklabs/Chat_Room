package chatroomserver;

/**
 * For public private key gen and encryption
 */
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.apache.commons.codec.binary.Base64;

/**
 * General Imports
 */
import java.util.Arrays;

/**
 * Can encrypt text, decrypt text, generate key pairs, hash passwords
 * @author Andrew Polk
 * @version 0.1
 */
public final class dataEncrypt {
    
    /***************************************************************************
    * Public variables
    ***************************************************************************/
    
    /**Public key, send to other users/server*/
    public PublicKey publicKey;
    
    /***************************************************************************
    * Private variables
    ***************************************************************************/
    
    private KeyPairGenerator    keyGen;
    private PrivateKey          privateKey;
    private Cipher              cipher;
    
    /***************************************************************************
    * Constructor
    ***************************************************************************/
    
    /**Constructor
     * assigns default values and generates pub/private keys
     * @param keyLength how long the key generated is, defined by server or static 1024.
     */
    public dataEncrypt(int keyLength) {
        this.keyGen = null;
        this.cipher = null;
        try{
            this.keyGen = KeyPairGenerator.getInstance("RSA");
            this.keyGen.initialize(keyLength);
            
            this.cipher = Cipher.getInstance("RSA");
            
            createKeys();
        }catch(NoSuchAlgorithmException | NoSuchPaddingException e){
            System.err.println(e.getMessage());
        }
    }
    
    /***************************************************************************
    * Public methods
    ***************************************************************************/
    
    /**Converts the current users public key to a string
     * @return the string representation of the users public key
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException 
     */
    public String getPublicKey() 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        return PublicKeyToString(this.publicKey);
    }
    
    /**Encrypts a byte array and return the String representation of them to be
     * over TCP.
     * Singly encrypts the bytes to send from the server
     * @param bytes the bytes to be encrypted
     * @return The string of the encrypted bytes
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException 
     */
    public String encryptBytes(byte[] bytes, PublicKey pubKey, int id) 
            throws UnsupportedEncodingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        return encrypt(((char)(id)+new String(bytes, "UTF-8")).getBytes(), pubKey, false);
    }
    
    /**Doubly encrypts a string of text to be sent to the server
     * @param msg the string to be encrypted
     * @param pubKey the key of the user to send to
     * @return the encrypted string
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException 
     */
    public String encryptText(String msg, PublicKey pubKey) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        return encrypt(((char)(0)+encrypt(msg.getBytes(), pubKey, true)).getBytes(), pubKey, false);
    }
    
    /**Decrypts a string once with the server's private key.
     * @param string The doubly encrypted string that the user received.
     * @return the decrypted data in byte array format
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException 
     */
    public byte[] decryptBytes(String string) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        return decrypt(string, null, true);
    }
    
    /**Decrypts a string twice, Once with the server's private key. Then with the 
     * public of whoever the message was sent from.
     * @param msg the doubly encrypted string that the user received.
     * @return the decrypted data in string format.
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     * @throws UnsupportedEncodingException 
     */
    public String decryptText(String msg, PublicKey pubKey) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException {
        
        return new String(decrypt(new String(decrypt(msg, pubKey, true), "UTF-8"), pubKey, false), "UTF-8");
    }
    
    public byte[] decrypt(String msg, PublicKey pubKey) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        return decrypt(msg, pubKey, false);
    }
    
    /***************************************************************************
    * Private methods
    ***************************************************************************/
    
    //Creates a private public key pair
    private void createKeys() {
        KeyPair pair = this.keyGen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }
    
    //Basic encrypt function that can take in an arbritrary length
    private String encrypt(byte[] bytes, PublicKey pubKey, boolean usePrivateKey) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        int SIZE = 116;
        
        if(usePrivateKey == true){
            this.cipher.init(Cipher.ENCRYPT_MODE, this.privateKey);
        }else{
            this.cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        }
        
        String result = "";
        
        //Can only encrypt 117 bytes at a time
        int start = 0, index = SIZE;
        while(true){
            //See if you only need to encrypt a smaller portion
            if(bytes.length-start < SIZE)
                index = bytes.length;
            
            //Get sub array of total message
            byte[] msg1 = Arrays.copyOfRange(bytes, start, index);
            
            //Encrypt the bytes
            result += Base64.encodeBase64String(cipher.doFinal(msg1));
            
            //increment and break when done
            start += SIZE;
            index += SIZE;
            if(start > bytes.length-1)
                break;
        }
        
        return result;
    }
    
    //Basic decrypt function that can take in an arbritrary lenght and return bytes
    private byte[] decrypt(String string, PublicKey pubKey, boolean usePrivateKey) 
            throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        
        int SIZE = 172;
        
        if(usePrivateKey){
            this.cipher.init(Cipher.DECRYPT_MODE, this.privateKey);
        }else{
            this.cipher.init(Cipher.DECRYPT_MODE, pubKey);
            
            if(string.length() != SIZE)
                string = string.substring(1);
        }
        
        byte[] result = new byte[0];
        
        int start = 0, index = SIZE;
        while(true){
            if(string.length()-start < SIZE)
                index = string.length();
            
            String msg1 = string.substring(start, index);
            
            //Encrypt the bytes
            byte[] temp2 = cipher.doFinal(Base64.decodeBase64(msg1));
            byte[] newResult = new byte[result.length + temp2.length];
            
            //Join result with new data together
            System.arraycopy(result, 0, newResult, 0, result.length);
            System.arraycopy(temp2, 0, newResult, result.length, temp2.length);
            
            result = newResult;
            
            start += SIZE;
            index += SIZE;
            if(start > string.length()-1)
                break;
        }
        return result;
    }
    
    /***************************************************************************
    * Static methods
    ***************************************************************************/
    
    public static String PublicKeyToString(PublicKey pubKey) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = kf.getKeySpec(pubKey, X509EncodedKeySpec.class);
        return Base64.encodeBase64String(spec.getEncoded());
    }
    
    public static PublicKey stringToPublicKey(String key) 
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        
        byte[] data = Base64.decodeBase64(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
