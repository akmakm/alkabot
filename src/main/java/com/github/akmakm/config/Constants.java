package com.github.akmakm.config;

/**
 *
 * @author alka
 */
public interface Constants {
    /** Application name. */
    static final String APPLICATION_NAME = "akmakm";    
    /** 
     * Predefined exchange and queue names.
     */
    static final String DEFAULT_EXCHANGE = "";
    static final String RESIZE_QUEUE = "resize";
    static final String UPLOAD_QUEUE = "upload";
    static final String DONE_QUEUE = "done";
    static final String FAILED_QUEUE = "failed";
    /** 
     * Predefined configurable properties names for json config.
     */
    public static final String TMP_FOLDER = "TMP_FOLDER";
    /** 
     * Unlimited count of items.
     */    
    public static final int CONSUME_ALL = -1;
    /** 
     * Default image size.
     */
    public static final int MAXD = 640;

    /**
     * Usage error message.
     */
    static final String USAGE =
            "Uploader Bot\n" 
            + "Usage:\n" 
            + "  ./bot command [arguments]\n" 
            + "Available commands:\n" 
            + "  schedule Add filenames to resize queue\n" 
            + "  resize Resize next images from the queue\n" 
            + "  status Output current status in format %queue%:%number_of_images%\n" 
            + "  upload Upload next images to remote storage";
    static final String TODO_LIST =
            "\n 3. retry command" 
            + "\n 5. unify loggers" 
            + "\n 6. callback port for credentials"
            + "\n 7. replace consumer with basicGet"
            + "\n 8. Corrupted color in resize kartomin.png";

    static final int NOTHING_TO_DO = 0;
    static final int DO_SHOW_STATUS = 1;
    static final int DO_SCHEDULE_FOR_RESIZE = 2;
    static final int DO_RESIZE_NEXT = 3;
    static final int DO_UPLOAD_NEXT = 4;
    static final int DO_RETRY_N = 5;

    // filter to identify images based on their extensions
    static final String[] EXTENSIONS = new String[]{
        "gif", "png", "bmp", "jpg", "JPG", "jpeg", "tiff"
    };
    
    // error codes
    static final int NO_ERROR = 0;
    static final int ERR_UNEXPECTED_RESIZE_ITEM = 31;
    static final int ERR_UNEXPECTED_UPLOAD_ITEM = 41;
}
