package com.github.akmakm.main;

import static com.github.akmakm.config.Constants.*;
import com.github.akmakm.rabbitmq.RabbitMQ;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * A resizing tool
 *
 * @author alka
 */
public class Resizer extends DefaultConsumer implements Runnable {

    private int myCount = CONSUME_ALL;
    private final RabbitMQ myRabbit;

    /**
     * DefaultConsumer constructor is prohibited
     *
     * @param rabbitChannel
     */
    private Resizer(Channel rabbitChannel) {
        super(rabbitChannel);
        myCount = 0;
        myRabbit = null;
    }

    /**
     * Constructor gets a rabbit channel and the images count to resize
     *
     * @param rabbit
     * @param count
     */
    public Resizer(RabbitMQ rabbit
            , int count) {
        super(rabbit.getChannel());
        myRabbit = rabbit;
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
        System.out.println("alkaRes: constructor - counted "+myCount+" items to resize");
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
            System.exit(ERR_UNEXPECTED_RESIZE_ITEM);
        }
        System.err.println("alkaRes hd1.1:  new File("+new String(body)
                +"); myCount=="+myCount);
        File fileIn = new File(new String(body));
        // scale image on disk
        String dirOutName = fileIn.getParent()
                    + File.separator
                    + "images_resized";
        File dirOut = new File(dirOutName);
        File fileOut = new File(dirOutName+File.separator+fileIn.getName());
        
        try {
            if (! dirOut.exists()) {
                System.err.println("alkaRes hd: creating "+dirOutName);
                dirOut.mkdir();
            }
            if (!fileIn.canRead()) {
                System.err.println("alkaRes hd3a:  cannot read "+fileIn.getName());
            } else {
                System.err.println("alkaRes hd3b:  read "+fileIn.getName());
                BufferedImage originalImage = ImageIO.read(fileIn);
                int type = originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB
                    : originalImage.getType();
                System.err.println("alkaRes hd4:  fileIn read ok");
                BufferedImage resizedImageJpg = resizeImage(fileIn);
                System.err.println("alkaRes hd5:  resize ok");
                ImageIO.write(resizedImageJpg, "jpg", fileOut);
                System.err.println("alkaRes hd6:  written ok");
                resultSuccess = true;
            }
        } catch (SecurityException ex1) {
            System.err.println("alkaRes hd3b:  problem with accessing files or"
                    + "creating an images_resized folder, exception \""
                    + ex1.getMessage() + "\"");
            ex1.printStackTrace(System.err);
        }
        
        // Acknowledge and move to next queue
        getChannel().basicAck (deliveryTag, true);
        if (resultSuccess) {
            myRabbit.putToQueue(UPLOAD_QUEUE, fileOut.getAbsolutePath());
        } else {
            myRabbit.putToQueue(FAILED_QUEUE, new String(body));
        }
        if (myCount == 0) {
            System.out.println("alkaRes: exiting \n"
                    + ", myCount="
                    + myCount);
            System.exit(NO_ERROR);
        }
    }


    /**
     * Actual image resize action
     *
     */
    private static BufferedImage resizeImage(//BufferedImage originalImage, int type) {
            File fileIn) throws IOException {
        BufferedImage originalImage = ImageIO.read(fileIn);
        int img_widthFull = originalImage.getWidth();
        int img_heightFull = originalImage.getHeight();
        // Scaled dimensions
        int img_widthNew = (img_widthFull > img_heightFull ? MAXD
                : MAXD * img_widthFull / img_heightFull);
        int img_heightNew = (img_widthFull <= img_heightFull ? MAXD
                : MAXD * img_heightFull / img_widthFull);

        int type = originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB
                 : originalImage.getType();
        // MAXDxMAXD is the desired bounding box of the scaled area
        BufferedImage resizedImage = new BufferedImage(MAXD, MAXD, type);
        Graphics2D g2Resize = resizedImage.createGraphics();
        if (g2Resize != null) {
            g2Resize.setColor(Color.WHITE);
            g2Resize.fillRect(0, 0, MAXD, MAXD);

            int x_Offset = (MAXD - img_widthNew) / 2;
            int y_Offset = (MAXD - img_heightNew) / 2;
            g2Resize.drawImage(originalImage, x_Offset, y_Offset, img_widthNew, img_heightNew, null);
            g2Resize.dispose();
        }
        return resizedImage;
    }
    
    
    /**
     * Resize task runner
     */
    @Override
    public void run() {
        boolean autoAck = false;
        try {
            getChannel().basicConsume(RESIZE_QUEUE, autoAck, this);
        } catch (IOException ex) {
            Logger.getLogger(Resizer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
