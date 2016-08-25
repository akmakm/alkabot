package com.github.akmakm.main;

import com.github.akmakm.rabbitmq.RabbitMQ;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * An uploading tool
 *
 * @author alka
 */
public class Uploader extends DefaultConsumer implements Runnable {

    public static final int ENTIRE_QUEUE = -1;
    private int myCount = ENTIRE_QUEUE;
    private final RabbitMQ myRabbit;
    final String DROP_BOX_APP_KEY = "APPKEY";
    final String DROP_BOX_APP_SECRET = "SECRETKEY";
    final String myCode;

    /**
     * DefaultConsumer constructor is prohibited
     *
     * @param rabbitChannel
     */
    private Uploader(Channel rabbitChannel) {
        super(rabbitChannel);
        myCount = -1;
        myRabbit = null;
        myCode = null;
    }

    /**
     * Constructor gets a rabbit channel and the images count to upload
     *
     * @param rabbit
     * @param count
     */
    public Uploader (RabbitMQ rabbit, int count, String code) {
        super(RabbitMQ.getChannel());
        myRabbit = rabbit;
        myCount = count;
        myCode = code;
    }

    /**
     * Consumes next item from the queue
     *
     * @param consumerTag
     * @param envelope
     * @param properties
     * @param body
     * @throws IOException
     */
    @Override
    synchronized public void handleDelivery(String consumerTag,
            Envelope envelope,
            AMQP.BasicProperties properties,
            byte[] body) throws IOException {
        boolean resultSuccess = false;
        try {
            System.err.println("alkaUpl hd1:  new File("+new String(body)+");");
            // find image on disk
            File fileIn = new File(new String(body));
            if (!fileIn.canRead()) {
                System.err.println("alkaUpl hd3a:  cannotread "+fileIn.getName());
            }
            System.err.println("alkaUpl hd3b:  read "+fileIn.getName());
            resultSuccess = doUpload (fileIn, myCode);
        } catch (Exception ex1) {
            System.err.println("alkaUpl hd3b:  problem with accessing files or"
                    + "creating an images_resized folder, exception \""
                    + ex1.getMessage() + "\"");
        }
        // Move to next queue
        if (resultSuccess) {
            myRabbit.putToQueue(myRabbit.DONE_QUEUE, new String(body));
        } else {
            myRabbit.putToQueue(myRabbit.FAILED_QUEUE, new String(body));
        }
        int remainingMessages = getChannel()
                .queueDeclarePassive(myRabbit.RESIZE_QUEUE)
                .getMessageCount();
        if (remainingMessages == 0
                || --myCount == 0) {
            System.out.println("alkaUpl: exiting (remainingMessages="
                    + remainingMessages
                    + ", myCount="
                    + myCount);
            System.exit(0);
        }
    }

    /**
     * Upload task runner
     */
    @Override
    public void run() {
        try {
            getChannel().basicConsume(myRabbit.RESIZE_QUEUE, true, this);
        } catch (IOException ex) {
            Logger.getLogger(Resizer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean doUpload(File fileUp, String code) throws FileNotFoundException, IOException {
        try {
            String rootDir = fileUp.getPath();
            DbxAppInfo dbxAppInfo = new DbxAppInfo(DROP_BOX_APP_KEY, DROP_BOX_APP_SECRET);
            
            DbxRequestConfig reqConfig = new DbxRequestConfig("javarootsDropbox/1.0",
                    Locale.getDefault().toString());
            DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(reqConfig, dbxAppInfo);
            
            String authorizeUrl = webAuth.start();
            DbxAuthFinish authFinish = webAuth.finish(code);
            String accessToken = authFinish.accessToken;
            DbxClient client = new DbxClient(reqConfig, accessToken);
            
            System.out.println("account name is : " + client.getAccountInfo().displayName);
            FileInputStream inputStream = new FileInputStream(fileUp);
            try {
                
                DbxEntry.File uploadedFile = client.uploadFile(rootDir,
                        DbxWriteMode.add(), fileUp.length(), inputStream);
//                String sharedUrl = client.createShareableUrl(fileUp);
                System.out.println("Uploaded: " + uploadedFile.toString() + " URL " 
                        + fileUp.getPath());
            } catch (IOException ex) {
                Logger.getLogger(Uploader.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                inputStream.close();
            }
        }   catch (DbxException ex) {
            Logger.getLogger(Uploader.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
    
}
