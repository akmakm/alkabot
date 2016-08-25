package com.github.akmakm.main;

import com.github.akmakm.config.Configuration;
import com.github.akmakm.rabbitmq.RabbitMQ;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.TimeoutException;


/**
 * Runs application.
 * 
 * @author alka
 */
public class Main {
    
    /**
     * Usage error message.
     */
    private static final String USAGE =
            "Uploader Bot\n" 
            + "Usage:\n" 
            + "  ./bot command [arguments]\n" 
            + "Available commands:\n" 
            + "  schedule Add filenames to resize queue\n" 
            + "  resize Resize next images from the queue\n" 
            + "  status Output current status in format %queue%:%number_of_images%\n" 
            + "  upload Upload next images to remote storage";

    private static final int NOTHING_TO_DO = 0;
    private static final int DO_SHOW_STATUS = 1;
    private static final int DO_SCHEDULE_FOR_RESIZE = 2;
    private static final int DO_RESIZE_NEXT = 3;
    private static final int DO_UPLOAD_NEXT = 4;
    private static final int DO_RETRY_N = 5;
//    private enum ActionType {
//        NOTHING_TO_DO,
//        DO_SHOW_STATUS,
//        DO_SCHEDULE_FOR_RESIZE,
//        DO_RESIZE_NEXT,
//        DO_RETRY_N
//    }

    // filter to identify images based on their extensions
    private static final String[] EXTENSIONS = new String[]{
        "gif", "png", "bmp", "jpg", "jpeg", "tiff"
    };
    private static final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
            for (final String ext : EXTENSIONS) {
                if (name.endsWith("." + ext)) {
                    return (true);
                }
            }
            return (false);
        }
    };
    
    /**
     * Runs application.  If an argument is provided, the argument will be used
     * to find the configuration file.  Otherwise, search the current directory
     * for the configuration file
     * 
     * @param args arguments
     * @throws IOException if error reading configuration file
     * @throws ParseException if error parsing configuration file
     * @throws TimeoutException if error consuming from RabbitMQ
     */
    public static void main(String[] args) throws IOException, 
            ParseException,
            TimeoutException {
        
        System.err.println("alka 001");
        File configFile = new File("alkabot.json");
        String dirPath = "./";
        File inputDir = null;
        int numItems = 0;
        // Parse options
        int actionToDo = NOTHING_TO_DO;
        
        try {
            if (args.length<1) {
                throw new RuntimeException();
            }
            System.err.println("alka 001.2, args[0]="+args[0]);
            switch (args[0]) {
            case "schedule" : 
                if (args.length>1) {
                    System.err.println("alka 002= schedule,"+args[1]);
                    inputDir = new File(args[1]);
                    System.err.print("alka 003.2 - DO_SCHEDULE_FOR_RESIZE, dirPath=");
                    actionToDo = DO_SCHEDULE_FOR_RESIZE;
                } 
                if (actionToDo == DO_SCHEDULE_FOR_RESIZE
                    && inputDir != null
                    && inputDir.isDirectory()) {
                    break;
                } else {
                    throw new RuntimeException();
                }
            case "resize":
                actionToDo = DO_RESIZE_NEXT;
            case "upload":
                actionToDo = (actionToDo == NOTHING_TO_DO ? DO_UPLOAD_NEXT:actionToDo);
            case "retry": 
                actionToDo = (actionToDo == NOTHING_TO_DO ? DO_RETRY_N:actionToDo);
                if (args.length == 1) {
                    numItems = Resizer.ENTIRE_QUEUE;
                    break;
                } else if (args.length>2 && args[1].equals("-n")) {
                    System.err.println("alka 002= -n="+args[2]);
                    numItems = Integer.parseInt(args[2]);
                    break;
                } else {
                    throw new RuntimeException();
                }
            case "status" : 
                actionToDo = DO_SHOW_STATUS;
                System.err.println("alka 003.1 - DO_SHOW_STATUS");
                break;
            default : throw new RuntimeException();
            }
        } catch (RuntimeException ex) {
            System.err.println(USAGE);
            System.exit(1);
        }
        
        // Read configuration file
        System.err.println("alka 003a.1");
        System.err.println("Alka-conf-file="+configFile.toString());
        Configuration config = new Configuration(configFile);
        System.err.println("alka 003a.2");
        config.read();
        System.err.println("alka 003a.3");

        // Build log
        Log log = new Log.Builder()
            .setNormalLog(config.get("NORMAL_LOG"))
            .setNormalLogNum(Integer.parseInt(
                config.getOrDefault("NORMAL_LOG_NUM", "10")))
            .setNormalLogSize(Integer.parseInt(
                config.getOrDefault("NORMAL_LOG_SIZE", "0")))
            .setNormalLogInterval(Long.parseLong(
                config.getOrDefault("NORMAL_LOG_INTERVAL", "0")))
            .setErrorLog(config.get("ERROR_LOG"))
            .setErrorLogNum(Integer.parseInt(
                config.getOrDefault("ERROR_LOG_NUM", "0")))
            .setErrorLogSize(Integer.parseInt(
                config.getOrDefault("ERROR_LOG_SIZE", "0")))
            .build();
        System.err.println("alka 004");

        // Build RabbitMQ
        RabbitMQ rabbitMQ = new RabbitMQ.Builder()
            .setHost(config.getOrDefault("RABBITMQ_HOST", "localhost"))
            .setPort(Integer.parseInt(
                config.getOrDefault("RABBITMQ_PORT", "5672")))
            .setUsername(config.get("RABBITMQ_USERNAME"))
            .setPassword(config.get("RABBITMQ_PASSWORD"))
            .setVirtualHost(config.getOrDefault("RABBITMQ_VIRTUAL_HOST", "/"))
            .setLog(log)
            .build();

        // Do the necessary actions
        switch (actionToDo) {
            case DO_SCHEDULE_FOR_RESIZE :
                System.err.println("alka 006.1 - schedule, dirPath: "
                                  + inputDir.getPath());
                try {
                    for (final File f : inputDir.listFiles(IMAGE_FILTER)) {
                        System.err.println("alka 006.1a, image: " + f.toURI());
                        rabbitMQ.putToQueue(RabbitMQ.RESIZE_QUEUE
                                , f.toURI().toString());
                    }
                } catch (SecurityException ex) {
                    log.rabbitError(dirPath, ex);
                }
                break;
            case DO_RESIZE_NEXT : 
                System.err.println("alka 006.2 - resize");
                new Resizer(rabbitMQ, numItems).run();
                break;
            case DO_UPLOAD_NEXT : 
                System.err.println("alka 006.3 - upload");
                new Uploader(rabbitMQ
                        , numItems
                        , config.getOrDefault("DBX_CODE","alkabot")).run();
                break;
            case DO_SHOW_STATUS : 
                System.err.println("alka 006.4 - status");
                rabbitMQ.printStatus();
                break;
        }
        
        System.err.println("alka 007");
        // Periodically ping RabbitMQ
//alka        rabbitMQ.ping(); - incomplete threads?
        System.exit(0);
    }
}