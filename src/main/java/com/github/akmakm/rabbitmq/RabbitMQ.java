package com.github.akmakm.rabbitmq;

import static com.github.akmakm.config.Constants.*;

import java.io.IOException;
import java.util.Observable;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Consumes messages from RabbitMQ. This class is observable and observers will
 * be notified when new payloads are consumed.
 *
 * @author alka
 */
public class RabbitMQ extends Observable {

    /**
     * Host.
     */
    private String host;

    /**
     * Port.
     */
    private int port;

    /**
     * Username.
     */
    private String username;

    /**
     * Password.
     */
    private String password;

    /**
     * Virtual host.
     */
    private String virtualHost;

    private static Channel mychannel;
    
    /**
     * RabbitMQ connection.
     */
    private static Connection connection;

    /**
     * RabbitMQ builder.
     */
    public static class Builder {

        /**
         * RabbitMQ being constructed.
         */
        private final RabbitMQ internal;

        /**
         * New builder.
         * @throws java.io.IOException
         * @throws java.util.concurrent.TimeoutException
         */
        public Builder() throws IOException, TimeoutException {
            internal = new RabbitMQ();
        }

        /**
         * Sets host.
         *
         * @param host host
         * @return this builder
         */
        public Builder setHost(String host) {
            internal.host = host;
            return this;
        }

        /**
         * Sets port.
         *
         * @param port port
         * @return this builder
         */
        public Builder setPort(int port) {
            internal.port = port;
            return this;
        }

        /**
         * Sets username.
         *
         * @param username username
         * @return this builder
         */
        public Builder setUsername(String username) {
            internal.username = username;
            return this;
        }

        /**
         * Sets password.
         *
         * @param password password
         * @return this builder
         */
        public Builder setPassword(String password) {
            internal.password = password;
            return this;
        }

        /**
         * Sets virtual host.
         *
         * @param virtualHost virtual host
         * @return this builder
         */
        public Builder setVirtualHost(String virtualHost) {
            internal.virtualHost = virtualHost;
            return this;
        }

        /**
         * Builds RabbitMQ.
         *
         * @return RabbitMQ
         * @throws java.io.IOException
         * @throws java.util.concurrent.TimeoutException
         */
        public RabbitMQ build() throws IOException, TimeoutException {
            // Open a connection with the given parameters
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(internal.host);
            factory.setPort(internal.port);
            factory.setUsername(internal.username);
            factory.setPassword(internal.password);
            factory.setVirtualHost(internal.virtualHost);
            // Detect if connection is lost
            factory.setRequestedHeartbeat(5);
            try {
                internal.connection = factory.newConnection();
                internal.mychannel = connection.createChannel();
            } catch (IOException | TimeoutException ex) {
                System.err.println("Failed to connect to the RabbitMQ service"
                                +" \nwith these parameters: host="+internal.host
                                +" \n                       port="+internal.port
                                +" \n                       user="+internal.username);
                ex.printStackTrace(System.err);
                throw new IOException("Couldn't connect to RabbitMQ");
            } 

            // Declare appropriate queues
            internal.mychannel.queueDeclare(RESIZE_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(UPLOAD_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(DONE_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(FAILED_QUEUE
                                           ,true, false, false, null);
            return internal;
        }
    }
    
    /**
     * Instantiates RabbitMQ.
     */
    private RabbitMQ() {
    }

    /**
     * Channel getter
     * @return 
     */
    public static Channel getChannel () {
        return mychannel;
    }
    
    /**
     * Puts a record to the queue
     * @param queueName
     * @param fileName
     */
    public void putToQueue (String queueName, String fileName) {
        byte[] messageBodyBytes = fileName.getBytes();
        try {
            mychannel.basicPublish(DEFAULT_EXCHANGE,
                    queueName,
                    null,
                    messageBodyBytes);
        } catch (IOException ex) {
            System.err.println("Failed to put into "
                    +queueName+" queue this item:"
                    +fileName);
            ex.printStackTrace(System.err);
            try {
                mychannel.basicPublish(DEFAULT_EXCHANGE,
                        FAILED_QUEUE,
                        null,
                        messageBodyBytes);
            } catch (IOException ex1) {
                System.err.println("Failed to put it into FAILED_QUEUE");
                ex1.printStackTrace(System.err);
            }
        }
    }
    
    public void printStatus () {
        System.out.println("Images Processor Bot"+'\n'
                          +"Queue"+'\t'+"Count");
        try {
            System.out.println(RESIZE_QUEUE+'\t'+mychannel
                    .queueDeclarePassive(RESIZE_QUEUE).getMessageCount());
            System.out.println(UPLOAD_QUEUE+'\t'+mychannel
                    .queueDeclarePassive(UPLOAD_QUEUE).getMessageCount());
            System.out.println(DONE_QUEUE+'\t'+mychannel
                    .queueDeclarePassive(DONE_QUEUE).getMessageCount());
            System.out.println(FAILED_QUEUE+'\t'+mychannel
                    .queueDeclarePassive(FAILED_QUEUE).getMessageCount());
        } catch (IOException ex) {
            System.err.println("Failed to printStatus");
            ex.printStackTrace(System.err);
        }
    }
    
    @Override
    public String toString() {
        return String.format("[RabbitMQ:%n"
            + "host=%s%n"
            + "port=%d%n"
            + "virtualHost=%s%n", host, port, virtualHost);
    }
}
