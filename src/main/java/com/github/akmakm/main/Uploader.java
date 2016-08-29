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
        myCount = count;
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
        java.io.File fileIn = new java.io.File(new String(body));
        try {
            // find image on disk
            if (!fileIn.canRead()) {
                System.err.println("Uploader:  cannot read "+fileIn.getName());
            } else {
                resultSuccess = doUpload (fileIn);
            }
        } catch (Exception ex) {
            System.err.println("Uploader:  problem with accessing files or"
                    + "uploading an image, exception \""
                    + ex.getMessage() + "\"");
            ex.printStackTrace(System.err);
        }

        // Acknowledge and move to next queue
        getChannel().basicAck (deliveryTag, true);
        if (resultSuccess) {
            System.out.println("Uploader: done "+fileIn.getAbsolutePath());
            myRabbit.putToQueue(DONE_QUEUE, new String(body));
        } else {
            myRabbit.putToQueue(FAILED_QUEUE, new String(body));
        }
        if (myCount == 0) {
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
        } catch (IOException ex) {
            System.err.println("Uploader: An error occured: " + ex.getMessage());
            ex.printStackTrace(System.err);
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
            System.err.println("Uploader:  failed to start consuming from the queue");
            ex.printStackTrace(System.err);
        }
    }

    
    
}
