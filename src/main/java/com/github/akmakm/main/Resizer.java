package com.github.akmakm.main;

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
 * @author alka1013
 */
public class Resizer extends DefaultConsumer implements Runnable {

    private static final int MAXD = 640;
    public static final int ENTIRE_QUEUE = -1;
    private int myCount = ENTIRE_QUEUE;
    private final RabbitMQ myRabbit;
//    private final String myResizeQueueName;
//    private final String myUploadQueueName;
//    private final String myFailQueueName;

    /**
     * DefaultConsumer constructor is prohibited
     *
     * @param rabbitChannel
     */
    private Resizer(Channel rabbitChannel) {
        super(rabbitChannel);
        myCount = -1;
        myRabbit = null;
    }

    /**
     * Constructor gets a rabbit channel and the images count to resize
     *
     * @param rabbit
     * @param count
     */
    public Resizer(RabbitMQ rabbit
//            Channel rabbitChannel
//            , String resQName
//            , String uplQName
//            , String failQName
            , int count) {
        super(rabbit.getChannel());
        myRabbit = rabbit;
        myCount = count;
//        myResizeQueueName = resQName;
//        myUploadQueueName = uplQName;
//        myFailQueueName = failQName;
    }

    /**
     * Actual image resize action
     *
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int type) {
        int img_widthFull = originalImage.getWidth();
        int img_heightFull = originalImage.getHeight();
        int img_widthNew = img_widthFull > img_heightFull ? MAXD
                : img_widthFull * img_heightFull / MAXD;
        int img_heightNew = img_widthFull <= img_heightFull ? MAXD
                : img_heightFull * img_widthFull / MAXD;

        // MAXDxMAXD is the desired bounding box of the scaled area
        BufferedImage resizedImage = new BufferedImage(MAXD, MAXD, type);
        Graphics2D g2Resize = resizedImage.createGraphics();
        g2Resize.setColor(Color.WHITE);
        g2Resize.fillRect(0, 0, MAXD, MAXD);

        int x_Offset = (MAXD - img_widthNew) / 2;
        int y_Offset = (MAXD - img_heightNew) / 2;
        g2Resize.drawImage(originalImage, x_Offset, y_Offset, img_widthNew, img_heightNew, null);
        g2Resize.dispose();

        return resizedImage;
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
            System.err.println("alkaRes hd1:  new File("+new String(body)+");");
            // scale image on disk
            File fileIn = new File(new String(body));
            // create output dir
            String dirOutName = fileIn.getParent()
                    + File.separator
                    + "images_resized";
            System.err.println("alkaRes hd2:  dirOutName="+dirOutName);
            File dirOut = new File(dirOutName);
            if (! dirOut.exists()) {
                System.err.println("alkaRes hd: creating "+dirOutName);
                if (!dirOut.mkdir()) {
                    System.err.println("alkaRes hd: hren ");
                    dirOut = dirOut.getParentFile();
                    System.err.println("alkaRes hd2b:  dirOutName="+dirOut.getName());
                } else {
                    System.err.println("alkaRes hd: ok ");
                }
                dirOut.setWritable(true);
            }
//            if (dirOut.isDirectory()) {
            if (!fileIn.canRead()) {
                System.err.println("alkaRes hd3a:  cannotread "+fileIn.getName());
            }
                System.err.println("alkaRes hd3b:  read "+fileIn.getName());
                BufferedImage originalImage = ImageIO.read(fileIn);
                int type = originalImage.getType() == 0 ? BufferedImage.TYPE_INT_ARGB
                    : originalImage.getType();
                System.err.println("alkaRes hd4:  fileIn read ok");
                BufferedImage resizedImageJpg = resizeImage(originalImage, type);
                System.err.println("alkaRes hd5:  resize ok");
                ImageIO.write(resizedImageJpg, "jpg"
                        , new File(dirOutName+File.separator+fileIn.getName()));
                System.err.println("alkaRes hd6:  written ok");
                resultSuccess = true;
//            } else {
//                System.err.println("alkaRes hd3a: directory "+dirOut.getName()
//                                  +" doesn't exist and cannot be created");
//            }
        } catch (Exception ex1) {
            System.err.println("alkaRes hd3b:  problem with accessing files or"
                    + "creating an images_resized folder, exception \""
                    + ex1.getMessage() + "\"");
        }
        // Move to next queue
        if (resultSuccess) {
            myRabbit.putToQueue(myRabbit.UPLOAD_QUEUE, new String(body));
        } else {
            myRabbit.putToQueue(myRabbit.FAILED_QUEUE, new String(body));
        }
        int remainingMessages = getChannel()
                .queueDeclarePassive(myRabbit.RESIZE_QUEUE)
                .getMessageCount();
        if (remainingMessages == 0
                || --myCount == 0) {
            System.out.println("alkaRes: exiting (remainingMessages="
                    + remainingMessages
                    + ", myCount="
                    + myCount);
            System.exit(0);
        }
    }

    /**
     * Resize task runner
     */
    @Override
    public void run() {
        try {
            getChannel().basicConsume(myRabbit.RESIZE_QUEUE, true, this);
        } catch (IOException ex) {
            Logger.getLogger(Resizer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
