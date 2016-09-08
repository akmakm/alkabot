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
import javax.imageio.IIOException;
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
            System.err.println("Error - unexpected resize request");
            System.exit(ERR_UNEXPECTED_RESIZE_ITEM);
        }
        File fileIn = new File(new String(body));
        // scale image on disk
        String dirOutName = fileIn.getParent()
                    + File.separator
                    + "images_resized";
        File dirOut = new File(dirOutName);
        File fileOut = new File(dirOutName+File.separator+fileIn.getName());
        
        try {
            if (! dirOut.exists()) {
                System.err.println("Resizer: creating "+dirOutName);
                dirOut.mkdir();
            }
            if (!fileIn.canRead()) {
                System.err.println("Resizer:  cannot read "+fileIn.getName());
            } else {
                BufferedImage resizedImageJpg = resizeImage(fileIn);
                System.err.println("Resizer:  attempting ImageIO.write "
                    + "(resizedImageJpg=" + resizedImageJpg
                    + ", \"jpg\", fileOut=" + fileOut + ")");
                ImageIO.write(resizedImageJpg, "jpg", fileOut);
                resultSuccess = true;
            }
        } catch (SecurityException | IIOException ex1) {
            System.err.println("Resizer:  problem with accessing files or"
                    + "creating an images_resized folder, exception \""
                    + ex1.getMessage() + "\"");
            ex1.printStackTrace(System.err);
        }
        
        // Acknowledge and move to next queue
        getChannel().basicAck (deliveryTag, true);
        if (resultSuccess) {
            System.out.println("Resizer: done "+fileIn.getAbsolutePath());
            fileIn.delete();
            myRabbit.putToQueue(UPLOAD_QUEUE, fileOut.getAbsolutePath());
        } else {
            myRabbit.putToQueue(FAILED_QUEUE, new String(body));
        }
        if (myCount == 0) {
            System.exit(NO_ERROR);
        }
    }


    /**
     * Actual image resize action
     *
     */
    private static BufferedImage resizeImage(File fileIn) throws IOException {
        BufferedImage originalImage = ImageIO.read(fileIn);
        int img_widthFull = originalImage.getWidth();
        int img_heightFull = originalImage.getHeight();
        // Scaled dimensions
        int img_widthNew = (img_widthFull > img_heightFull ? MAXD
                : MAXD * img_widthFull / img_heightFull);
        int img_heightNew = (img_widthFull <= img_heightFull ? MAXD
                : MAXD * img_heightFull / img_widthFull);

                System.err.println("Resizer:  type=" +originalImage.getType());
                int type = originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB
                 : originalImage.getType();
        if (type == BufferedImage.TYPE_INT_RGB /*||
            type == BufferedImage.TYPE_4BYTE_ABGR*/) {
            type = BufferedImage.TYPE_INT_ARGB;
        }
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
            System.err.println("Resizer: failed to start consuming from the queue");
            ex.printStackTrace(System.err);
        }
    }

}
