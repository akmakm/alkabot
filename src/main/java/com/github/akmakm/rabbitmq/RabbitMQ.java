package com.github.akmakm.rabbitmq;

import com.github.akmakm.main.Log;

import java.io.IOException;
import java.util.Observable;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Timer;
import java.util.TimerTask;

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
     * Predefined exchange and queue names.
     */
    protected static final String DEFAULT_EXCHANGE = "";
    public static final String RESIZE_QUEUE = "resizeQueue";
    public static final String UPLOAD_QUEUE = "uploadQueue";
    public static final String DONE_QUEUE = "doneQueue";
    public static final String FAILED_QUEUE = "failedQueue";
    
    /** tbd
     * Queue to consume.
     */
    private String queue;
    
    /**tbd
     * Name of backup queue, or {@code null} if queue should not be backed up.
     */
    private String backupQueue;

    /**tbd
     * Name of error queue, or {@code null} if no error queue.
     */
    private String errorQueue;

    /**
     * Whether to verify timestamp unit. If not, assumes nanoseconds.
     */
    private boolean verifyTimestamp = false;

    /**
     * Log.
     */
    private Log log;

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
         * Sets queue to consume.
         *
         * @param queue queue
         * @return this builder
         */
        public Builder setQueue(String queue) {
            internal.queue = queue;
            return this;
        }

        /**
         * Sets error queue.
         *
         * @param errorQueue error queue
         * @return this builder
         */
        public Builder setErrorQueue(String errorQueue) {
            internal.errorQueue = errorQueue;
            return this;
        }

        /**
         * Whether to verify the timestamp unit. Defaults to false.
         *
         * @param verifyTimestamp whether to verify timestamp
         * @return this builder
         */
        public Builder setVerifyTimestamp(boolean verifyTimestamp) {
            internal.verifyTimestamp = verifyTimestamp;
            return this;
        }

        /**
         * Log. If not set, this object will not log messages.
         *
         * @param log log
         * @return this builder
         */
        public Builder setLog(Log log) {
            internal.log = log;
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
            if (internal.log != null) {
                internal.log.rabbitCreated(internal);
            }
            // Open a connection with the given parameters
            System.err.println("alka 004.1, host="+internal.host+""
                    + ", port="+internal.port
                    + ", username="+internal.username
                    + ", password="+internal.password);
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(internal.host);
            factory.setPort(internal.port);
            factory.setUsername(internal.username);
            factory.setPassword(internal.password);
            factory.setVirtualHost(internal.virtualHost);
            // Detect if connection is lost
            factory.setRequestedHeartbeat(5);
            System.err.println("alka 004.2 - get connection and channel");
            try {
                internal.connection = factory.newConnection();
                internal.mychannel = connection.createChannel();
            } catch (IOException e) {
                throw new IOException("Couldn't connect to RabbitMQ");
            } catch (TimeoutException e) {
                throw new TimeoutException("Couldn't connect to RabbitMQ");
            }

            // Declare appropriate queues
            //alka tbd channel.queueDeclarePassive(queue);
            System.err.println("alka 004.3 - declare queues");
            internal.mychannel.queueDeclare(RESIZE_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(UPLOAD_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(DONE_QUEUE
                                           ,true, false, false, null);
            internal.mychannel.queueDeclare(FAILED_QUEUE
                                           ,true, false, false, null);
            System.err.println("alka 004.4");
            return internal;
        }
    }
    
    /**
     * Parses RabbitMQ payload strings.
     */
//    private RabbitMQParser parser;

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
            try {
                mychannel.basicPublish(DEFAULT_EXCHANGE,
                        FAILED_QUEUE,
                        null,
                        messageBodyBytes);
            } catch (IOException ex1) {
                log.rabbitError(fileName, ex);
                log.rabbitError(fileName, ex1);
            }
        }
    }
    
    /**
     * Starts consuming data from RabbitMQ.
     *
     * @throws IOException error connecting to RabbitMQ
     * @throws TimeoutException error connecting to RabbitMQ
     */
    public void consume() throws IOException, TimeoutException {
        
        // Consumer
        System.err.println("alka 006.1 - consumer");
//        Consumer consumer = new DefaultConsumer(mychannel) {
//            @Override
//            public void handleDelivery(String consumerTag, Envelope envelope,
//                                AMQP.BasicProperties properties, byte[] body)
//                throws IOException {
//                if (log != null) {
//                    log.rabbitRead();
//                }
//                // Notify observers
//                setChanged();
//                String payload = new String(body);
//                try {
//                    notifyObservers(parser.parse(payload));
//                } catch (Exception e) {
//                    // Publish erronous payloads to error queue, if any
//                    boolean backed = false;
//                    if (errorQueue != null) {
//                        mychannel.basicPublish("", errorQueue, null, body);
//                        backed = true;
//                    }
//                    log.rabbitError(payload, e);
//                }
//            }
//        };
    }

    public void printStatus () {
        System.out.println("Images Processor Bot"+'\n'
                          +"Queue:Count");
        try {
            System.out.println("resizeQueue:"+mychannel
                    .queueDeclarePassive(RESIZE_QUEUE).getMessageCount());
            System.out.println("uploadQueue:"+mychannel
                    .queueDeclarePassive(UPLOAD_QUEUE).getMessageCount());
            System.out.println("doneQueue:"+mychannel
                    .queueDeclarePassive(DONE_QUEUE).getMessageCount());
            System.out.println("failedQueue:"+mychannel
                    .queueDeclarePassive(FAILED_QUEUE).getMessageCount());
        } catch (IOException ex) {
            log.rabbitError("Alka: printStatus()", ex);
        }
    }
    
    /**
     * Periodically checks if the connection is open.
     */
    public void ping() {
        final Timer timer = new Timer(); //alka
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!connection.isOpen()) {
                    timer.cancel(); //alka
                    if (log != null) {
                        log.rabbitPingError(RabbitMQ.this);
                    }
                    // Attempt to reconnect
                    reconnect();
                    ping();
                }
            }
        }, 5000, 5000);
    }

    /**
     * Attempts to reconnect to RabbitMQ.
     */
    private void reconnect() {
        for (int i = 0; i < 4; i++) {
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {

            }
            try {
                // Try to reconnect
                consume();
                log.rabbitReconnectSuccess(this);
                return;
            } catch (Exception e) {
                log.rabbitReconnectError(this, e);
            }
        }
        // Stop trying after 4 failed attempts
        System.exit(1);
    }

    @Override
    public String toString() {
        return String.format("[RabbitMQ:%n"
            + "host=%s%n"
            + "port=%d%n"
            + "virtualHost=%s%n"
            + "queue=%s%n"
            + "backupQueue=%s]", host, port, virtualHost, queue, backupQueue);
    }
}
