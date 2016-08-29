package com.github.akmakm.main;

import com.github.akmakm.config.Configuration;
import static com.github.akmakm.config.Constants.*;
import com.github.akmakm.rabbitmq.RabbitMQ;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.*;
import com.google.api.services.drive.Drive;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.*;

/**
 * An uploading tool
 *
 * @author alka
 */
public class Uploader extends DefaultConsumer implements Runnable {

    private int myCount = CONSUME_ALL;
    private final RabbitMQ myRabbit;
    private final Drive myDrive;
    private final Configuration myConfig;
    
    /**
     * DefaultConsumer constructor is prohibited
     *
     * @param rabbitChannel
     */
    private Uploader(Channel rabbitChannel) {
        super(rabbitChannel);
        myCount = 0;
        myRabbit = null;
        myDrive = null;
        myConfig = null;
    }

    /**
     * Constructor gets a rabbit channel and the images count to upload
     *
     * @param rabbit
     * @param gDrive
     * @param count
     * @param config
     */
    public Uploader (RabbitMQ rabbit
            , Drive gDrive
            , int count
            , Configuration config) {
        super(RabbitMQ.getChannel());
        myRabbit = rabbit;
        myDrive = gDrive;
        myConfig = config;
        // Count items in the queue to process
        try {
            myCount = getChannel()
                     .queueDeclarePassive(RESIZE_QUEUE)
                     .getMessageCount();
        } catch (IOException ex) {
            Logger.getLogger(Resizer.class.getName()).log(Level.SEVERE, null, ex);
            myCount = 0;
        }
        if (count == CONSUME_ALL) {
            // count is equal to the queue length
        } else if (count > 0) {
            // Up to count, but no more than there are in the queue
            myCount = Math.min(count, myCount);
        } else {
            // Some negative value was given, nothing to do
            myCount = 0;
        }
        System.out.println("alkaUpl: constructor - counted "+myCount+" items to upload");
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
        long deliveryTag = envelope.getDeliveryTag();
        boolean resultSuccess = false;
        if (--myCount < 0) {
            getChannel().basicAck (deliveryTag, false);
            System.exit(ERR_UNEXPECTED_UPLOAD_ITEM);
        }
        try {
            System.err.println("alkaUpl hd11:  new File("+new String(body)+");");
            // find image on disk
            java.io.File fileIn = new java.io.File(new String(body));
            if (!fileIn.canRead()) {
                System.err.println("alkaUpl hd13a:  cannot read "+fileIn.getName());
            } else {
                System.err.println("alkaUpl hd13b:  read "+fileIn.getName());
                resultSuccess = doUpload (fileIn);
            }
        } catch (Exception ex1) {
            System.err.println("alkaUpl hd13b:  problem with accessing files or"
                    + "uploading an image, exception \""
                    + ex1.getMessage() + "\"");
        }

        // Acknowledge and move to next queue
        getChannel().basicAck (deliveryTag, true);
        if (resultSuccess) {
            myRabbit.putToQueue(DONE_QUEUE, new String(body));
        } else {
            myRabbit.putToQueue(FAILED_QUEUE, new String(body));
        }
        if (myCount == 0) {
            System.out.println("alkaUpl: exiting \n"
                    + ", myCount="
                    + myCount);
            System.exit(NO_ERROR);
        }
    }

    /**
     * Upload function
     */
    private boolean doUpload(java.io.File fileUp) throws 
            FileNotFoundException
            , IOException {
              
        // File's metadata.
        File body = new File();
        body.setTitle(fileUp.getName());
        body.setDescription(fileUp.getPath());
        body.setMimeType("image/jpeg");
        // File's content.
        FileContent mediaContent = new FileContent("image/jpeg", fileUp);
        try {
            File file = myDrive.files().insert(body, mediaContent).execute();
            // Uncomment the following line to print the File ID.
            // System.out.println("File ID: " + file.getId());
            return true;
        } catch (IOException e) {
            System.err.println("An error occured: " + e);
            return false;
        }

    }

    /**
     * Upload task runner
     */
    @Override
    public void run() {
        boolean autoAck = false;
        try {
            getChannel().basicConsume(UPLOAD_QUEUE, autoAck, this);
        } catch (IOException ex) {
            Logger.getLogger(Resizer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
    
}
