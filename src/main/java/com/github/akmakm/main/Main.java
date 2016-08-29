package com.github.akmakm.main;

import static com.github.akmakm.config.Constants.*;
import com.github.akmakm.config.Configuration;
import com.github.akmakm.rabbitmq.RabbitMQ;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.lang.Thread.sleep;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;


/**
 * Runs application.
 * 
 * @author alka
 */
public class Main {

    private static final File configFile = new File("alkabot.json");
    static final Configuration config = new Configuration(configFile);
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

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;
        /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY =
        JacksonFactory.getDefaultInstance();
    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;
    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/drive-java-quickstart
     */
    private static final List<String> SCOPES =
        Arrays.asList(DriveScopes.DRIVE_METADATA_READONLY
                ,DriveScopes.DRIVE_FILE);
    /** Directory to store user credentials for this application. */
    private static java.io.File DATA_STORE_DIR;
    static {
        /** Directory to store user credentials for this application. */
        try {
            // Read configuration file
            config.read();
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_DIR = new java.io.File(config.get(TMP_FOLDER)
                    , ".credentials/drive-java-quickstart");
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
            System.err.println("alka 003a.3");
        } catch (GeneralSecurityException | IOException t) {
            System.err.println("alka 003a.4 - failure");
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(configFile);
        System.err.println("alka 003b.1 - in="
                +configFile.getName());
        GoogleClientSecrets clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        LocalServerReceiver localReceiver = 
                new LocalServerReceiver.Builder().setPort(8089).build();
        Credential credential = new AuthorizationCodeInstalledApp(
//            flow, new LocalServerReceiver()).authorize("user");
            flow, localReceiver).authorize("user");
        System.out.println(
                "alkaAuth: Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Drive client service.
     * @return an authorized Drive client service
     * @throws IOException
     */
    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    
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
        System.err.println("alka 001 TODO:" + TODO_LIST);
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
                    System.err.print("alka 002.1 - DO_SCHEDULE_FOR_RESIZE, dirPath=");
                    actionToDo = DO_SCHEDULE_FOR_RESIZE;
                } 
                if (actionToDo == DO_SCHEDULE_FOR_RESIZE
                    && inputDir != null
                    && inputDir.isDirectory()) {
                    break;
                } else {
                    throw new RuntimeException("Error: wrong directory: "+args[1]);
                }
            case "resize":
                actionToDo = DO_RESIZE_NEXT;
            case "upload":
                actionToDo = (actionToDo == NOTHING_TO_DO ? DO_UPLOAD_NEXT:actionToDo);
            case "retry": 
                actionToDo = (actionToDo == NOTHING_TO_DO ? DO_RETRY_N:actionToDo);
                if (args.length == 1) {
                    numItems = CONSUME_ALL;
                    break;
                } else if (args.length>2 && args[1].equals("-n")) {
                    System.err.println("alka 002= -n="+args[2]);
                    numItems = Integer.parseInt(args[2]);
                    break;
                } else {
                    throw new RuntimeException
                        ("Error: wrong arguments to this command: "+args[0]);
                }
            case "status" : 
                actionToDo = DO_SHOW_STATUS;
                System.err.println("alka 003.1 - DO_SHOW_STATUS");
                break;
            default : throw new RuntimeException("Error: wrong command: "+args[0]);
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            System.err.println(USAGE);
            System.exit(1);
        }
        
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
                try {
                    System.err.println("alka 006.1 - schedule, dirPath: "
                                      + inputDir.getPath());
                    for (final File f : inputDir.listFiles(IMAGE_FILTER)) {
                        System.err.println("alka 006.1a, image: " 
                                + f.getAbsolutePath());
                        rabbitMQ.putToQueue(RESIZE_QUEUE
                                , f.getAbsolutePath());
                    }
                } catch (SecurityException | NullPointerException ex) {
                    log.rabbitError(dirPath, ex);
                }
        System.exit(0);
            case DO_RESIZE_NEXT : 
                System.err.println("alka 006.2 - resize n="+numItems);
                new Resizer(rabbitMQ, numItems).run();
                break;
            case DO_UPLOAD_NEXT : 
                System.err.println("alka 006.3 - upload"+numItems);
                // Build a new authorized GoogleDrive API client service 
                // with getDriveService() to initialize an Uploader.
                new Uploader(rabbitMQ
                        , getDriveService()
                        , numItems
                        , config).run();
                break;
            case DO_SHOW_STATUS : 
                System.err.println("alka 006.4 - status");
                rabbitMQ.printStatus();
        System.exit(0);
        }
        
        System.err.println("alka 007");
        try {
            // Periodically ping RabbitMQ
//        rabbitMQ.ping();
            sleep (5000);
        } catch (InterruptedException ex) {
        }
    }
}