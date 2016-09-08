package com.github.akmakm.main;

import static com.github.akmakm.config.Constants.*;
import com.github.akmakm.config.Configuration;
import com.github.akmakm.rabbitmq.RabbitMQ;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.rabbitmq.client.GetResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;


/**
 * Runs application.
 * 
 * @author alka
 */
public class Main {

    private static final File configFile = new File("alkabot.json");
    static final Configuration config = new Configuration(configFile);

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;
    /** Directory to store user credentials for this application. */
    private static java.io.File DATA_STORE_DIR;

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

        File inputDir = null;
        int numItems = 0;
        
        // Parse options
        int actionToDo = NOTHING_TO_DO;
        
        try {
            if (args.length<1) {
                throw new RuntimeException();
            }
            switch (args[0]) {
            case "schedule" : 
                if (args.length>1) {
                    inputDir = new File(args[1]);
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
                    numItems = Integer.parseInt(args[2]);
                    break;
                } else {
                    throw new RuntimeException
                        ("Error: wrong arguments to this command: "+args[0]);
                }
            case "status" : 
                actionToDo = DO_SHOW_STATUS;
                break;
            default : throw new RuntimeException("Error: wrong command: "+args[0]);
            }
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
            System.err.println(USAGE);
            System.exit(ERR_MALFORMED_PARAMETERS);
        }
        
        // Build RabbitMQ
        RabbitMQ rabbitMQ = new RabbitMQ.Builder()
            .setHost(config.getOrDefault("RABBITMQ_HOST", "localhost"))
            .setPort(Integer.parseInt(
                config.getOrDefault("RABBITMQ_PORT", "5672")))
            .setUsername(config.get("RABBITMQ_USERNAME"))
            .setPassword(config.get("RABBITMQ_PASSWORD"))
            .setVirtualHost(config.getOrDefault("RABBITMQ_VIRTUAL_HOST", "/"))
            .build();

        // Do the necessary actions
        switch (actionToDo) {
            case DO_SCHEDULE_FOR_RESIZE :
                try {
                    for (final File f : inputDir.listFiles(IMAGE_FILTER)) {
                        rabbitMQ.putToQueue(RESIZE_QUEUE
                                , f.getAbsolutePath());
                    }
                } catch (SecurityException | NullPointerException ex) {
                    System.err.println("Failed to get files to schedule");
                    ex.printStackTrace(System.err);
                    System.exit(ERR_FILES_NOT_ACCESSIBLE);
                }
                System.exit(NO_ERROR);
            case DO_RESIZE_NEXT : 
                new Resizer(rabbitMQ
                          , numberOfItemsToConsume(rabbitMQ
                                               , RESIZE_QUEUE
                                               , numItems)).run();
                break;
            case DO_UPLOAD_NEXT : 
                // Build a new authorized GoogleDrive API client service 
                // with getDriveService() to initialize an Uploader.
                new Uploader(rabbitMQ
                        , getDriveService()
                        , numberOfItemsToConsume(rabbitMQ
                                               , UPLOAD_QUEUE
                                               , numItems)
                        , config).run();
                break;
            case DO_RETRY_N : 
                numItems = numberOfItemsToConsume(rabbitMQ
                                                , FAILED_QUEUE
                                                , numItems);
                for (int i=0; i<numItems; i++) {
                    GetResponse nextItem = rabbitMQ.getChannel()
                            .basicGet(FAILED_QUEUE, true);
                    rabbitMQ.putToQueue(RESIZE_QUEUE
                            , new String(nextItem.getBody()));
                }
                System.exit(NO_ERROR);
            case DO_SHOW_STATUS : 
                rabbitMQ.printStatus();
            default:
                System.exit(NO_ERROR);
        }

    }

    /**
     * Calculate the number of items to consume
     * based on a parameter given in command line
     * and remaining items in the queue
     */
    private static int numberOfItemsToConsume (RabbitMQ rabbitMQ
                                      , String queue
                                      , int count) {
        // Count items in the queue to process
        int result;
        try {
            result = rabbitMQ.getChannel()
                     .queueDeclarePassive(queue)
                     .getMessageCount();
        } catch (IOException ex) {
            System.err.println("Failed to get the queue length ("
                              +queue+")");
            ex.printStackTrace(System.err);
            result = 0;
        }
        if (count == CONSUME_ALL) {
            // count is equal to the queue length
        } else if (count > 0) {
            // Up to count, but no more than there are in the queue
            result = Math.min(count, result);
        } else {
            // Some negative value was given, nothing to do
            result = 0;
        }

        return result;
    }

    /**
     * Generates a filename filter for searching images.
     */
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

    static {
        /** Directory to store user credentials for this application. */
        try {
            // Read configuration file
            config.read();
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_DIR = new java.io.File(config.get(TMP_FOLDER)
                    , ".credentials/drive-java-quickstart");
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (GeneralSecurityException | IOException ex) {
            System.err.println("Failed to get temp directory");
            ex.printStackTrace(System.err);
            System.exit(ERR_CONFIGURATION_PROBLEM);
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
        GoogleClientSecrets clientSecrets =
            GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        Credential credential=null;
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        System.err.println("oau, scopes="+flow.getScopesAsString());
        // Read the authorization code from config
        String authorizationCode = config.get(CLOUD_USER_CODE);
        if (authorizationCode != null) {
            System.err.println("oau, code="+authorizationCode);

            // Authorize the OAuth2 token
            GoogleAuthorizationCodeTokenRequest tokenRequest = flow
                    .newTokenRequest(authorizationCode);
            tokenRequest.setRedirectUri(clientSecrets
                    .getInstalled()
                    .getRedirectUris()
                    .get(0));
            System.err.println("oau, uri 0="+clientSecrets
                    .getInstalled()
                    .getRedirectUris()
                    .get(0));
            try {
                GoogleTokenResponse tokenResponse = tokenRequest.execute();
                // Create the OAuth2 credential
                System.err.println("oau, token="+tokenResponse.toString());
                credential = new GoogleCredential
                        .Builder().setTransport(HTTP_TRANSPORT)
                        .setJsonFactory(JSON_FACTORY)
                        .setClientSecrets(clientSecrets)
                        .build();
                // Set authorized credentials
                credential.setFromTokenResponse(tokenResponse);
            } catch (IOException ex) {
                System.err.println("Failed to get authorisation for the code: "
                        + authorizationCode);
                authorizationCode = null;
            }
        } 
        if (authorizationCode == null)  {
            LocalServerReceiver localReceiver = 
                new LocalServerReceiver.Builder().setPort(36438).build();
            credential = new AuthorizationCodeInstalledApp(
                flow, localReceiver).authorize("user");
        }
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
    
}